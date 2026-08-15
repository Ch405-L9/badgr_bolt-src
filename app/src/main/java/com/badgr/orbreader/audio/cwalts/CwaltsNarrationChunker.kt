package com.badgr.orbreader.audio.cwalts

/** Splits canonical reader text without changing any characters. */
object CwaltsNarrationChunker {
    const val STARTUP_TARGET = 220
    const val STEADY_TARGET = 500
    const val HARD_LIMIT = 700
    private const val STARTUP_MIN = 150
    private const val STEADY_MIN = 300

    fun split(source: String): List<String> {
        require(source.length <= Int.MAX_VALUE)
        if (source.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        var first = true
        while (start < source.length) {
            val remaining = source.length - start
            val target = if (first) STARTUP_TARGET else STEADY_TARGET
            val minimum = if (first) STARTUP_MIN else STEADY_MIN
            if (remaining <= target) {
                result += source.substring(start)
                break
            }
            val targetEnd = (start + target).coerceAtMost(source.length)
            val hardEnd = (start + HARD_LIMIT).coerceAtMost(source.length)
            val boundary = findBoundary(source, start, targetEnd, minimum)
                ?: findBoundary(source, targetEnd, hardEnd, 1)
                ?: source.lastIndexOf(' ', hardEnd - 1).takeIf { it > start }?.plus(1)
                ?: hardEnd
            require(boundary - start <= HARD_LIMIT)
            result += source.substring(start, boundary)
            start = boundary
            first = false
        }
        return result
    }

    private fun findBoundary(source: String, start: Int, end: Int, minimum: Int): Int? {
        val lower = start + minimum
        if (end <= lower) return null
        val paragraph = source.lastIndexOf("\n\n", end - 1)
        if (paragraph >= lower) return paragraph + 2
        for (index in (end - 1) downTo lower) {
            val c = source[index]
            if (c == '.' || c == '!' || c == '?' || c == ';' || c == ':') {
                if (index + 1 == source.length || source[index + 1].isWhitespace()) return index + 1
            }
        }
        for (index in (end - 1) downTo lower) {
            if (source[index].isWhitespace()) return index + 1
        }
        return null
    }
}
