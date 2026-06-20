package com.badgr.orbreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "srs_cards")
data class SrsCardEntity(
    @PrimaryKey val id: String,          // deterministic: "${bookId}_${question.hashCode()}"
    val bookId:       String,
    val question:     String,
    val optionsJson:  String,            // JSON array of 4 option strings
    val answerIndex:  Int,
    val nextReviewAt: Long,              // epoch ms
    val intervalDays: Int   = 1,
    val repetitions:  Int   = 0,
    val easeFactor:   Float = 2.5f,
    val createdAt:    Long  = System.currentTimeMillis()
)
