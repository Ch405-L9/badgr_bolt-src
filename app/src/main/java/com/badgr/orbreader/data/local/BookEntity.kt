package com.badgr.orbreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.badgr.orbreader.data.model.Book
import com.badgr.orbreader.data.model.FileType

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileType: String,
    val wordCount: Int,
    val createdAt: Long,
    val currentWordIndex: Int = 0,
    val coverPath: String? = null,
    val summary: String? = null
) {
    fun toDomain() = Book(
        id               = id,
        title            = title,
        fileType         = FileType.valueOf(fileType),
        wordCount        = wordCount,
        createdAt        = createdAt,
        coverPath        = coverPath
    )

    companion object {
        fun fromDomain(book: Book) = BookEntity(
            id               = book.id,
            title            = book.title,
            fileType         = book.fileType.name,
            wordCount        = book.wordCount,
            createdAt        = book.createdAt,
            coverPath        = book.coverPath
        )
    }
}
