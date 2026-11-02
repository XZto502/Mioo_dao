package com.mioo.dao.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import com.mioo.dao.ui.theme.settingsItemStyle
import com.mioo.dao.ui.theme.settingsDescriptionStyle
import com.mioo.dao.ui.theme.readingTextStyle
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mioo.dao.data.repository.historyCookieIdentity
import com.mioo.dao.data.api.CookieInterceptor
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.immersiveDialogProperties
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import com.mioo.dao.ui.theme.readingCardColor

/** Choose locally first; only commit the selection and send after explicit confirmation. */
@Composable
fun ConfirmSendCookieDialog(
    cookies: List<String>, notes: Map<String, String>, selectedIndex: Int,
    onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var chosen by remember { mutableStateOf(cookies.getOrNull(selectedIndex)) }
    var closing by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val reduced = isReducedMotionEnabled()
    fun close(selection: Pair<Int, String>? = null) {
        if (!closing) {
            confirmed = selection
            closing = true
            visible.targetState = false
        }
    }
    LaunchedEffect(closing, visible.isIdle, visible.currentState) {
        if (closing && visible.isIdle && !visible.currentState) {
            val result = confirmed
            if (result == null) onDismiss() else onConfirm(result.first, result.second)
        }
    }
    Dialog(onDismissRequest = { close() }, properties = immersiveDialogProperties()) {
        ImmersiveDialogEffect()
        AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(if (reduced) 0 else 180)) + scaleIn(initialScale = 0.97f, animationSpec = tween(if (reduced) 0 else 220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(if (reduced) 0 else 140)) + scaleOut(targetScale = 0.98f, animationSpec = tween(if (reduced) 0 else 160))) {
            Surface(shape = RoundedCornerShape(20.dp), color = readingCardColor(), tonalElevation = 0.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("确认发言饼干", style = readingTextStyle(17f, 24f, FontWeight.SemiBold))
                    Spacer(Modifier.height(6.dp))
                    Text("请选择本次发送使用的饼干。", style = settingsDescriptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (cookies.isEmpty()) {
                        Text("还没有可用饼干，请先在应用设置中添加。", style = settingsDescriptionStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(cookies) { _, cookie ->
                                val selected = chosen == cookie
                                val rowColor by animateColorAsState(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer else readingCardColor(),
                                    animationSpec = tween(if (reduced) 0 else 150), label = "confirmedCookieColor")
                                Surface(shape = RoundedCornerShape(12.dp),
                                    color = rowColor,
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !closing) { chosen = cookie }) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = selected, onClick = null, enabled = !closing)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            val identity = historyCookieIdentity(cookie)
                                            Text(notes[cookie]?.takeIf { it.isNotBlank() } ?: identity,
                                                style = settingsItemStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (!notes[cookie].isNullOrBlank()) Text(identity, style = settingsDescriptionStyle(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        if (chosen != null && CookieInterceptor.extractHash(chosen).isNullOrBlank()) {
                            Text("所选饼干不可用，请选择其他饼干或在设置中检查。",
                                style = settingsDescriptionStyle(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { close() }, enabled = !closing) { Text("取消", style = settingsItemStyle()) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val cookie = chosen
                            val index = cookies.indexOf(cookie)
                            if (cookie != null && !CookieInterceptor.extractHash(cookie).isNullOrBlank() && index >= 0) close(index to cookie)
                        }, enabled = !closing && !CookieInterceptor.extractHash(chosen).isNullOrBlank() && cookies.any { it == chosen }) { Text("确认发送", style = settingsItemStyle()) }
                    }
                }
            }
        }
    }
}
