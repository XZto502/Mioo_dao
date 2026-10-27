package com.mioo.dao.utils

/**
 * 跑团/安科骰点高亮的判定体系。
 *
 * X岛骰子机制（用户确认）：回复里插入 `[起,止]`（如 [1,100]），岛方掷骰后输出样例为
 * **`1[1,2]`** —— 出目数字直接前缀在原区间前。因此正文里的骰点形如 `96[1,100]`
 * （出目 96、区间 1-100），result/min/max 三元组完整。另有 PO 红字 `XdY=结果` 格式。
 */
enum class DiceRulePreset(val label: String, val desc: String) {
    HIGHLIGHT_ONLY("仅高亮", "默认"),
    ANKO_HIGH_GOOD("安科规则", "d100 大数优"),
    COC_LOW_GOOD("CoC 规则", "d100 小数优"),
    DND_D20("DND 规则", "d20 极值")
}

/** 骰点胶囊的高亮档位。 */
enum class DiceTier { NORMAL, EXTREME_VALUE, CRITICAL_SUCCESS, CRITICAL_FAILURE }

/** 一枚骰子出目：result 出目；min/max 区间（真实下限，[0,1] 型为 0）。 */
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
    val tier: DiceTier
) {
    /** 胶囊文本 = 原文完整内容（出目 + 原区间），如 `96[1,100]`。 */
    val badgeText: String get() = "${token.result}[${token.min},${token.max}]"
}

object DiceRoll {

    /** 岛方掷骰输出：出目前缀原区间，如 `1[1,2]`、`96[1,100]`。 */
    private val DICE_PATTERN = Regex("(\\d{1,4})\\[(\\d{1,4})\\s*,\\s*(\\d{1,4})\\]")

    /** PO 红字骰点格式：`1d100=57`（允许全角等号/冒号）。 */
    private val XDY_PATTERN = Regex("(\\d{1,2})d(\\d{1,4})\\s*[=＝:]\\s*(\\d{1,4})", RegexOption.IGNORE_CASE)

    /** 找出一段纯文本里的全部骰点（按位置排序，重叠去重）。 */
    fun findTokens(text: String): List<DiceRollMatch> {
        if (!text.any { it.isDigit() }) return emptyList()
        data class Raw(val token: DiceRollToken, val start: Int, val end: Int)
        val raw = mutableListOf<Raw>()
        for (m in DICE_PATTERN.findAll(text)) {
            val result = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            val max = m.groupValues[3].toInt()
            if (max < min || result < min || result > max) continue
            raw.add(Raw(DiceRollToken(result, min, max), m.range.first, m.range.last + 1))
        }
        for (m in XDY_PATTERN.findAll(text)) {
            val faces = m.groupValues[2].toInt()
            val result = m.groupValues[3].toInt()
            if (faces <= 0 || result < 1 || result > faces) continue
            raw.add(Raw(DiceRollToken(result, 1, faces), m.range.first, m.range.last + 1))
        }
        val result = mutableListOf<DiceRollMatch>()
        var lastEnd = -1
        for (m in raw.sortedBy { it.start }) {
            if (m.start < lastEnd) continue // 与前一骰点重叠（如 XdY 命中已被区间覆盖）
            result.add(DiceRollMatch(m.token, m.start, m.end))
            lastEnd = m.end
        }
        return result
    }

    /**
     * 按规则判定档位。
     */
    fun judge(preset: DiceRulePreset, token: DiceRollToken): DiceTier {
        val onBoundary = token.result == token.max || token.result == token.min
        return when (preset) {
            // 默认态：只做中性高亮，不判吉凶（大成功/大失败特殊渲染仅在选定规则后出现）
            DiceRulePreset.HIGHLIGHT_ONLY -> DiceTier.NORMAL
            DiceRulePreset.ANKO_HIGH_GOOD -> when {
                token.max == 100 && token.result >= 96 -> DiceTier.CRITICAL_SUCCESS
                token.max == 100 && token.result <= 5 -> DiceTier.CRITICAL_FAILURE
                token.span in 1..2 && onBoundary -> DiceTier.EXTREME_VALUE
                else -> DiceTier.NORMAL
            }
            DiceRulePreset.COC_LOW_GOOD -> when {
                token.max == 100 && token.result <= 5 -> DiceTier.CRITICAL_SUCCESS
                token.max == 100 && token.result >= 96 -> DiceTier.CRITICAL_FAILURE
                token.span in 1..2 && onBoundary -> DiceTier.EXTREME_VALUE
                else -> DiceTier.NORMAL
            }
            DiceRulePreset.DND_D20 -> when {
                token.max == 20 && token.result == 20 -> DiceTier.CRITICAL_SUCCESS
                token.max == 20 && token.result == 1 -> DiceTier.CRITICAL_FAILURE
                token.span in 1..2 && onBoundary -> DiceTier.EXTREME_VALUE
                else -> DiceTier.NORMAL
            }
        }
    }
}
