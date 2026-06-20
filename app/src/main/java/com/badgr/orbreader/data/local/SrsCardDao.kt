package com.badgr.orbreader.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class BookDueCount(val bookId: String, val count: Int)

@Dao
interface SrsCardDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(card: SrsCardEntity)

    @Update
    suspend fun updateCard(card: SrsCardEntity)

    @Query("SELECT * FROM srs_cards WHERE bookId = :bookId AND nextReviewAt <= :nowMs ORDER BY nextReviewAt ASC")
    suspend fun getDueCards(bookId: String, nowMs: Long): List<SrsCardEntity>

    @Query("SELECT bookId, COUNT(*) as count FROM srs_cards WHERE nextReviewAt <= :nowMs GROUP BY bookId")
    fun getDueCountsFlow(nowMs: Long): Flow<List<BookDueCount>>

    @Query("DELETE FROM srs_cards WHERE bookId = :bookId")
    suspend fun deleteCardsByBook(bookId: String)
}
