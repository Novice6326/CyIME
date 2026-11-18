package com.kingzcheung.xime.wentao

import org.junit.Assert.assertEquals
import org.junit.Test

class WentaoTypingTextTest {
    @Test
    fun splitsChineseAndLatinCharacters() {
        assertEquals(listOf("你", "好", "A", "B"), WentaoTypingText.splitCharacters("你好AB"))
    }

    @Test
    fun keepsEmojiSequencesIntact() {
        assertEquals(
            listOf("你", "👨‍👩‍👧‍👦", "👍🏽", "🇨🇳", "好"),
            WentaoTypingText.splitCharacters("你👨‍👩‍👧‍👦👍🏽🇨🇳好")
        )
    }

    @Test
    fun keepsCombiningMarksAndCrLfIntact() {
        assertEquals(
            listOf("é", "\\r\\n", "x"),
            WentaoTypingText.splitCharacters("é\\r\\nx")
        )
    }

    @Test
    fun handlesEmptyText() {
        assertEquals(emptyList<String>(), WentaoTypingText.splitCharacters(""))
    }
}
