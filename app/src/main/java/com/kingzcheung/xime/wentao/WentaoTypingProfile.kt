package com.kingzcheung.xime.wentao

/**
 * 文涛逐字输入速度模型。
 *
 * 9 个预设档位覆盖 6,000–30,000 个用户感知字符/小时，步进 3,000。
 * 自定义档位不设产品层上限；运行时以纳秒保存间隔，避免毫秒整数除法
 * 把高速档位提前截断为 0。最终调度器应使用支持纳秒或至少 1ms 的时钟。
 */
data class WentaoTypingProfile private constructor(
    val label: String,
    val charactersPerHour: Long,
    val isCustom: Boolean
) {
    /** 理想平均间隔，单位纳秒。 */
    val intervalNanos: Long
        get() = NANOS_PER_HOUR / charactersPerHour

    /** 理想平均间隔，单位毫秒；仅用于 UI 展示。 */
    val intervalMillis: Double
        get() = intervalNanos / NANOS_PER_MILLI

    /**
     * 按随机系数调整间隔。randomFactor 应在 [-0.25, 0.25]，
     * 调用方可每个字符重新抽样，形成自然的速度波动。
     */
    fun intervalWithJitterNanos(randomFactor: Double): Long {
        val factor = randomFactor.coerceIn(-0.25, 0.25)
        return (intervalNanos * (1.0 + factor)).toLong().coerceAtLeast(MIN_INTERVAL_NANOS)
    }

    companion object {
        private const val NANOS_PER_HOUR = 3_600_000_000_000L
        private const val NANOS_PER_MILLI = 1_000_000.0
        private const val MIN_INTERVAL_NANOS = 1_000_000L

        enum class Preset(
            val title: String,
            val charactersPerHour: Long
        ) {
            LEVEL_1("1档 · 6000字/小时", 6_000L),
            LEVEL_2("2档 · 9000字/小时", 9_000L),
            LEVEL_3("3档 · 12000字/小时", 12_000L),
            LEVEL_4("4档 · 15000字/小时", 15_000L),
            LEVEL_5("5档 · 18000字/小时", 18_000L),
            LEVEL_6("6档 · 21000字/小时", 21_000L),
            LEVEL_7("7档 · 24000字/小时", 24_000L),
            LEVEL_8("8档 · 27000字/小时", 27_000L),
            LEVEL_9("9档 · 30000字/小时", 30_000L)
        }

        fun preset(preset: Preset): WentaoTypingProfile =
            WentaoTypingProfile(preset.title, preset.charactersPerHour, false)

        /** 自定义速度必须为正数，不设 30,000 字/小时的预设上限。 */
        fun custom(charactersPerHour: Long): WentaoTypingProfile {
            require(charactersPerHour > 0L) { "charactersPerHour must be positive" }
            return WentaoTypingProfile(
                label = "自定义 · ${charactersPerHour}字/小时",
                charactersPerHour = charactersPerHour,
                isCustom = true
            )
        }

        fun presets(): List<WentaoTypingProfile> =
            Preset.entries.map(::preset)
    }
}

/** 标点停顿规则：对标点之后的下一次调度增加间隔。 */
object WentaoTypingPacing {
    fun punctuationMultiplier(character: String): Int = when {
        character.any { it == '\\n' || it == '\\r' } -> 5
        character.any { it in "。！？!?" } -> 4
        character.any { it in "；;：" } -> 3
        character.any { it in "，、," } -> 2
        else -> 1
    }

    fun nextDelayNanos(
        previousCharacter: String,
        profile: WentaoTypingProfile,
        randomFactor: Double = 0.0
    ): Long = profile.intervalWithJitterNanos(randomFactor) *
        punctuationMultiplier(previousCharacter)
}
