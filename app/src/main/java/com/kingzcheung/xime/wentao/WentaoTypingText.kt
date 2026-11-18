package com.kingzcheung.xime.wentao

/**
 * 将剪贴板文本拆成用户感知字符（扩展字素簇），而不是 UTF-16 code unit。
 * 中文、代理对 emoji、组合音标、肤色修饰符、ZWJ emoji、国旗、CRLF、
 * Hangul Jamo、常见 Indic conjunct 和 Prepend 序列尽量保持为完整输入单位。
 * 这是轻量实现，不宣称完整符合 Unicode UAX #29。
 */
object WentaoTypingText {
    fun splitCharacters(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var cluster = StringBuilder()
        var offset = 0
        var previousCodePoint = -1
        var regionalIndicatorCount = 0

        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val attach = cluster.isNotEmpty() && (
                isExtend(codePoint) ||
                    codePoint == ZERO_WIDTH_JOINER ||
                    isEmojiZwjContinuation(cluster, codePoint) ||
                    isPrepend(previousCodePoint) ||
                    isIndicConjunctContinuation(cluster, codePoint) ||
                    isHangulContinuation(previousCodePoint, codePoint) ||
                    (previousCodePoint == CARRIAGE_RETURN && codePoint == LINE_FEED) ||
                    (isRegionalIndicator(codePoint) &&
                        isRegionalIndicator(previousCodePoint) &&
                        regionalIndicatorCount % 2 == 1)
                )

            if (!attach && cluster.isNotEmpty()) {
                result += cluster.toString()
                cluster = StringBuilder()
                regionalIndicatorCount = 0
            }

            cluster.appendCodePoint(codePoint)
            if (isRegionalIndicator(codePoint)) regionalIndicatorCount++
            previousCodePoint = codePoint
            offset += Character.charCount(codePoint)
        }

        if (cluster.isNotEmpty()) result += cluster.toString()
        return result
    }

    private fun isExtend(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint in 0xFE00..0xFE0F ||
            codePoint in 0xE0100..0xE01EF ||
            codePoint in 0x1F3FB..0x1F3FF ||
            codePoint in 0xE0020..0xE007F ||
            codePoint == ZERO_WIDTH_NON_JOINER
    }



    /**
     * GB11: only an Extended_Pictographic character may continue an emoji ZWJ sequence.
     * ZWJ itself is already attached by GB9; this check prevents arbitrary text after ZWJ
     * from being swallowed into the same typing unit.
     */
    private fun isEmojiZwjContinuation(cluster: StringBuilder, current: Int): Boolean {
        if (!isExtendedPictographic(current) || cluster.isEmpty()) return false
        var offset = cluster.length
        val joiner = Character.codePointBefore(cluster, offset)
        if (joiner != ZERO_WIDTH_JOINER) return false
        offset -= Character.charCount(joiner)
        while (offset > 0) {
            val codePoint = Character.codePointBefore(cluster, offset)
            offset -= Character.charCount(codePoint)
            if (isExtend(codePoint)) continue
            return isExtendedPictographic(codePoint)
        }
        return false
    }

    /**
     * Compact Extended_Pictographic ranges used for common emoji blocks and legacy symbols.
     * The official Unicode emoji-data.txt remains the source of truth for full conformance.
     */
    private fun isExtendedPictographic(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x1FC00..0x1FFFD ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF ||
            codePoint in 0x2B00..0x2BFF ||
            codePoint in 0x00A9..0x00AE ||
            codePoint == 0x203C || codePoint == 0x2049 ||
            codePoint == 0x2122 || codePoint == 0x2139 ||
            codePoint in 0x2194..0x2199 ||
            codePoint in 0x21A9..0x21AA ||
            codePoint == 0x2328 || codePoint == 0x2388 ||
            codePoint == 0x23CF || codePoint == 0x24C2 ||
            codePoint == 0x25AA || codePoint == 0x25AB ||
            codePoint == 0x25B6 || codePoint == 0x25C0 ||
            codePoint in 0x25FB..0x25FE ||
            codePoint == 0x2934 || codePoint == 0x2935 ||
            codePoint == 0x2B05 || codePoint == 0x2B06 ||
            codePoint == 0x2B07 || codePoint == 0x2B1B ||
            codePoint == 0x2B1C || codePoint == 0x2B50 ||
            codePoint == 0x2B55 || codePoint == 0x3030 ||
            codePoint == 0x303D || codePoint == 0x3297 ||
            codePoint == 0x3299

    /** GB9b: a Prepend character remains attached to the following character. */
    private fun isPrepend(codePoint: Int): Boolean =
        codePoint in 0x0600..0x0605 ||
            codePoint == 0x06DD ||
            codePoint == 0x070F ||
            codePoint in 0x0890..0x0891 ||
            codePoint == 0x08E2 ||
            codePoint == 0x0D4E ||
            codePoint == 0x110BD ||
            codePoint == 0x110CD ||
            codePoint in 0x111C2..0x111C3 ||
            codePoint == 0x113D1 ||
            codePoint == 0x1193F ||
            codePoint == 0x11941 ||
            codePoint == 0x11A3A ||
            codePoint in 0x11A84..0x11A89 ||
            codePoint == 0x11D46 ||
            codePoint == 0x11F02

    /**
     * Approximation of GB9c for common Indic conjuncts. The linker set follows the
     * Unicode Indic_Conjunct_Break Linker property; consonant detection is deliberately
     * limited to letter code points in Indic-script blocks.
     */
    private fun isIndicConjunctContinuation(cluster: StringBuilder, current: Int): Boolean {
        if (!isIndicConsonant(current)) return false
        var offset = cluster.length
        var foundLinker = false
        while (offset > 0) {
            val codePoint = Character.codePointBefore(cluster, offset)
            offset -= Character.charCount(codePoint)
            when {
                isIndicLinker(codePoint) -> foundLinker = true
                isExtend(codePoint) -> Unit
                isIndicConsonant(codePoint) -> return foundLinker
                else -> return false
            }
        }
        return false
    }

    private fun isIndicConsonant(codePoint: Int): Boolean {
        // Exact InCB=Consonant ranges from Unicode 18.0 DerivedCoreProperties.txt.
        var low = 0
        var high = INDIC_CONSONANT_RANGES.size / 2 - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val start = INDIC_CONSONANT_RANGES[middle * 2]
            val end = INDIC_CONSONANT_RANGES[middle * 2 + 1]
            when {
                codePoint < start -> high = middle - 1
                codePoint > end -> low = middle + 1
                else -> return true
            }
        }
        return false
    }

    private fun isIndicLinker(codePoint: Int): Boolean = when (codePoint) {
        0x094D, 0x09CD, 0x0ACD, 0x0B4D, 0x0C4D, 0x0D4D, 0x1039, 0x17D2,
        0x1A60, 0x1B44, 0x1BAB, 0x1CF5, 0x1CF6, 0xA9C0, 0xAAF6, 0x10A3F,
        0x11133, 0x113D0, 0x1193E, 0x11A3A, 0x11A47, 0x11A99, 0x11F42 -> true
        else -> false
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in 0x1F1E6..0x1F1FF

    /**
     * Unicode grapheme rules GB6–GB8: keep decomposed Hangul syllable jamo together.
     * Without this, a pasted Korean syllable can be emitted as several visible keystrokes.
     */
    private fun isHangulContinuation(previous: Int, current: Int): Boolean {
        val previousType = hangulType(previous)
        val currentType = hangulType(current)
        return when (previousType) {
            HangulType.L -> currentType == HangulType.L || currentType == HangulType.V ||
                currentType == HangulType.LV || currentType == HangulType.LVT
            HangulType.LV, HangulType.V -> currentType == HangulType.V || currentType == HangulType.T
            HangulType.LVT, HangulType.T -> currentType == HangulType.T
            HangulType.OTHER -> false
        }
    }

    private fun hangulType(codePoint: Int): HangulType = when {
        codePoint in 0x1100..0x115F || codePoint in 0xA960..0xA97C -> HangulType.L
        codePoint in 0x1160..0x11A7 || codePoint in 0xD7B0..0xD7C6 -> HangulType.V
        codePoint in 0x11A8..0x11FF || codePoint in 0xD7CB..0xD7FB -> HangulType.T
        codePoint in 0xAC00..0xD7A3 && (codePoint - 0xAC00) % 28 == 0 -> HangulType.LV
        codePoint in 0xAC00..0xD7A3 -> HangulType.LVT
        else -> HangulType.OTHER
    }

    private enum class HangulType { L, V, T, LV, LVT, OTHER }

    private val INDIC_CONSONANT_RANGES = intArrayOf(
        0x0915, 0x0939, 0x0958, 0x095F, 0x0978, 0x097F,
        0x0995, 0x09A8, 0x09AA, 0x09B0, 0x09B2, 0x09B2, 0x09B6, 0x09B9,
        0x09DC, 0x09DD, 0x09DF, 0x09DF, 0x09F0, 0x09F1,
        0x0A95, 0x0AA8, 0x0AAA, 0x0AB0, 0x0AB2, 0x0AB3, 0x0AB5, 0x0AB9,
        0x0AF9, 0x0AF9, 0x0B15, 0x0B28, 0x0B2A, 0x0B30, 0x0B32, 0x0B33,
        0x0B35, 0x0B39, 0x0B5C, 0x0B5D, 0x0B5F, 0x0B5F, 0x0B71, 0x0B71,
        0x0C15, 0x0C28, 0x0C2A, 0x0C39, 0x0C58, 0x0C5A, 0x0D15, 0x0D3A,
        0x1000, 0x102A, 0x103F, 0x103F, 0x1050, 0x1055, 0x105A, 0x105D,
        0x1061, 0x1061, 0x1065, 0x1066, 0x106E, 0x1070, 0x1075, 0x1081,
        0x108E, 0x108E, 0x1780, 0x17B3, 0x1A20, 0x1A54, 0x1B0B, 0x1B0C,
        0x1B13, 0x1B33, 0x1B45, 0x1B4C, 0x1B83, 0x1BA0, 0x1BAE, 0x1BAF,
        0x1BBB, 0x1BBD, 0xA989, 0xA98B, 0xA98F, 0xA9B2, 0xA9E0, 0xA9E4,
        0xA9E7, 0xA9EF, 0xA9FA, 0xA9FE, 0xAA60, 0xAA6F, 0xAA71, 0xAA73,
        0xAA7A, 0xAA7A, 0xAA7E, 0xAA7F, 0xAAE0, 0xAAEA, 0xABC0, 0xABDA,
        0x10A00, 0x10A00, 0x10A10, 0x10A13, 0x10A15, 0x10A17, 0x10A19, 0x10A35,
        0x11103, 0x11126, 0x11144, 0x11144, 0x11147, 0x11147, 0x11380, 0x11389,
        0x1138B, 0x1138B, 0x1138E, 0x1138E, 0x11390, 0x113B5,
        0x11900, 0x11906, 0x11909, 0x11909, 0x1190C, 0x11913, 0x11915, 0x11916,
        0x11918, 0x1192F, 0x11A00, 0x11A00, 0x11A0B, 0x11A32, 0x11A50, 0x11A50,
        0x11A5C, 0x11A83, 0x11B0A, 0x11B0A, 0x11DF1, 0x11DF1,
        0x11F04, 0x11F10, 0x11F12, 0x11F33
    )

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val ZERO_WIDTH_NON_JOINER = 0x200C
    private const val CARRIAGE_RETURN = 0x000D
    private const val LINE_FEED = 0x000A
}
