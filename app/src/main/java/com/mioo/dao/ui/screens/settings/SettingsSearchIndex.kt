package com.mioo.dao.ui.screens.settings

/**
 * Stable section IDs shared between FeatureGuideScreen and SettingsScreen
 * for deep-linking and in-page search jump/highlight.
 */
object SettingsSectionIds {
    const val APPEARANCE = "appearance"
    const val KAOMOJI = "kaomoji"
    const val READING = "reading"
    const val COOKIES = "cookies"
    const val BLOCKS = "blocks"
    const val SUBSCRIPTION = "subscription"
    const val GESTURES = "gestures"
    const val NETWORK = "network"
    const val BACKUP = "backup"
    const val OFFLINE = "offline"
}

data class SettingSectionMeta(
    val id: String,
    val title: String,
    val aliases: List<String>
)

val SETTINGS_SECTIONS: List<SettingSectionMeta> = listOf(
    SettingSectionMeta(
        id = SettingsSectionIds.APPEARANCE,
        title = "外观与主题",
        aliases = listOf(
            "主题", "字号", "头图", "字体", "模糊", "行距",
            "深色", "浅色", "暗黑", "纯黑", "amoled", "配色",
            "主题色", "毛玻璃", "顶栏", "顶栏背景", "背景", "appearance", "theme"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.KAOMOJI,
        title = "颜文字与创作",
        aliases = listOf(
            "颜文字", "快捷输入", "草稿", "草稿箱", "创作者", "创作模式",
            "涂鸦", "表情", "编辑器", "kaomoji"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.READING,
        title = "阅读与排版",
        aliases = listOf(
            "阅读", "排版", "字号", "字体", "行距", "行间距",
            "自定义字体", "预览行数", "引用", "自动显示",
            "骰点", "跑团", "安科", "分享", "水印", "reading"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.COOKIES,
        title = "发言与饼干",
        aliases = listOf(
            "饼干", "确认", "鉴权", "userhash", "扫码", "二维码",
            "备注", "发言", "cookie", "cookies", "auth"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.BLOCKS,
        title = "屏蔽管理",
        aliases = listOf(
            "屏蔽", "拉黑", "关键词", "关键字", "过滤", "隐藏",
            "黑名单", "block", "blocks"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.SUBSCRIPTION,
        title = "订阅与通知",
        aliases = listOf(
            "通知", "提醒", "免打扰", "间隔", "后台",
            "订阅", "收藏夹", "uuid", "轮询", "推送", "自启动", "subscription"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.GESTURES,
        title = "手势与交互",
        aliases = listOf(
            "预测返回", "预测性返回", "手势", "防误触", "滑动", "侧滑",
            "更新通道", "正式版", "测试版", "gestures"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.NETWORK,
        title = "网络与诊断",
        aliases = listOf(
            "卡", "连不上", "诊断", "兼容", "网络",
            "通信", "超时", "延迟", "dns", "第三方", "省流量",
            "默认打开", "链接", "network"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.BACKUP,
        title = "备份与恢复",
        aliases = listOf(
            "备份", "恢复", "导入", "导出", "换机", "json", "迁移", "backup"
        )
    ),
    SettingSectionMeta(
        id = SettingsSectionIds.OFFLINE,
        title = "离线与缓存",
        aliases = listOf(
            "离线", "缓存", "清理", "预加载", "图片缓存",
            "自动更新", "wi-fi", "wifi", "offline", "cache"
        )
    )
)

fun matchSettingSectionMetas(query: String): List<SettingSectionMeta> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return SETTINGS_SECTIONS.filter { section ->
        section.title.lowercase().contains(needle) ||
            section.id.lowercase().contains(needle) ||
            section.aliases.any { alias -> alias.lowercase().contains(needle) }
    }
}

fun matchSettingSections(query: String): Set<String> =
    matchSettingSectionMetas(query).mapTo(LinkedHashSet()) { it.id }
