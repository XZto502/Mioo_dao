package com.mioo.dao.ui.screens.thread

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.mioo.dao.data.local.DraftEntity
import com.mioo.dao.ui.screens.forum.PostDraftCard
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import com.mioo.dao.ui.components.ComposerToolButtons
import com.mioo.dao.ui.components.CookieSelector
import com.mioo.dao.ui.components.ConfirmSendCookieDialog
import com.mioo.dao.ui.components.DiceDialog
import com.mioo.dao.ui.components.KaomojiQuickPanel
import com.mioo.dao.ui.components.rememberEffectiveImeBottomPx
import com.mioo.dao.ui.components.compressImageUnder2MB
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.mioo.dao.ui.theme.readingComposerColor
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled

internal class ReplyEditorState {
    val text = mutableStateOf(TextFieldValue())
    val attachment = mutableStateOf<Uri?>(null)
    val showDoodlePad = mutableStateOf(false)
    val preparingAttachment = mutableStateOf(false)
    val undo = mutableStateListOf<TextFieldValue>()
    val redo = mutableStateListOf<TextFieldValue>()

    fun clearHistory() {
        undo.clear()
        redo.clear()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplyInputArea(
    replyText: String,
    quotedPostNo: String?,
    cookies: List<String>,
    selectedCookieIndex: Int,
    confirmCookieBeforeSend: Boolean = false,
    cookieNotes: Map<String, String> = emptyMap(),
    isReplying: Boolean = false,
    replyError: String? = null,
    onCookieSelect: (Int) -> Unit,
    onReplyTextChange: (String) -> Unit,
    onCancelQuote: () -> Unit,
    onSend: (java.io.File?, String?) -> Unit,
    editorState: ReplyEditorState,
    fullScreen: Boolean = false,
    onOpen: () -> Unit = {},
    onClose: () -> Unit = {},
    threadId: String = "",
    title: String = "",
    name: String = "",
    onIdentityChange: (String, String) -> Unit = { _, _ -> },
    drafts: List<DraftEntity> = emptyList(),
    onSaveDraft: () -> Unit = {},
    onSelectDraft: (DraftEntity) -> Unit = {}
) {
    var cookieMenuExpanded by remember { mutableStateOf(false) }
    var showCookieConfirmation by remember { mutableStateOf(false) }
    var kaomojiMenuExpanded by remember { mutableStateOf(false) }
    var showDiceDialog by remember { mutableStateOf(false) }
    var attachedImageUri by editorState.attachment
    var showDrafts by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) attachedImageUri = uri
    }
    var ignoreImeCloseUntilMs by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var wasReplying by remember { mutableStateOf(false) }
    var textFieldValue by editorState.text
    fun applyEdit(value: TextFieldValue) {
        if (value.text != textFieldValue.text) {
            editorState.undo.add(textFieldValue.copy(composition = null))
            if (editorState.undo.size > 100) editorState.undo.removeAt(0)
            editorState.redo.clear()
        }
        textFieldValue = value
        if (value.text != replyText) onReplyTextChange(value.text)
    }

    fun restoreEdit(undo: Boolean) {
        val source = if (undo) editorState.undo else editorState.redo
        val destination = if (undo) editorState.redo else editorState.undo
        if (source.isEmpty()) return
        destination.add(textFieldValue.copy(composition = null))
        textFieldValue = source.removeAt(source.lastIndex)
        onReplyTextChange(textFieldValue.text)
    }

    fun insertPair(open: String, close: String) {
        val value = textFieldValue
        val start = value.selection.min
        val end = value.selection.max
        val selected = value.text.substring(start, end)
        applyEdit(TextFieldValue(value.text.replaceRange(start, end, open + selected + close),
            TextRange(start + open.length + selected.length)))
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
        val start = selection.min.coerceIn(0, text.length)
        val end = selection.max.coerceIn(0, text.length)
        val prefix = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
        val insert = prefix + snippet
        val newText = text.substring(0, start) + insert + text.substring(end)
        val newCursor = start + insert.length
        applyEdit(TextFieldValue(text = newText, selection = TextRange(newCursor)))
    }

    fun closeToolPanels() {
        if (kaomojiMenuExpanded) kaomojiMenuExpanded = false
    }

    fun openDoodlePad() {
        hideSystemIme()
        closeToolPanels()
        editorState.showDoodlePad.value = true
    }

    fun minimize() {
        hideSystemIme()
        closeToolPanels()
        onClose()
    }

    BackHandler(enabled = fullScreen && !kaomojiMenuExpanded) { minimize() }
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
                composition = null,
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

    val scope = rememberCoroutineScope()
    val preparingAttachment by editorState.preparingAttachment
    val canSend = (replyText.isNotBlank() || attachedImageUri != null) && !isReplying && !preparingAttachment
    fun submit(cookie: String?, index: Int? = null) {
        if (preparingAttachment || isReplying) return
        val uri = attachedImageUri
        if (uri == null) {
            index?.let(onCookieSelect)
            onSend(null, cookie)
            return
        }
        editorState.preparingAttachment.value = true
        scope.launch {
            try {
                val file = compressImageUnder2MB(context, uri)
                index?.let(onCookieSelect)
                onSend(file, cookie)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, "图片处理失败", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                editorState.preparingAttachment.value = false
            }
        }
    }

    fun send() {
        if (confirmCookieBeforeSend) {
            hideSystemIme()
            closeToolPanels()
            showCookieConfirmation = true
        } else {
            submit(null)
        }
    }

    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val paper = if (dark) Color(0xFF16181E) else Color.White
    val page = if (dark) Color(0xFF0E1015) else Color(0xFFF0F1F4)

    // 实心底座贴屏幕物理底边，由内容下方的共享槽位把控件顶起来：
    // 无键盘无面板时槽位 = 导航条高度（与现在贴导航条一致）；
    // 键盘/面板谁高用谁，背景始终不透明地铺到屏幕底。
    Surface(
        color = if (fullScreen) page else readingComposerColor(),
        tonalElevation = 0.dp,
        modifier = if (fullScreen) Modifier.fillMaxSize().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null, onClick = {}
        ) else Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (fullScreen) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { minimize() }) { Icon(Icons.Default.Close, "收起写作台") }
                    Column(Modifier.weight(1f)) {
                        Text("回复串", fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("No.$threadId", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box {
                        IconButton(onClick = { hideSystemIme(); closeToolPanels(); showDrafts = true },
                            enabled = !isReplying) {
                            Icon(Icons.Outlined.Description, "选择草稿")
                        }
                        if (drafts.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(9.dp)
                            .size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    }
                    IconButton(onClick = onSaveDraft, enabled = !isReplying &&
                        (replyText.isNotBlank() || title.isNotBlank() || name.isNotBlank())) {
                        Icon(Icons.Outlined.Save, "保存草稿")
                    }
                    Button(onClick = { send() }, enabled = canSend, shape = CircleShape) {
                        Text("发送", maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        if (isReplying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Send, null, Modifier.size(16.dp))
                    }
                }
            }
            Surface(
                modifier = if (fullScreen) Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                    else Modifier.fillMaxWidth(),
                color = if (fullScreen) paper else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (fullScreen) 0.dp else 12.dp, vertical = 8.dp)) {
                if (fullScreen) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(title, { onIdentityChange(it, name) }, Modifier.weight(1f),
                            enabled = !isReplying, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { field ->
                                Box(Modifier.padding(vertical = 8.dp)) {
                                    if (title.isEmpty()) Text("标题（选填）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    field()
                                }
                            })
                        Surface(onClick = { showName = !showName }, shape = CircleShape,
                            color = if (dark) Color(0xFF22252D) else Color(0xFFF2F4F8),
                            modifier = Modifier.widthIn(max = 140.dp)) {
                            Text(name.ifBlank { "无名氏" }, Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AnimatedVisibility(showName) {
                        BasicTextField(name, { onIdentityChange(title, it) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            enabled = !isReplying, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { field ->
                                Box {
                                    if (name.isEmpty()) Text("名称（选填）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    field()
                                }
                            })
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                val reducedMotion = isReducedMotionEnabled()
                AnimatedVisibility(
                    visible = quotedPostNo != null,
                    modifier = if (fullScreen) Modifier.padding(horizontal = 12.dp) else Modifier,
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


                AnimatedVisibility(
                    visible = attachedImageUri != null,
                    modifier = if (fullScreen) Modifier.padding(horizontal = 12.dp) else Modifier,
                    enter = MiooMotion.softExpandEnter(reducedMotion),
                    exit = MiooMotion.softExpandExit(reducedMotion)
                ) {
                    attachedImageUri?.let { uri ->
                        com.mioo.dao.ui.components.LocalImagePreview(uri,
                            modifier = Modifier.padding(vertical = 6.dp),
                            attachment = true, attachmentBackground = if (fullScreen) paper else readingComposerColor(),
                            onClick = ::openDoodlePad,
                            onRemove = { attachedImageUri = null })
                    }
                }

                if (fullScreen) {
                    BasicTextField(textFieldValue, { applyEdit(it) },
                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)
                            .onFocusChanged { if (it.isFocused) closeToolPanels() },
                        enabled = !isReplying,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp,
                            color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { field ->
                            Box(Modifier.fillMaxSize()) {
                                if (textFieldValue.text.isEmpty()) Text("正文内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                field()
                            }
                        })
                    LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        item {
                            IconButton({ restoreEdit(true) }, Modifier.size(40.dp), enabled = !isReplying && editorState.undo.isNotEmpty()) {
                                Icon(Icons.Filled.Undo, "撤销", Modifier.size(18.dp))
                            }
                        }
                        item {
                            IconButton({ restoreEdit(false) }, Modifier.size(40.dp), enabled = !isReplying && editorState.redo.isNotEmpty()) {
                                Icon(Icons.Filled.Redo, "重做", Modifier.size(18.dp))
                            }
                        }
                        items(listOf(
                            Triple("「」", "「", "」"), Triple("『』", "『", "』"), Triple("【】", "【", "】"),
                            Triple("——", "——", ""), Triple("……", "……", ""), Triple("防剧透", "[h]", "[/h]")
                        )) { (label, open, close) ->
                            Surface(onClick = { insertPair(open, close) }, enabled = !isReplying,
                                shape = CircleShape, color = if (dark) Color(0xFF22252D) else Color(0xFFF2F4F8),
                                modifier = Modifier.height(30.dp)) {
                                Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                } else OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { applyEdit(it) },
                    enabled = !isReplying,
                    trailingIcon = {
                        IconButton(onClick = { hideSystemIme(); closeToolPanels(); onOpen() }) {
                            Icon(Icons.Outlined.OpenInFull, "全屏创作", Modifier.size(18.dp))
                        }
                    },
                    placeholder = { Text("回复...", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
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

            }
            }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
                        onDoodleClick = ::openDoodlePad,
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
                            imagePickerLauncher.launch("image/*")
                        }
                    )

                    if (!fullScreen) {
                    Spacer(modifier = Modifier.width(4.dp))

                    // Send Button
                    val isSendActive = canSend
                    val sendIconColor = if (isSendActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

                    IconButton(
                        onClick = { send() },
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
            // 实心背景铺底直达屏幕底，面板内容 navigationBarsPadding 防撞小白条。
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

    if (showDrafts) {
        ModalBottomSheet(onDismissRequest = { showDrafts = false }, containerColor = paper) {
            Text("选择回复草稿", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium)
            if (drafts.isEmpty()) {
                Text("当前串暂无回复草稿", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(drafts, key = { it.id }) { draft ->
                        PostDraftCard(draft, "No.${draft.targetId}") {
                            showDrafts = false
                            editorState.clearHistory()
                            textFieldValue = TextFieldValue(draft.content, TextRange(draft.content.length))
                            onSelectDraft(draft)
                        }
                    }
                }
            }
        }
    }

    if (showDiceDialog) {
        DiceDialog(
            onDismiss = { showDiceDialog = false },
            onInsert = { expr ->
                insertAtCursor(expr)
                showDiceDialog = false
            }
        )
    }

    if (showCookieConfirmation) {
        ConfirmSendCookieDialog(cookies = cookies, notes = cookieNotes, selectedIndex = selectedCookieIndex,
            onDismiss = { showCookieConfirmation = false }, onConfirm = { index, cookie ->
                showCookieConfirmation = false
                submit(cookie, index)
            })
    }

}
