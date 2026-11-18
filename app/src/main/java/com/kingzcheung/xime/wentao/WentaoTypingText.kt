package com.kingzcheung.xime.wentao

/**
 * 将剪贴板文本拆成用户感知字符（扩展字素簇），而不是 UTF-16 code unit。
 * 中文、代理对 emoji、组合音标、肤色修饰符、ZWJ 家庭 emoji、国旗和 CRLF
 * 均不得在中间断开。
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
            codePoint in 0xE0020..0xE007F
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in 0x1F1E6..0x1F1FF

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val CARRIAGE_RETURN = 0x000D
    private const val LINE_FEED = 0x000A
}
