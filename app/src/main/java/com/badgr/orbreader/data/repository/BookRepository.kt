package com.badgr.orbreader.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.badgr.orbreader.billing.ProGate
import com.badgr.orbreader.data.local.BookDao
import com.badgr.orbreader.data.local.BookEntity
import com.badgr.orbreader.data.model.Book
import com.badgr.orbreader.data.model.FileType
import com.badgr.orbreader.data.remote.ApiClient
import com.badgr.orbreader.util.CoverExtractor
import com.badgr.orbreader.util.EpubMetadata
import com.badgr.orbreader.util.WordTokenizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

sealed class ImportResult {
    data class Success(val book: Book) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao
) {
    private val gson = Gson()

    private companion object {
        const val MAX_IMPORT_BYTES_FREE = 20L  * 1024 * 1024 // 20 MB — free tier
        const val MAX_IMPORT_BYTES_PRO  = 100L * 1024 * 1024 // 100 MB — Pro, matches backend cap
    }

    val books: Flow<List<Book>> = bookDao.getAllBooks().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun importTxt(uri: Uri, fileName: String): ImportResult =
        withContext(Dispatchers.IO) {
            try {
                val text = readTextFromUri(context.contentResolver, uri)
                val words = WordTokenizer.tokenize(text)
                val book = Book(
                    title     = fileName,
                    fileType  = FileType.TXT,
                    wordCount = words.size,
                    coverPath = null
                )
                saveBook(book, words)
                ImportResult.Success(book)
            } catch (e: Exception) {
                ImportResult.Error("Failed to read TXT: ${e.localizedMessage}")
            }
        }

    suspend fun importRemote(
        uri: Uri,
        fileName: String,
        fileType: FileType,
        mimeType: String
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            // Query file size before reading anything — enforces the cap and lets OkHttp
            // send a Content-Length header so the server doesn't have to buffer the body.
            val fileSize = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L

            val maxBytes = if (ProGate.largeFileImport) MAX_IMPORT_BYTES_PRO else MAX_IMPORT_BYTES_FREE
            val limitMb  = maxBytes / (1024L * 1024L)
            if (fileSize > maxBytes) {
                return@withContext ImportResult.Error(
                    "File too large. Maximum is ${limitMb} MB." +
                        if (!ProGate.largeFileImport) " Upgrade to Pro for up to 100 MB." else ""
                )
            }

            // Stream the upload — no readBytes(), no in-memory copy of the whole file.
            val mediaType = mimeType.toMediaTypeOrNull()
            val requestBody = object : RequestBody() {
                override fun contentType() = mediaType
                override fun contentLength() = fileSize
                override fun writeTo(sink: BufferedSink) {
                    context.contentResolver.openInputStream(uri)?.source()?.use { source ->
                        sink.writeAll(source)
                    }
                }
            }
            val part = MultipartBody.Part.createFormData("file", fileName, requestBody)

            val response = ApiClient.convertApi.convertFile(part)

            if (!response.isSuccessful) {
                return@withContext ImportResult.Error(
                    "Server error ${response.code()}: " +
                        (response.errorBody()?.string()?.take(500) ?: "unknown error")
                )
            }

            val body = response.body()
            if (body?.text == null) {
                return@withContext ImportResult.Error(
                    body?.error ?: "Invalid response from server"
                )
            }

            val words = WordTokenizer.tokenize(body.text)
            val tempId = java.util.UUID.randomUUID().toString()

            // Cover + metadata — each branch reads only what it needs, after the upload
            // stream is already closed. Memory peak is now one operation at a time.
            val coverPath: String?
            val displayTitle: String

            when (fileType) {
                FileType.EPUB -> {
                    val epubBytes = context.contentResolver.openInputStream(uri)?.readBytes()
                        ?: byteArrayOf()
                    coverPath    = CoverExtractor.fromEpub(context, tempId, epubBytes)
                    val meta     = EpubMetadata.extract(epubBytes)
                    displayTitle = meta.title?.takeIf { it.isNotBlank() } ?: fileName
                }
                FileType.PDF -> {
                    // Write directly to a temp file — never buffers all bytes in RAM.
                    val tmp = File(context.cacheDir, "$tempId.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    coverPath    = CoverExtractor.fromPdf(context, tempId, tmp).also { tmp.delete() }
                    displayTitle = fileName
                }
                else -> {
                    coverPath    = null
                    displayTitle = fileName
                }
            }

            val book = Book(
                title     = displayTitle,
                fileType  = fileType,
                wordCount = words.size,
                coverPath = coverPath
            )
            saveBook(book, words)
            ImportResult.Success(book)

        } catch (e: Exception) {
            ImportResult.Error("Network error: ${e.localizedMessage}")
        }
    }

    suspend fun loadWords(bookId: String): List<String> = withContext(Dispatchers.IO) {
        val file = wordFile(bookId)
        if (!file.exists()) return@withContext emptyList()
        val json = file.readText()
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.deleteBookById(book.id)
        wordFile(book.id).delete()
        book.coverPath?.let { File(it).delete() }
    }

    private suspend fun saveBook(book: Book, words: List<String>) {
        val target = wordFile(book.id)
        val tmp = File(target.parent, "${target.name}.tmp")
        try {
            tmp.writeText(gson.toJson(words))
            // rename() is atomic on the same filesystem (internal storage) — target is either
            // the old file or the new file, never a partial write.
            if (!tmp.renameTo(target)) {
                // Cross-filesystem fallback (should not happen on internal storage).
                target.writeText(gson.toJson(words))
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        bookDao.insertBook(BookEntity.fromDomain(book))
    }

    suspend fun fetchAndCacheSummary(bookId: String): Result<String> = withContext(Dispatchers.IO) {
        // Return cached summary if already fetched.
        bookDao.getSummary(bookId)?.let { return@withContext Result.success(it) }

        val words = loadWords(bookId)
        if (words.isEmpty()) return@withContext Result.failure(Exception("No text found for this book."))

        // Cap at 4000 words — backend enforces the same limit.
        val text = words.take(4000).joinToString(" ")
        try {
            val response = ApiClient.summarizeApi.summarize(
                com.badgr.orbreader.data.remote.SummarizeRequest(text = text)
            )
            if (response.isSuccessful) {
                val summary = response.body()?.summary
                    ?: return@withContext Result.failure(Exception("Empty summary from server."))
                bookDao.updateSummary(bookId, summary)
                Result.success(summary)
            } else {
                Result.failure(Exception("Server error ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun wordFile(bookId: String): File =
        File(context.filesDir, "words_$bookId.json")

    private fun readTextFromUri(resolver: ContentResolver, uri: Uri): String =
        resolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw IllegalStateException("Could not open URI: $uri")
}
