package com.mioo.dao.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 回复栏 / 举报对话框 / 发表新串共用的饼干选择器：40dp MD3 胶囊 + 自绘弹出菜单。
 * 弹出菜单锚定在仅包裹胶囊自身的 Box 上，收起动画播完才卸载 Popup。
 */
@Composable
fun CookieSelector(
    cookies: List<String>,
    selectedIndex: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit
) {
    val currentCookie = cookies.getOrNull(selectedIndex) ?: "选择饼干"
    val displayName = remember(currentCookie) { parseCookieName(currentCookie) }
    val displayText = if (displayName.length > 8) displayName.take(8) + "..." else displayName
    // 胶囊色不透明：透出下层文字的 alpha 色会在玻璃底栏上糊字
    val capsuleColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "cookieCapsule"
    )
    val capsuleBorderColor = if (expanded) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "cookieArrow"
    )

    Box {
        Surface(
            onClick = { onExpandedChange(true) },
            shape = CircleShape,
            color = capsuleColor,
            border = BorderStroke(1.dp, capsuleBorderColor),
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(15.dp)
                        .rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // 菜单挂载状态：收起动画播完才卸载 Popup（isIdle 前不能走 if）
        val menuState = remember { MutableTransitionState(false) }
        menuState.targetState = expanded
        if (menuState.currentState || menuState.targetState || !menuState.isIdle) {
            val density = LocalDensity.current
            // 正向 y 微调：把翻转后悬在锚点上方的菜单向下拉回，
            // 让卡片底边贴在胶囊顶边上方 4-6dp，不再飞向输入框上方
            val menuPositionProvider = remember(density) {
                CookieMenuPositionProvider(density, DpOffset(0.dp, 4.dp))
            }
            Popup(
                popupPositionProvider = menuPositionProvider,
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visibleState = menuState,
                    enter = scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(160),
                        transformOrigin = TransformOrigin(0f, 0f)
                    ) + fadeIn(tween(120)),
                    exit = scaleOut(
                        targetScale = 0.94f,
                        animationSpec = tween(140),
                        transformOrigin = TransformOrigin(0f, 0f)
                    ) + fadeOut(tween(120))
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier.widthIn(min = 148.dp, max = 210.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                // 四边等宽 8dp：自绘卡片没有 DropdownMenu 内置的
                                // DropdownMenuVerticalPadding，上下留白须自己给足，
                                // 否则选中胶囊会顶到大圆角卡片边缘
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            cookies.forEachIndexed { index, cookie ->
                                val isSelected = index == selectedIndex
                                Surface(
                                    onClick = {
                                        onSelect(index)
                                        onExpandedChange(false)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = parseCookieName(cookie),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 复刻 M3 1.2.0 DropdownMenuPositionProvider 的定位候选与回退：
 * 优先贴锚点下方，放不下依次回退锚点上方、窗口上/下缘（水平先锚点左/右对齐，再回退窗口左/右缘），
 * 让自绘 Popup 的落点在任意位置都与原 DropdownMenu 一致。
 */
private class CookieMenuPositionProvider(
    private val density: Density,
    private val contentOffset: DpOffset
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val offsetX = with(density) { contentOffset.x.roundToPx() }
        val offsetY = with(density) { contentOffset.y.roundToPx() }
        // M3 MenuDefaults 的 MenuVerticalMargin
        val verticalMargin = with(density) { 48.dp.roundToPx() }
        val menuWidth = popupContentSize.width
        val menuHeight = popupContentSize.height

        val xCandidates = listOf(
            anchorBounds.left + offsetX,
            anchorBounds.right - menuWidth + offsetX,
            windowHorizontal(anchorBounds, windowSize, layoutDirection, menuWidth)
        )
        val x = xCandidates.firstOrNull { it >= 0 && it + menuWidth <= windowSize.width }
            ?: xCandidates.last()

        val yCandidates = listOf(
            anchorBounds.bottom + offsetY,
            anchorBounds.top - menuHeight + offsetY,
            anchorBounds.top - menuHeight / 2 + offsetY,
            windowVertical(anchorBounds, windowSize, menuHeight, verticalMargin)
        )
        val y = yCandidates.firstOrNull {
            it >= verticalMargin && it + menuHeight <= windowSize.height - verticalMargin
        } ?: yCandidates.last()

        return IntOffset(x, y)
    }

    /** 对应 WindowAlignmentMarginPosition.Horizontal(margin = 0)：锚点偏左贴窗口左缘，偏右贴右缘，放不下居中 */
    private fun windowHorizontal(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        menuWidth: Int
    ): Int {
        if (menuWidth >= windowSize.width) {
            return Alignment.CenterHorizontally.align(menuWidth, windowSize.width, layoutDirection)
        }
        val x = if (anchorBounds.center.x < windowSize.width / 2) {
            0
        } else {
            windowSize.width - menuWidth
        }
        return x.coerceIn(0, windowSize.width - menuWidth)
    }

    /** 对应 WindowAlignmentMarginPosition.Vertical(margin = 48dp)：锚点偏上贴窗口上缘，偏下贴下缘，放不下居中 */
    private fun windowVertical(
        anchorBounds: IntRect,
        windowSize: IntSize,
        menuHeight: Int,
        verticalMargin: Int
    ): Int {
        if (menuHeight >= windowSize.height - 2 * verticalMargin) {
            return Alignment.CenterVertically.align(menuHeight, windowSize.height)
        }
        val y = if (anchorBounds.center.y < windowSize.height / 2) {
            verticalMargin
        } else {
            windowSize.height - verticalMargin - menuHeight
        }
        return y.coerceIn(verticalMargin, windowSize.height - verticalMargin - menuHeight)
    }
}

/**
 * Extract a human-readable display name from a cookie JSON string.
 * Returns the "name" field if available, otherwise falls back to "cookie" or "userhash".
 * Returns the raw cookie string if it's not valid JSON.
 */
internal fun parseCookieName(cookie: String): String {
    return try {
        if (cookie.startsWith("{")) {
            val json = org.json.JSONObject(cookie)
            var name = json.optString("name")
            if (name.isEmpty()) name = json.optString("cookie", "")
            if (name.isEmpty()) name = json.optString("userhash", cookie)
            name
        } else {
            cookie
        }
    } catch (_: Exception) {
        cookie
    }
}
