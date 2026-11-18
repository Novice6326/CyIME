package com.kingzcheung.xime.wentao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the official Unicode 18.0.0 GraphemeBreakTest.txt corpus.
 * Source data is preserved under src/test/resources/unicode/18.0.0/.
 */
class Unicode18GraphemeConformanceTest {
    @Test
    fun matchesOfficialUnicode18GraphemeBreakTest() {
        val stream = javaClass.getResourceAsStream(
            "/unicode/18.0.0/GraphemeBreakTest.txt"
        ) ?: error("Missing official Unicode 18.0.0 GraphemeBreakTest.txt")

        val mismatches = mutableListOf<String>()
        var cases = 0

        stream.bufferedReader().useLines { lines ->
            lines.forEachIndexed { lineNumber, raw ->
                val data = raw.substringBefore('#').trim()
                if (data.isEmpty()) return@forEachIndexed
                val tokens = data.split(Regex("\\s+"))
                val expected = mutableListOf<String>()
                val cluster = StringBuilder()

                for (token in tokens) {
                    when (token) {
                        "÷" -> {
                            if (cluster.isNotEmpty()) {
                                expected += cluster.toString()
                                cluster.setLength(0)
                            }
                        }
                        "×" -> Unit
                        else -> cluster.appendCodePoint(token.toInt(16))
                    }
                }
                if (cluster.isNotEmpty()) expected += cluster.toString()

                val input = expected.joinToString(separator = "")
                val actual = WentaoTypingText.splitCharacters(input)
                cases++
                if (actual != expected && mismatches.size < 30) {
                    mismatches += "line $lineNumber: expected=$expected actual=$actual"
                }
            }
        }

        assertEquals("Unicode 18.0.0 test corpus case count changed", 853, cases)
        assertTrue(
            "Unicode 18.0.0 grapheme mismatches (${mismatches.size}):\n" +
                mismatches.joinToString("\n"),
            mismatches.isEmpty()
        )
    }
}
