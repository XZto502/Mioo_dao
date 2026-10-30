package com.mioo.dao.ui.screens.thread

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.mioo.dao.ui.components.ComposerToolButtons
import com.mioo.dao.ui.components.CookieSelector
import com.mioo.dao.ui.components.DiceDialog
import com.mioo.dao.ui.components.KaomojiQuickPanel
import com.mioo.dao.ui.components.rememberEffectiveImeBottomPx
import com.mioo.dao.ui.components.toFile
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled

@Composable
fun ReplyInputArea(
    replyText: String,
    quotedPostNo: String?,
    cookies: List<String>,
    selectedCookieIndex: Int,
    isReplying: Boolean = false,
    replyError: String? = null,
    onCookieSelect: (Int) -> Unit,
    onReplyTextChange: (String) -> Unit,
    onCancelQuote: () -> Unit,
    onSend: (java.io.File?) -> Unit,
    hideWhenReading: Boolean = false,
    onContentHeightChanged: (Int) -> Unit = {}
) {
    var isInputFocused by remember { mutableStateOf(false) }
    var cookieMenuExpanded by remember { mutableStateOf(false) }
    var kaomojiMenuExpanded by remember { mutableStateOf(false) }
    var showDiceDialog by remember { mutableStateOf(false) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isPickingImage by remember { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        isPickingImage = false
        if (uri != null) attachedImageUri = uri
    }
    var previewAttachedUri by remember { mutableStateOf<Uri?>(null) }
    var ignoreImeCloseUntilMs by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var wasReplying by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = replyText, selection = TextRange(replyText.length)))
    }

    val hideSystemIme = remember(view, context, keyboardController, focusManager) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            val token = (context as? android.app.Activity)?.currentFocus?.windowToken
                ?: view.windowToken
                ?: (context as? android.app.Activity)?.window?.decorView?.windowToken
            if (token != null) {
                imm?.hideSoftInputFromWindow(token, 0)
            }
        }
    }

    fun insertAtCursor(snippet: String) {
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(0, text.length)
        val prefix = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
        val insert = prefix + snippet
        val newText = text.substring(0, start) + insert + text.substring(end)
        val newCursor = start + insert.length
        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
        onReplyTextChange(newText)
    }

    fun closeToolPanels() {
        if (kaomojiMenuExpanded) kaomojiMenuExpanded = false
    }

    val toolPanelOpen = kaomojiMenuExpanded
    BackHandler(enabled = toolPanelOpen) {
        closeToolPanels()
    }

    // 【共享底部高度槽位】底部总占位恒取 max(键盘, 面板)，绝不相加——
    // 从颜文字/骰子面板切到系统键盘时，槽位只在两个值之间平滑取大变化，永不过冲。
    val imeBottomPx = rememberEffectiveImeBottomPx()
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    var rememberedImeHeightPx by remember { mutableIntStateOf(0) }
    val defaultPanelHeightPx = with(density) { 280.dp.roundToPx() }
    // 面板高度跟随用户键盘实际高度（键盘升过一次后记录），否则兜底 280dp
    val targetPanelHeightPx =
        if (rememberedImeHeightPx > navBarBottomPx) rememberedImeHeightPx else defaultPanelHeightPx
    val isKeyboardRising = imeBottomPx > navBarBottomPx + with(density) { 40.dp.roundToPx() }
    LaunchedEffect(imeBottomPx, isKeyboardRising) {
        if (imeBottomPx > rememberedImeHeightPx) {
            rememberedImeHeightPx = imeBottomPx
        }
        // 键盘升起 → 同帧关面板（单一动画源）。这是键盘主动升起，不是用户点面板按钮。
        // 仍尊重刚点开面板后的 450ms 窗口：从键盘态点开面板会先 hideSystemIme，
        // 键盘下落动画期间 isKeyboardRising 同样为 true，无窗口会把刚打开的面板立刻关掉。
        if (isKeyboardRising) {
            if (android.os.SystemClock.uptimeMillis() < ignoreImeCloseUntilMs) return@LaunchedEffect
            if (kaomojiMenuExpanded) kaomojiMenuExpanded = false
        }
    }
    // 【核心】底部总占位 = max(键盘, 面板)，绝不相加
    val bottomSlotHeightPx =
        if (toolPanelOpen) maxOf(imeBottomPx, targetPanelHeightPx) else maxOf(imeBottomPx, navBarBottomPx)
    val bottomSlotHeightDp = with(density) { bottomSlotHeightPx.toDp() }

    LaunchedEffect(replyText) {
        if (textFieldValue.text != replyText) {
            textFieldValue = textFieldValue.copy(
                text = replyText,
                selection = TextRange(replyText.length)
            )
        }
    }

    LaunchedEffect(isReplying) {
        if (wasReplying && !isReplying && replyError == null) {
            attachedImageUri = null
            // 发送成功后收起输入法与颜文字面板
            hideSystemIme()
            if (kaomojiMenuExpanded) kaomojiMenuExpanded = false
        }
        wasReplying = isReplying
    }

    // 注意：不要在 replyText 变空时清 attachedImageUri —— 用户"选图→输入→删光文字"
    // 的正常操作会把附件静默清掉。附件只在发送成功路径（isReplying 回落）清理。

    // Show error toast when replyError changes
    LaunchedEffect(replyError) {
        if (replyError != null) {
            android.widget.Toast.makeText(context, "回复失败: $replyError", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // 毛玻璃贴屏幕物理底边，由内容下方的共享槽位把控件顶起来：
    // 无键盘无面板时槽位 = 导航条高度（与现在贴导航条一致）；
    // 键盘/面板谁高用谁，玻璃始终铺到屏幕底无断层。
    val reducedComposerMotion = isReducedMotionEnabled()
    val keepVisible = isInputFocused || replyText.isNotEmpty() || attachedImageUri != null ||
        quotedPostNo != null || isReplying || toolPanelOpen || cookieMenuExpanded ||
        showDiceDialog || previewAttachedUri != null || isKeyboardRising || isPickingImage
    AnimatedVisibility(
        visible = !hideWhenReading || keepVisible,
        enter = if (reducedComposerMotion) fadeIn(MiooMotion.tweenOut(1)) else
            slideInVertically(MiooMotion.bottomBarTween()) { it } + fadeIn(MiooMotion.bottomBarTween()) +
                expandVertically(MiooMotion.bottomBarTween(), expandFrom = Alignment.Bottom),
        exit = if (reducedComposerMotion) fadeOut(MiooMotion.tweenExit(1)) else
            slideOutVertically(MiooMotion.bottomBarTween()) { it } + fadeOut(MiooMotion.bottomBarTween()) +
                shrinkVertically(MiooMotion.bottomBarTween(), shrinkTowards = Alignment.Bottom)
    ) {
    Surface(
        color = DaoTheme.colors.glassNavBar,
        tonalElevation = 0.dp,
        // Measure full content inside AnimatedVisibility, not its animated outer bounds.
        // The list retains this end inset while the composer slides out and back in.
        modifier = Modifier.fillMaxWidth().onSizeChanged { onContentHeightChanged(it.height) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val reducedMotion = isReducedMotionEnabled()
                AnimatedVisibility(
                    visible = quotedPostNo != null,
                    enter = MiooMotion.softExpandEnter(reducedMotion),
                    exit = MiooMotion.softExpandExit(reducedMotion)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "正在引用: >>No.$quotedPostNo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        IconButton(
                            onClick = onCancelQuote,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消引用",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Image Preview (if attached)
                AnimatedVisibility(
                    visible = attachedImageUri != null,
                    enter = MiooMotion.softExpandEnter(reducedMotion),
                    exit = MiooMotion.softExpandExit(reducedMotion)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(attachedImageUri),
                                contentDescription = "Preview",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { previewAttachedUri = attachedImageUri }
                            )
                            IconButton(
                                onClick = { attachedImageUri = null },
                                // 不要黑色圆底：只留白色 ×，靠图标自身可见性
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 1. Outlined Text Field (full width)
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onReplyTextChange(newValue.text)
                    },
                    placeholder = { Text("回复...", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            isInputFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                closeToolPanels()
                            }
                        },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Control row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cookie selector
                    CookieSelector(
                        cookies = cookies,
                        selectedIndex = selectedCookieIndex,
                        expanded = cookieMenuExpanded,
                        onExpandedChange = { cookieMenuExpanded = it },
                        onSelect = onCookieSelect
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 骰娘 · 颜文字 · 图片（同组）
                    ComposerToolButtons(
                        diceSelected = false,
                        kaomojiSelected = kaomojiMenuExpanded,
                        hasImage = attachedImageUri != null,
                        onDiceClick = { showDiceDialog = true },
                        onKaomojiClick = {
                            val open = !kaomojiMenuExpanded
                            kaomojiMenuExpanded = open
                            if (open) {
                                ignoreImeCloseUntilMs = android.os.SystemClock.uptimeMillis() + 450L
                                hideSystemIme()
                            }
                        },
                        onImageClick = {
                            closeToolPanels()
                            isPickingImage = true
                            imagePickerLauncher.launch("image/*")
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Send Button
                    val isSendActive = (replyText.isNotBlank() || attachedImageUri != null) && !isReplying
                    val sendIconColor = if (isSendActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

                    IconButton(
                        onClick = {
                            val file = attachedImageUri?.toFile(context)
                            if (attachedImageUri != null && file == null) {
                                android.widget.Toast.makeText(context, "图片读取失败", android.widget.Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            onSend(file)
                        },
                        enabled = isSendActive
                    ) {
                        if (isReplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "发送",
                                tint = sendIconColor
                            )
                        }
                    }
                }
            }

            // 【共享底部槽位】max(键盘, 面板)，绝不相加。面板开时直接填充槽位
            // （无收起动画，高度变化完全由槽位驱动 = 单一动画源，杜绝 ime+面板叠加过冲）。
            // 毛玻璃铺底直达屏幕底，面板内容 navigationBarsPadding 防撞小白条。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomSlotHeightDp)
            ) {
                if (toolPanelOpen) {
                    KaomojiQuickPanel(
                        onInsert = { insertAtCursor(it) },
                        modifier = Modifier.navigationBarsPadding(),
                        heightDp = with(density) {
                            (bottomSlotHeightPx - navBarBottomPx).coerceAtLeast(0)
                                .toDp().value.roundToInt()
                        }
                    )
                }
            }
        }
    }

    } // AnimatedVisibility: composer state stays mounted above.

    if (showDiceDialog) {
        DiceDialog(
            onDismiss = { showDiceDialog = false },
            onInsert = { expr ->
                insertAtCursor(expr)
                showDiceDialog = false
            }
        )
    }

    previewAttachedUri?.let { uri ->
        Dialog(
            onDismissRequest = { previewAttachedUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { previewAttachedUri = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "附件预览",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
