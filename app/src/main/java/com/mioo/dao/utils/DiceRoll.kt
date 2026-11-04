package com.mioo.dao.utils

/**
 * 跑团/安科骰点徽章。
 *
 * X 岛原文是 `<b>2<small>[0,9]</small></b>`，剥标签后为 `2[0,9]`：出目在前，区间在后。
 */
enum class DiceRulePreset(
    val label: String,
    val desc: String,
    val shortLabel: String,
    val menuLabel: String
) {
    NEUTRAL_DEFAULT("默认纯色", "不标金/红", "默认纯色", "默认纯色"),
    ANKO_HIGH_GOOD("安科规则", "大数优", "安科(大优)", "安科规则(大数优)"),
    COC_LOW_GOOD("CoC规则", "小数优", "CoC(小优)", "CoC规则(小数优)");

    companion object {
        /** 旧存档里的智能识别 / 仅高亮 / DND 都归到默认纯色。 */
        fun fromStored(name: String): DiceRulePreset? = when (name) {
            "NEUTRAL_DEFAULT", "HIGHLIGHT_ONLY", "SMART_NEUTRAL", "DND_D20" -> NEUTRAL_DEFAULT
            "ANKO_HIGH_GOOD" -> ANKO_HIGH_GOOD
            "COC_LOW_GOOD" -> COC_LOW_GOOD
            else -> null
        }
    }
}

/** 胶囊配色档。默认纯色下全部是 [NORMAL]。 */
enum class DiceLevel { NORMAL, CRITICAL_GOOD, CRITICAL_BAD }

/** 一枚骰子出目：result 出目；min/max 区间。 */
data class DiceRollToken(
    val result: Int,
    val min: Int,
    val max: Int
) {
    val span: Int get() = max - min
}

/** 骰点在纯文本中的位置（供 AnnotatedString 精确替换为内联胶囊）。 */
data class DiceRollMatch(
    val token: DiceRollToken,
    val start: Int,
    val end: Int
)

/** 解析产物中的一枚骰子胶囊：[id] 对应 InlineTextContent 占位符。 */
data class DiceBadgeSpec(
    val id: String,
    val token: DiceRollToken,
    val level: DiceLevel
) {
    /** 胶囊完整文本，如 `2 [0,9]`、`98 [1,100]`。 */
    val badgeText: String get() = "${token.result} [${token.min},${token.max}]"
}

object DiceRoll {

    /** 出目前缀区间：`2[0,9]`、`98[1,100]`，允许数字与括号间的空白。 */
    private val DICE_PATTERN = Regex("(\\d+)\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]")

    /** PO 红字骰点格式：`1d100=57`（允许全角等号/冒号）。 */
    private val XDY_PATTERN = Regex("(\\d{1,2})d(\\d{1,4})\\s*[=＝:]\\s*(\\d{1,4})", RegexOption.IGNORE_CASE)

    private val TAGS = Regex("<[^>]+>")

    fun containsDice(html: String): Boolean {
        if (html.isEmpty() || !html.any { it.isDigit() }) return false
        return findTokens(html.replace(TAGS, "")).isNotEmpty()
    }

    /** 找出一段纯文本里的全部骰点（按位置排序，重叠去重）。 */
    fun findTokens(text: String): List<DiceRollMatch> {
        if (!text.any { it.isDigit() }) return emptyList()
        data class Raw(val token: DiceRollToken, val start: Int, val end: Int)
        val raw = mutableListOf<Raw>()
        for (m in DICE_PATTERN.findAll(text)) {
            val result = m.groupValues[1].toIntOrNull() ?: continue
            val min = m.groupValues[2].toIntOrNull() ?: continue
            val max = m.groupValues[3].toIntOrNull() ?: continue
            if (max < min || result < min || result > max) continue
            raw.add(Raw(DiceRollToken(result, min, max), m.range.first, m.range.last + 1))
        }
        for (m in XDY_PATTERN.findAll(text)) {
            val faces = m.groupValues[2].toIntOrNull() ?: continue
            val result = m.groupValues[3].toIntOrNull() ?: continue
            if (faces <= 0 || result < 1 || result > faces) continue
            raw.add(Raw(DiceRollToken(result, 1, faces), m.range.first, m.range.last + 1))
        }
        val result = mutableListOf<DiceRollMatch>()
        var lastEnd = -1
        for (m in raw.sortedBy { it.start }) {
            if (m.start < lastEnd) continue
            result.add(DiceRollMatch(m.token, m.start, m.end))
            lastEnd = m.end
        }
        return result
    }

    /**
     * 默认纯色一律 [DiceLevel.NORMAL]。
     * 只有用户改成安科或 CoC 后，极值才返回金/红。
     */
    fun judge(preset: DiceRulePreset, token: DiceRollToken): DiceLevel {
        val span = token.max - token.min
        if (preset == DiceRulePreset.NEUTRAL_DEFAULT || span <= 0) return DiceLevel.NORMAL
        val highThreshold: Int
        val lowThreshold: Int
        if (span >= 19) {
            highThreshold = token.max - (span * 0.05f).toInt()
            lowThreshold = token.min + (span * 0.05f).toInt()
        } else {
            highThreshold = token.max
            lowThreshold = token.min
        }
        return when (preset) {
            DiceRulePreset.ANKO_HIGH_GOOD -> when {
                token.result >= highThreshold -> DiceLevel.CRITICAL_GOOD
                token.result <= lowThreshold -> DiceLevel.CRITICAL_BAD
                else -> DiceLevel.NORMAL
            }
            DiceRulePreset.COC_LOW_GOOD -> when {
                token.result <= lowThreshold -> DiceLevel.CRITICAL_GOOD
                token.result >= highThreshold -> DiceLevel.CRITICAL_BAD
                else -> DiceLevel.NORMAL
            }
            DiceRulePreset.NEUTRAL_DEFAULT -> DiceLevel.NORMAL
        }
    }
}
