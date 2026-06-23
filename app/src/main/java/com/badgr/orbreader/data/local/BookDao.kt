package com.badgr.orbreader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    suspend fun getAllBooks_suspend(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)

    @Query("UPDATE books SET currentWordIndex = :index WHERE id = :bookId")
    suspend fun updateProgress(bookId: String, index: Int)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun bookCount(): Int

    @Query("UPDATE books SET summary = :summary WHERE id = :bookId")
    suspend fun updateSummary(bookId: String, summary: String)

    @Query("SELECT summary FROM books WHERE id = :bookId")
    suspend fun getSummary(bookId: String): String?

    @Query("UPDATE books SET category = :category WHERE id = :bookId")
    suspend fun updateCategory(bookId: String, category: String)
}
