package com.badgr.orbreader.audio.cwalts

/** Splits canonical reader text without changing any characters. */
object CwaltsNarrationChunker {
    const val SOFT_LIMIT = 4000
    const val HARD_LIMIT = 5000

    fun split(source: String): List<String> {
        require(source.length <= Int.MAX_VALUE)
        if (source.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        while (start < source.length) {
            val remaining = source.length - start
            if (remaining <= SOFT_LIMIT) {
                result += source.substring(start)
                break
            }
            val softEnd = start + SOFT_LIMIT
            val boundary = sequenceOf(
                source.lastIndexOf("\n\n", softEnd - 1).let { if (it >= start) it + 2 else -1 },
                source.lastIndexOf('\n', softEnd - 1).let { if (it >= start) it + 1 else -1 },
                source.lastIndexOf('.', softEnd - 1).let { if (it >= start) it + 1 else -1 },
                source.lastIndexOf(' ', softEnd - 1).let { if (it >= start) it + 1 else -1 }
            ).firstOrNull { it > start && it <= softEnd } ?: softEnd
            require(boundary - start <= HARD_LIMIT)
            result += source.substring(start, boundary)
            start = boundary
        }
        return result
    }
}
