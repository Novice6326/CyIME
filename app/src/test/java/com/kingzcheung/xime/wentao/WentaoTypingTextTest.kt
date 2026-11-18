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
            listOf("é", "\r\n", "x"),
            WentaoTypingText.splitCharacters("é\r\nx")
        )
    }

    @Test
    fun keepsHangulJamoAndKeycapEmojiIntact() {
        assertEquals(
            listOf("한", "❤️", "1️⃣", "끝"),
            WentaoTypingText.splitCharacters("한❤️1️⃣끝")
        )
    }

    @Test
    fun keepsIndicConjunctSequencesTogether() {
        assertEquals(
            listOf("क्ष", "क़", "த", "மி", "ழ்"),
            WentaoTypingText.splitCharacters("क्षक़தமிழ்")
        )
    }

    @Test
    fun keepsPrependCharactersWithTheirFollowingText() {
        assertEquals(
            listOf("\u0600A", "B"),
            WentaoTypingText.splitCharacters("\u0600AB")
        )
    }

    @Test
    fun keepsZeroWidthNonJoinerWithThePrecedingCluster() {
        assertEquals(
            listOf("क\u200C", "ष"),
            WentaoTypingText.splitCharacters("क\u200Cष")
        )
    }

    @Test
    fun handlesEmptyText() {
        assertEquals(emptyList<String>(), WentaoTypingText.splitCharacters(""))
    }
}
