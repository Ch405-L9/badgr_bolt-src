package com.badgr.orbreader.audio.cwalts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.badgr.orbreader.BuildConfig
import com.badgr.orbreader.data.model.Book
import com.badgr.orbreader.util.BookCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.security.MessageDigest

enum class CwaltsNarrationState { Unavailable, Idle, Preparing, Queued, Processing, Ready, Playing, Paused, Failed }

data class CwaltsNarrationStatus(
    val state: CwaltsNarrationState = CwaltsNarrationState.Idle,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val message: String? = null
)

object CwaltsMetadata {
    fun fromBook(book: Book): Map<String, String> = when (book.category) {
        BookCategorizer.SCIENCE -> mapOf("domain" to "educational", "content_mode" to "informational")
        BookCategorizer.TECHNOLOGY -> mapOf("domain" to "technical", "content_mode" to "instructional")
        BookCategorizer.FICTION -> mapOf("content_mode" to "narrative")
        else -> emptyMap()
    }
}

class CwaltsNarrationController(private val context: Context) {
    private val api: CwaltsNarrationApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.CWALTS_BASE_URL.trimEnd('/') + "/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CwaltsNarrationApi::class.java)
    }
    private val cacheRoot = File(context.filesDir, "cwalts")
    private var player: MediaPlayer? = null

    suspend fun verifyHealth() = withContext(Dispatchers.IO) {
        val response = api.health()
        check(response.isSuccessful && response.body()?.provider == "f5_local") {
            "C.Walts health unavailable"
        }
        Log.i("CwaltsNarration", "health provider=f5_local voice=${response.body()?.voice}")
    }

    suspend fun prepareSegment(book: Book, canonicalText: String, segmentIndex: Int): File = withContext(Dispatchers.IO) {
        val chunks = CwaltsNarrationChunker.split(canonicalText)
        require(segmentIndex in chunks.indices)
        val text = chunks[segmentIndex]
        val directory = File(cacheRoot, book.id).apply { mkdirs() }
        val hash = sha256("${book.id}|$segmentIndex|$text|B.Lawson|F5TTS_v1_Base")
        val target = File(directory, "%06d_%s.wav".format(segmentIndex, hash))
        if (!target.isFile || target.length() == 0L) {
            Log.i("CwaltsNarration", "segment=$segmentIndex cache=miss chars=${text.length}")
            val accepted = api.narrate(CwaltsNarrateRequest(text, CwaltsMetadata.fromBook(book)))
            check(accepted.isSuccessful && accepted.body() != null) { "C.Walts request failed" }
            val id = accepted.body()!!.jobId
            Log.i("CwaltsNarration", "segment=$segmentIndex post=accepted job=$id")
            var status = "queued"
            while (status == "queued" || status == "running") {
                delay(2000)
                val response = api.job(id)
                check(response.isSuccessful && response.body() != null) { "C.Walts job status failed" }
                Log.i("CwaltsNarration", "segment=$segmentIndex job=$id status=${response.body()!!.status}")
                status = response.body()!!.status
            }
            check(status == "completed") { "C.Walts job failed" }
            val audio = api.audio(id)
            check(audio.isSuccessful && audio.body() != null) { "C.Walts audio download failed" }
            Log.i("CwaltsNarration", "segment=$segmentIndex audio=downloaded")
            val temp = File(directory, ".$hash.tmp")
            audio.body()!!.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
            check(temp.length() > 44L) { "C.Walts returned empty audio" }
            check(temp.renameTo(target)) { "C.Walts audio cache commit failed" }
        } else {
            Log.i("CwaltsNarration", "segment=$segmentIndex cache=hit")
        }
        target
    }

    fun play(file: File, onComplete: () -> Unit = {}) {
        Log.i("CwaltsNarration", "playback=start file=${file.name}")
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                Log.i("CwaltsNarration", "playback=complete file=${file.name}")
                onComplete()
            }
            prepare()
            playbackParams = playbackParams.setSpeed(1.0f)
            start()
        }
    }

    fun pause() { player?.pause() }
    fun resume() { player?.start() }
    fun stop() { player?.stop(); player?.release(); player = null }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
