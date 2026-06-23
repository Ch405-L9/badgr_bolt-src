package com.badgr.orbreader.util

object BookCategorizer {

    const val FICTION          = "Fiction"
    const val BIOGRAPHY        = "Biography & Memoir"
    const val SELF_IMPROVEMENT = "Self-Improvement"
    const val HISTORY          = "History"
    const val SCIENCE          = "Science"
    const val TECHNOLOGY       = "Technology"
    const val NON_FICTION      = "Non-Fiction"
    const val OTHER            = "Other"

    val ALL_CATEGORIES = listOf(
        FICTION, BIOGRAPHY, SELF_IMPROVEMENT, HISTORY,
        SCIENCE, TECHNOLOGY, NON_FICTION, OTHER
    )

    private val rules = listOf(
        BIOGRAPHY        to listOf(
            "biography", "memoir", "autobiography", "life of", "diary",
            "journals", "confessions", "recollections", "letters of"
        ),
        HISTORY          to listOf(
            "history", "historical", "revolution", "war", "empire", "dynasty",
            "ancient", "civilization", "chronicle", "colonial", "medieval",
            "republic", "kingdom", "century", "conquest", "crusade", "civil war"
        ),
        SCIENCE          to listOf(
            "science", "biology", "physics", "chemistry", "astronomy", "quantum",
            "evolution", "cosmos", "genome", "species", "brain", "neuroscience",
            "ecology", "molecules", "relativity", "genetics"
        ),
        TECHNOLOGY       to listOf(
            "programming", "software", "code", "coding", "computer", "technology",
            "machine learning", "algorithm", "data science", "digital", "cybersecurity",
            "hacking", "python", "java", "javascript", "startup", "silicon valley",
            "artificial intelligence", "app development", "blockchain"
        ),
        SELF_IMPROVEMENT to listOf(
            "habit", "habits", "mindset", "success", "productivity", "leadership",
            "motivation", "discipline", "mindfulness", "principles", "wealth",
            "confidence", "power of", "influence", "emotional intelligence",
            "decision making", "purpose", "resilience", "courage", "stillness",
            "gratitude", "atomic", "limitless", "self-help", "think and grow",
            "rich dad", "7 habits", "how to win"
        ),
        FICTION          to listOf(
            "novel", "fiction", "fantasy", "mystery", "thriller", "horror",
            "romance", "adventure", "saga", "epic", "detective", "dystopia",
            "fable", "tales", "chronicles of", "lord of", "game of"
        ),
    )

    /**
     * Returns a category for the given book title + optional first words.
     * Falls back to NON_FICTION when no keyword matches.
     */
    fun categorize(title: String, firstWords: List<String> = emptyList()): String {
        val haystack = buildString {
            append(title.lowercase())
            if (firstWords.isNotEmpty()) {
                append(' ')
                append(firstWords.take(200).joinToString(" ").lowercase())
            }
        }
        for ((category, keywords) in rules) {
            if (keywords.any { haystack.contains(it) }) return category
        }
        return NON_FICTION
    }
}
