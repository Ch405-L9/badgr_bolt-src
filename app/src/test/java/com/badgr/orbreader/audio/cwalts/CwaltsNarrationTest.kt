package com.badgr.orbreader.audio.cwalts

import com.badgr.orbreader.data.model.Book
import com.badgr.orbreader.data.model.FileType
import com.badgr.orbreader.util.BookCategorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CwaltsNarrationTest {
    @Test
    fun chunksReassembleExactlyAndStayBelowServerLimit() {
        val source = (1..900).joinToString(" ") { "paragraph-$it." }
        val chunks = CwaltsNarrationChunker.split(source)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= CwaltsNarrationChunker.HARD_LIMIT })
        assertEquals(source, chunks.joinToString(""))
    }

    @Test
    fun objectiveCategoriesMapOnlyToSupportedMetadata() {
        val technical = Book("t", "t", FileType.TXT, 1, category = BookCategorizer.TECHNOLOGY)
        val fiction = Book("f", "f", FileType.TXT, 1, category = BookCategorizer.FICTION)
        val unknown = Book("u", "u", FileType.TXT, 1, category = BookCategorizer.OTHER)
        assertEquals("technical", CwaltsMetadata.fromBook(technical)["domain"])
        assertEquals("narrative", CwaltsMetadata.fromBook(fiction)["content_mode"])
        assertTrue(CwaltsMetadata.fromBook(unknown).isEmpty())
    }
}
