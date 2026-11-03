package com.mioo.dao.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FreeCopyDialog(text: String, onDismiss: () -> Unit, postId: String = "") {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val pageColor = if (dark) {
        if (MaterialTheme.colorScheme.background == Color.Black) Color.Black else Color(0xFF0E1015)
    } else Color(0xFFF0F1F4)
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val reducedMotion = isReducedMotionEnabled()
    var closing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val parsedText by produceState<String?>(initialValue = null, key1 = text) {
        value = withContext(Dispatchers.Default) {
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString().trim()
        }
    }
    val cleanText = parsedText.orEmpty()
    val quickItems = remember(cleanText, postId) {
        (listOfNotNull(postId.takeIf { it.isNotBlank() }?.let { "No.$it" }) +
            Regex("https?://[^\\s\\u4e00-\\u9fa5\"<>]+|>>No\\.\\d+")
                .findAll(cleanText).map { it.value.removePrefix(">>") }.toList()).distinct()
    }
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity, view, dark) {
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            if (oldStatus != null) controller.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    fun close() {
        if (closing) return
        closing = true
    }
    LaunchedEffect(parsedText, closing, reducedMotion) {
        if (closing) {
            progress.animateTo(0f, tween(if (reducedMotion) 0 else MiooMotion.DurationExitFast, easing = MiooMotion.EaseOut))
            withFrameNanos { }
            onDismiss()
        } else if (parsedText != null) {
            withFrameNanos { }
            progress.animateTo(1f, tween(if (reducedMotion) 0 else MiooMotion.DurationModal, easing = MiooMotion.EaseOut))
        }
    }
    BackHandler { close() }
    if (parsedText == null) return
    Box(Modifier.fillMaxSize().zIndex(100f).graphicsLayer {
        alpha = progress.value
        val startScale = if (reducedMotion) 1f else MiooMotion.ScaleEnterFrom
        scaleX = startScale + (1f - startScale) * alpha
        scaleY = scaleX
    }.background(pageColor)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { close() }) { Icon(Icons.Default.ArrowBack, "返回") }
                Text("自由复制", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val buttonColor by animateColorAsState(if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, label = "copyColor")
                Surface(onClick = {
                    if (!copied && !closing && parsedText != null) {
                        clipboard.setText(AnnotatedString(cleanText))
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copied = true
                        scope.launch { delay(350); close() }
                    }
                }, shape = CircleShape, color = buttonColor) {
                    AnimatedContent(copied, label = "copyAll") { done ->
                        Text(if (done) "✓ 已复制" else "复制全文", Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Surface(
                Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 12.dp, vertical = 8.dp)
                    ,
                shape = RoundedCornerShape(20.dp),
                color = if (dark) Color(0xFF16181E) else Color.White
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("长按文字可自由拖动选区", Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                        if (postId.isNotBlank()) Text("No.$postId", fontSize = 12.sp, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                    }
                    Spacer(Modifier.height(12.dp))
                    Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    if (parsedText != null) {
                        SelectionContainer {
                            Text(cleanText, fontSize = 15.5.sp, lineHeight = 25.sp, letterSpacing = 0.25.sp,
                                fontWeight = FontWeight.Normal, color = if (dark) Color(0xFFE3E6EA) else Color(0xFF22262C))
                        }
                    }
                }
            }
            LazyRow(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickItems, key = { it }) { value ->
                    var wasCopied by remember { mutableStateOf(false) }
                    LaunchedEffect(wasCopied) { if (wasCopied) { delay(1200); wasCopied = false } }
                    val scale by animateFloatAsState(if (wasCopied) 0.96f else 1f, label = "copyScale")
                    val color by animateColorAsState(if (wasCopied) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f), label = "copyChip")
                    Surface(onClick = {
                        clipboard.setText(AnnotatedString(value))
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        wasCopied = true
                    }, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, shape = CircleShape, color = color) {
                        AnimatedContent(wasCopied, label = "copyItem") { done ->
                            Text(if (done) "✓ 已复制" else value, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
