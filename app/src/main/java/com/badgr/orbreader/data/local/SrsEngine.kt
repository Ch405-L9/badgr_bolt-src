package com.badgr.orbreader.data.local

object SrsEngine {

    private const val DAY_MS = 86_400_000L

    fun schedule(card: SrsCardEntity, correct: Boolean): SrsCardEntity {
        return if (correct) {
            val newInterval = when (card.repetitions) {
                0    -> 1
                1    -> 6
                else -> (card.intervalDays * card.easeFactor).toInt().coerceAtLeast(7)
            }
            card.copy(
                intervalDays = newInterval,
                repetitions  = card.repetitions + 1,
                easeFactor   = (card.easeFactor + 0.05f).coerceAtMost(3.0f),
                nextReviewAt = System.currentTimeMillis() + newInterval * DAY_MS
            )
        } else {
            card.copy(
                intervalDays = 1,
                repetitions  = 0,
                easeFactor   = (card.easeFactor - 0.15f).coerceAtLeast(1.3f),
                nextReviewAt = System.currentTimeMillis() + DAY_MS
            )
        }
    }
}
