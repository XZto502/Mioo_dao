package com.mioo.dao.ui.screens.forum

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.mioo.dao.data.local.DraftEntity
import com.mioo.dao.data.local.PostDraftContent
import com.mioo.dao.ui.components.ComposerToolButtons
import com.mioo.dao.ui.components.CookieSelector
import com.mioo.dao.ui.components.ConfirmSendCookieDialog
import com.mioo.dao.ui.components.DiceDialog
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.KaomojiQuickPanel
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.findActivity
import com.mioo.dao.ui.theme.immersiveDialogProperties

/** Page-root overlay: one Activity window owns editing, keyboard and system insets. */
@Composable
fun CreateThreadEditor(
    cookies: List<String>,
    selectedCookieIndex: Int,
    confirmCookieBeforeSend: Boolean,
    cookieNotes: Map<String, String>,
    boardName: String,
    initialContent: String = "",
    initialTitle: String = "",
    initialName: String = "",
    drafts: List<DraftEntity> = emptyList(),
    onSelectDraft: (DraftEntity) -> Unit,
    onSaveDraft: ((String, String, String, (Boolean) -> Unit) -> Unit)?,
    onCookieSelect: (Int) -> Unit,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, Uri?, String?) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = isReducedMotionEnabled()
    val scope = rememberCoroutineScope()
    // Follow the app theme, including an explicit light/dark override in settings.
    val isDark = scheme.background.luminance() < 0.5f
    val pageBg = if (isDark) Color(0xFF0E1015) else Color(0xFFF0F2F6)
    val paperColor = if (isDark) Color(0xFF16181E) else Color.White
    val capsuleColor = if (isDark) Color(0xFF181A20) else Color.White
    val context = LocalContext.current
    val view = LocalView.current
    val window = context.findActivity()?.window
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    DisposableEffect(window, view, isDark) {
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        val oldSoftInput = window?.attributes?.softInputMode
        controller?.isAppearanceLightStatusBars = !isDark
        controller?.isAppearanceLightNavigationBars = !isDark
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            if (oldStatus != null) controller?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller?.isAppearanceLightNavigationBars = oldNavigation
            if (oldSoftInput != null) window?.setSoftInputMode(oldSoftInput)
        }
    }

    var title by remember { mutableStateOf(initialTitle) }
    var name by remember { mutableStateOf(initialName) }
    var body by remember { mutableStateOf(TextFieldValue(initialContent, TextRange(initialContent.length))) }
    var savedFields by remember { mutableStateOf(PostDraftContent(initialTitle, initialName, initialContent)) }
    var image by remember { mutableStateOf<Uri?>(null) }
    var previewImage by remember { mutableStateOf<Uri?>(null) }
    var nameExpanded by remember { mutableStateOf(false) }
    var cookiesExpanded by remember { mutableStateOf(false) }
    var showCookieConfirmation by remember { mutableStateOf(false) }
    var kaomojiExpanded by remember { mutableStateOf(false) }
    var diceOpen by remember { mutableStateOf(false) }
    var draftsOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var draftToOpen by remember { mutableStateOf<DraftEntity?>(null) }
    var saving by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val fields = PostDraftContent(title, name, body.text)
    val dirty = fields != savedFields || image != null
    val busy = saving || isSubmitting
    val canSend = !busy && (body.text.isNotBlank() || image != null)
    val sendColor by animateColorAsState(if (canSend) scheme.primary else scheme.surfaceVariant, label = "postSendColor")
    val sendTextColor by animateColorAsState(if (canSend) scheme.onPrimary else scheme.onSurfaceVariant.copy(alpha = 0.45f), label = "postSendTextColor")
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image = it }

    LaunchedEffect(nameExpanded) {
        if (nameExpanded) {
            nameFocus.requestFocus()
            keyboard?.show()
        }
    }

    fun finishAction() {
        confirmLeave = false
        keyboard?.hide()
        focus.clearFocus(force = true)
        val selected = draftToOpen
        draftToOpen = null
        if (selected == null) onDismiss() else onSelectDraft(selected)
    }
    fun save(leave: Boolean) {
        val saveDraft = onSaveDraft ?: return
        if (busy || !fields.hasContent) return
        val snapshot = fields
        saving = true
        saveDraft(snapshot.title, snapshot.name, snapshot.content) { success ->
            saving = false
            if (success) {
                savedFields = snapshot
                if (leave) finishAction()
            }
        }
    }
    fun requestClose() {
        if (busy) return
        when {
            draftsOpen -> draftsOpen = false
            kaomojiExpanded -> kaomojiExpanded = false
            dirty -> confirmLeave = true
            else -> onDismiss()
        }
    }
    fun insert(text: String) {
        val start = body.selection.min.coerceIn(0, body.text.length)
        val end = body.selection.max.coerceIn(start, body.text.length)
        val prefix = if (start > 0 && !body.text[start - 1].isWhitespace()) " " else ""
        val inserted = prefix + text
        body = TextFieldValue(body.text.replaceRange(start, end, inserted), TextRange(start + inserted.length))
    }
    BackHandler(enabled = !confirmLeave && !diceOpen && previewImage == null) { requestClose() }

    // Background precedes all insets: both system-bar regions stay completely opaque.
    CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
    Box(
        Modifier.fillMaxSize().zIndex(100f).background(pageBg).imePadding()
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { requestClose() }, enabled = !busy, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
                Column(Modifier.weight(1f).padding(start = 2.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("发表新串", fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (boardName.isNotBlank()) Surface(shape = RoundedCornerShape(6.dp), color = scheme.primary.copy(alpha = 0.12f)) {
                        Text(boardName, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = scheme.primary,
                            fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = {
                    keyboard?.hide()
                    focus.clearFocus(force = true)
                    kaomojiExpanded = false
                    draftsOpen = true
                }, enabled = !busy, modifier = Modifier.size(40.dp)) {
                    Box {
                        Icon(Icons.Outlined.Description, contentDescription = "选择草稿")
                        if (drafts.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).size(6.dp).clip(CircleShape).background(scheme.error))
                    }
                }
                if (onSaveDraft != null) IconButton(onClick = { save(false) }, enabled = fields.hasContent && !busy, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Save, contentDescription = "保存草稿")
                }
                Button(
                    onClick = {
                        keyboard?.hide()
                        focus.clearFocus(force = true)
                        if (confirmCookieBeforeSend) showCookieConfirmation = true
                        else onSubmit(title, name.ifBlank { "无名氏" }, body.text, image, null)
                    },
                    enabled = canSend, shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = sendColor, contentColor = sendTextColor,
                        disabledContainerColor = sendColor, disabledContentColor = sendTextColor)
                ) {
                    if (isSubmitting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSubmitting) "发送中" else "发送", fontSize = 13.sp)
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp), color = paperColor
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicTextField(
                            value = title, onValueChange = { title = it }, singleLine = true,
                            modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) kaomojiExpanded = false },
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            decorationBox = { input -> Box {
                                if (title.isEmpty()) Text("标题（选填）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant.copy(alpha = 0.45f))
                                input()
                            } }
                        )
                        Surface(
                            onClick = {
                                if (nameExpanded) { focus.clearFocus(); keyboard?.hide() }
                                nameExpanded = !nameExpanded
                            }, shape = CircleShape,
                            color = scheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.widthIn(max = 140.dp)
                        ) {
                            Text("署名: ${name.ifBlank { "无名氏" }}", Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    AnimatedVisibility(nameExpanded) {
                        BasicTextField(
                            value = name, onValueChange = { name = it }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                .focusRequester(nameFocus).onFocusChanged { if (it.isFocused) kaomojiExpanded = false },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface), cursorBrush = SolidColor(scheme.primary),
                            decorationBox = { input -> Box {
                                if (name.isEmpty()) Text("名称（选填，默认无名氏）", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant.copy(alpha = 0.45f))
                                input()
                            } }
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = scheme.outlineVariant.copy(alpha = 0.3f))
                    BasicTextField(
                        value = body, onValueChange = { body = it },
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp).onFocusChanged { if (it.isFocused) kaomojiExpanded = false },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 24.sp, color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        decorationBox = { input -> Box(Modifier.fillMaxSize()) {
                            if (body.text.isEmpty()) Text("正文内容…", fontSize = 15.5.sp, lineHeight = 24.sp, color = scheme.onSurfaceVariant.copy(alpha = 0.42f))
                            input()
                        } }
                    )
                    image?.let { uri ->
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).size(80.dp).clip(RoundedCornerShape(12.dp))) {
                            AsyncImage(uri, contentDescription = "附图预览", contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clickable { previewImage = uri })
                            IconButton(onClick = { image = null }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)) {
                                Icon(Icons.Default.Close, contentDescription = "移除图片", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(kaomojiExpanded) {
                KaomojiQuickPanel(onInsert = { insert(it) }, heightDp = 220, modifier = Modifier.fillMaxWidth())
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (cookies.isNotEmpty()) CookieSelector(cookies, selectedCookieIndex, cookiesExpanded,
                    { cookiesExpanded = it }, onCookieSelect, containerColor = capsuleColor)
                Spacer(Modifier.weight(1f))
                ComposerToolButtons(
                    diceSelected = diceOpen, kaomojiSelected = kaomojiExpanded, hasImage = image != null,
                    onDiceClick = { diceOpen = true },
                    onKaomojiClick = {
                        keyboard?.hide()
                        focus.clearFocus(force = true)
                        kaomojiExpanded = !kaomojiExpanded
                    },
                    onImageClick = { kaomojiExpanded = false; imagePicker.launch("image/*") },
                    containerColor = capsuleColor
                )
            }
        }
        AnimatedVisibility(visible = draftsOpen,
            enter = fadeIn(tween(if (reducedMotion) 0 else 200)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 180))) {
            PostDraftPicker(drafts, reducedMotion = reducedMotion, onDismiss = { draftsOpen = false }, onSelect = { draft ->
                draftsOpen = false
                scope.launch {
                    if (!reducedMotion) delay(180)
                    if (dirty) { draftToOpen = draft; confirmLeave = true } else onSelectDraft(draft)
                }
            })
        }
    }
    }
    if (showCookieConfirmation) {
        ConfirmSendCookieDialog(cookies = cookies, notes = cookieNotes, selectedIndex = selectedCookieIndex,
            onDismiss = { showCookieConfirmation = false }, onConfirm = { index, cookie ->
                showCookieConfirmation = false
                onCookieSelect(index)
                onSubmit(title, name.ifBlank { "无名氏" }, body.text, image, cookie)
            })
    }
    if (diceOpen) DiceDialog(onDismiss = { diceOpen = false }, onInsert = { insert(it); diceOpen = false })
    previewImage?.let { uri -> ImageViewer(imageUrl = uri.toString(), onDismiss = { previewImage = null }) }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { if (!saving) { confirmLeave = false; draftToOpen = null } },
        properties = immersiveDialogProperties(),
        icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
        title = { Text(if (onSaveDraft != null) "是否保存草稿？" else "放弃本次编辑？") },
        text = {
            ImmersiveDialogEffect(lightAppearance = !isDark)
            Text(buildString {
                append(if (onSaveDraft != null) "保存后可从草稿箱继续编辑。" else "草稿箱已关闭。")
                append("放弃将清空本次修改，已保存的草稿不受影响。")
                if (image != null) append("\n图片不会存入草稿，继续编辑时需重新选择。")
            })
        },
        confirmButton = { if (onSaveDraft != null) FilledTonalButton(onClick = { save(true) }, enabled = fields.hasContent && !saving) { Text(if (saving) "保存中…" else "保存草稿") } },
        dismissButton = {
            FilledTonalButton(onClick = { finishAction() }, enabled = !saving,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.surfaceVariant, contentColor = scheme.onSurfaceVariant)) { Text("放弃") }
        }
    )
}
