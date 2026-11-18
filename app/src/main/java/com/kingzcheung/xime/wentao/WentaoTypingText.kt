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
                    previousCodePoint == ZERO_WIDTH_JOINER ||
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
            codePoint == 0x1193F ||
            codePoint == 0x11941 ||
            codePoint == 0x11A3A ||
            codePoint in 0x11A84..0x11A89 ||
            codePoint == 0x11D46 ||
            codePoint == 0x11F02 ||
            codePoint in 0x13430..0x1343F

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
        val inIndicBlock =
            codePoint in 0x0900..0x0DFF ||
                codePoint in 0x1000..0x109F ||
                codePoint in 0x1780..0x17FF ||
                codePoint in 0x1A20..0x1AAF ||
                codePoint in 0x1B00..0x1BFF ||
                codePoint in 0xA980..0xAAFF ||
                codePoint in 0xABC0..0xABFF ||
                codePoint in 0x10A00..0x11FFF
        return inIndicBlock && Character.isLetter(codePoint)
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

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val ZERO_WIDTH_NON_JOINER = 0x200C
    private const val CARRIAGE_RETURN = 0x000D
    private const val LINE_FEED = 0x000A
}
