package com.mioo.dao.ui.screens.thread

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.rememberAsyncImagePainter
import com.mioo.dao.ui.components.toFile
import com.mioo.dao.ui.components.KAOMOJI_LIST
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.mioo.dao.ui.components.FreeCopyDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import com.mioo.dao.data.model.Reply
import com.mioo.dao.data.model.Thread
import com.mioo.dao.ui.components.PostData
import com.mioo.dao.ui.components.ReplyCard
import com.mioo.dao.ui.components.ThreadCard
import com.mioo.dao.ui.components.ImageViewer
import com.mioo.dao.ui.components.RefPopup
import com.mioo.dao.ui.components.decodeHtmlEntities
import com.mioo.dao.ui.screens.settings.SettingsViewModel
import com.mioo.dao.ui.theme.DaoTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToThread: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.settingsState.collectAsState()
    var activeImageUrl by remember { mutableStateOf<String?>(null) }
    var showBookmarkMenu by remember { mutableStateOf(false) }
    var freeCopyText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(settingsState.blockedThreads) {
        if (settingsState.blockedThreads.contains(viewModel.threadId)) {
            Toast.makeText(context, "该帖子已被屏蔽", Toast.LENGTH_SHORT).show()
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.thread?.title ?: "详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Subscription button
                    Box {
                        IconButton(onClick = {
                            if (uiState.isSubscribed || uiState.feedFolders.isEmpty()) {
                                // If already subscribed (locally) or no extra folders, just toggle local
                                viewModel.toggleBookmark(null) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // If not subscribed and has extra folders, show menu
                                showBookmarkMenu = true
                            }
                        }) {
                            Icon(
                                imageVector = if (uiState.isSubscribed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (uiState.isSubscribed) "取消收藏" else "收藏",
                                tint = if (uiState.isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        androidx.compose.material3.DropdownMenu(
                            expanded = showBookmarkMenu,
                            onDismissRequest = { showBookmarkMenu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("本地收藏") },
                                onClick = {
                                    showBookmarkMenu = false
                                    viewModel.toggleBookmark(null) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            uiState.feedFolders.forEach { folder ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(folder.name) },
                                    onClick = {
                                        showBookmarkMenu = false
                                        viewModel.toggleBookmark(folder.uuid) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // OP Only toggle button
                    IconButton(onClick = { viewModel.togglePoOnly() }) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = "只看楼主",
                            tint = if (uiState.showPoOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // Download Thread button
                    if (uiState.isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            Toast.makeText(context, "正在下载该串至本地...", Toast.LENGTH_SHORT).show()
                            viewModel.downloadThread { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "下载本串"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaoTheme.colors.glassTopBar
                )
            )
        },
        bottomBar = {
            val context = androidx.compose.ui.platform.LocalContext.current
            ReplyInputArea(
                replyText = uiState.replyText,
                quotedPostNo = uiState.quotedPostNo,
                cookies = settingsState.cookiesList,
                selectedCookieIndex = settingsState.selectedCookieIndex,
                isReplying = uiState.isReplying,
                replyError = uiState.replyError,
                onCookieSelect = { settingsViewModel.selectCookie(it) },
                onReplyTextChange = { viewModel.updateReplyText(it) },
                onCancelQuote = { viewModel.setQuotedPost(null) },
                onSend = { file -> 
                    val currentCookie = settingsState.cookiesList.getOrNull(settingsState.selectedCookieIndex) ?: "无名氏"
                    // Extract name from cookie json if possible
                    var authorName = "无名氏"
                    try {
                        if (currentCookie.startsWith("{")) {
                            val json = org.json.JSONObject(currentCookie)
                            authorName = json.optString("name", "无名氏")
                        }
                    } catch (e: Exception) {}
                    viewModel.submitReply(author = authorName, imageFile = file)
                }
            )
        },
        modifier = modifier.imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "发生错误",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                val filteredReplies = remember(uiState.posts, settingsState.blockedUsers, settingsState.blockedKeywords) {
                    uiState.posts.filter { reply ->
                        !settingsState.blockedUsers.contains(reply.userHash) &&
                        settingsState.blockedKeywords.none { keyword ->
                            reply.content?.contains(keyword, ignoreCase = true) == true
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp
                    )
                ) {
                    // Render Main Thread
                    uiState.thread?.let { mainThread ->
                        item {
                            var showBlockDialog by remember { mutableStateOf(false) }

                            if (showBlockDialog) {
                                AlertDialog(
                                    onDismissRequest = { showBlockDialog = false },
                                    title = { Text("内容操作 No.${mainThread.idStr}") },
                                    text = {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.setQuotedPost(mainThread.idStr)
                                                    Toast.makeText(context, "已引用 No.${mainThread.idStr}", Toast.LENGTH_SHORT).show()
                                                    showBlockDialog = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("引用该帖子")
                                            }
                                            TextButton(
                                                onClick = {
                                                    settingsViewModel.addBlockedThread(mainThread.idStr)
                                                    showBlockDialog = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("屏蔽此串 (No.${mainThread.idStr})")
                                            }
                                            TextButton(
                                                onClick = {
                                                    settingsViewModel.addBlockedUser(mainThread.userHash)
                                                    showBlockDialog = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("屏蔽该发言饼干 (ID: ${mainThread.userHash})")
                                            }
                                            TextButton(
                                                onClick = {
                                                    freeCopyText = mainThread.content
                                                    showBlockDialog = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("自由复制帖子内容")
                                            }
                                            TextButton(
                                                onClick = { showBlockDialog = false },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("取消")
                                            }
                                        }
                                    },
                                    confirmButton = {}
                                )
                            }

                            val mainThreadPostData = remember(mainThread) { mainThread.toPostData() }
                            val onQuoteClickRemembered = remember { { quoteNo: String -> viewModel.showRefPopup(quoteNo) } }
                            val onImageClickRemembered = remember { { url: String -> activeImageUrl = url } }
                            val onLongClickRemembered = remember { { showBlockDialog = true } }
                            ThreadCard(
                                postData = mainThreadPostData,
                                replyCount = mainThread.replyCount ?: 0,
                                onThreadClick = {},
                                onQuoteClick = onQuoteClickRemembered,
                                onImageClick = onImageClickRemembered,
                                onLongClick = onLongClickRemembered
                            )
                        }
                    }


                    items(
                        items = filteredReplies,
                        key = { it.id },
                        contentType = { "reply_card" }
                    ) { reply ->
                        val quoteIds = remember(reply.content) {
                            val decoded = reply.content.decodeHtmlEntities()
                            val matcher = java.util.regex.Pattern.compile(">>(?:No\\.)?(\\d+)").matcher(decoded)
                            val list = mutableListOf<String>()
                            while (matcher.find()) {
                                matcher.group(1)?.let { list.add(it) }
                            }
                            list
                        }
                        
                        val quotedPostsData = remember(quoteIds, uiState.quoteCache, uiState.thread?.userHash) {
                            quoteIds.mapNotNull { id ->
                                uiState.quoteCache[id]?.let {
                                    val isFollowUp = it.resto != null && it.resto > 0L
                                    PostData(
                                        id = it.idStr,
                                        title = it.title ?: "",
                                        userName = it.name ?: "无名氏",
                                        userId = it.userHash,
                                        createdAt = it.now,
                                        content = it.content,
                                        imageUrl = if (it.imageUrl != null) "https://image.nmb.best/image/${it.imageUrl}" else null,
                                        isPo = it.userHash == uiState.thread?.userHash,
                                        isAdmin = it.isAdmin,
                                        isSage = it.isSage,
                                        resto = if (isFollowUp) it.resto.toString() else it.idStr
                                    )
                                }
                            }
                        }

                        var showReplyBlockDialog by remember { mutableStateOf(false) }

                        if (showReplyBlockDialog) {
                            AlertDialog(
                                onDismissRequest = { showReplyBlockDialog = false },
                                title = { Text("内容操作 No.${reply.idStr}") },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                viewModel.setQuotedPost(reply.idStr)
                                                Toast.makeText(context, "已引用 No.${reply.idStr}", Toast.LENGTH_SHORT).show()
                                                showReplyBlockDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("引用该帖子")
                                        }
                                        TextButton(
                                            onClick = {
                                                settingsViewModel.addBlockedThread(viewModel.threadId)
                                                showReplyBlockDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("屏蔽此串 (No.${viewModel.threadId})")
                                        }
                                        TextButton(
                                            onClick = {
                                                settingsViewModel.addBlockedUser(reply.userHash)
                                                showReplyBlockDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("屏蔽该发言饼干 (ID: ${reply.userHash})")
                                        }
                                        TextButton(
                                            onClick = {
                                                freeCopyText = reply.content
                                                showReplyBlockDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("自由复制回复内容")
                                        }
                                        TextButton(
                                            onClick = { showReplyBlockDialog = false },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("取消")
                                        }
                                    }
                                },
                                confirmButton = {}
                            )
                        }

                        val isPo = reply.userHash == uiState.thread?.userHash
                        val postData = remember(reply, isPo) { reply.toPostData(isPo) }
                        val onQuoteClickRemembered = remember(reply) { { quoteNo: String -> viewModel.showRefPopup(quoteNo) } }
                        val onImageClickRemembered = remember { { url: String -> activeImageUrl = url } }
                        val onCardLongClickRemembered = remember { { showReplyBlockDialog = true } }
                        val onViewThreadClickRemembered = remember { { threadId: String -> onNavigateToThread(threadId) } }

                        ReplyCard(
                            postData = postData,
                            onQuoteClick = onQuoteClickRemembered,
                            onImageClick = onImageClickRemembered,
                            onCardClick = {},
                            onCardLongClick = onCardLongClickRemembered,
                            quotedPosts = quotedPostsData,
                            onViewThreadClick = onViewThreadClickRemembered,
                            currentThreadId = viewModel.threadId
                        )
                    }
                }
            }

            freeCopyText?.let { text ->
                FreeCopyDialog(
                    text = text,
                    onDismiss = { freeCopyText = null }
                )
            }

            // Image Viewer Overlay
            activeImageUrl?.let { imageUrl ->
                ImageViewer(
                    imageUrl = imageUrl,
                    onDismiss = { activeImageUrl = null }
                )
            }

            // Ref Popup Dialog Overlay
            uiState.refPostId?.let { refId ->
                RefPopup(
                    postId = refId,
                    postData = uiState.refPostData,
                    isLoading = uiState.isRefLoading,
                    errorMessage = uiState.refError,
                    onDismiss = { viewModel.dismissRefPopup() },
                    onQuoteClick = { quoteNo -> viewModel.showRefPopup(quoteNo) },
                    onImageClick = { url -> activeImageUrl = url },
                    onViewThreadClick = { threadId ->
                        viewModel.dismissRefPopup()
                        onNavigateToThread(threadId)
                    },
                    currentThreadId = viewModel.threadId
                )
            }
        }
    }
}

fun Thread.toPostData(cdnUrl: String = "https://image.nmb.best"): PostData {
    return PostData(
        id = this.idStr,
        title = this.title ?: "",
        userName = this.name ?: "Anonymous",
        userId = this.userHash,
        createdAt = this.now,
        content = this.content,
        imageUrl = if (this.imageUrl != null) "$cdnUrl/image/${this.imageUrl}" else null,
        isPo = false,
        isAdmin = this.isAdmin,
        isSage = this.isSage,
        resto = this.idStr
    )
}

fun Reply.toPostData(isPo: Boolean, cdnUrl: String = "https://image.nmb.best"): PostData {
    return PostData(
        id = this.idStr,
        title = this.title ?: "",
        userName = this.name ?: "Anonymous",
        userId = this.userHash,
        createdAt = this.now,
        content = this.content,
        imageUrl = if (this.imageUrl != null) "$cdnUrl/image/${this.imageUrl}" else null,
        isPo = isPo,
        isAdmin = this.isAdmin,
        isSage = this.isSage,
        resto = this.resto?.toString() ?: this.idStr
    )
}

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
    onSend: (java.io.File?) -> Unit
) {
    var cookieMenuExpanded by remember { mutableStateOf(false) }
    var kaomojiMenuExpanded by remember { mutableStateOf(false) }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var wasReplying by remember { mutableStateOf(false) }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = replyText,
                selection = TextRange(replyText.length)
            )
        )
    }

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
        }
        wasReplying = isReplying
    }

    LaunchedEffect(replyText) {
        if (replyText.isEmpty()) {
            attachedImageUri = null
        }
    }

    // Show error toast when replyError changes
    androidx.compose.runtime.LaunchedEffect(replyError) {
        if (replyError != null) {
            android.widget.Toast.makeText(context, "回复失败: $replyError", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Surface(
        color = DaoTheme.colors.glassNavBar,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AnimatedVisibility(visible = quotedPostNo != null) {
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
                        text = "正在回复引用: >>No.$quotedPostNo",
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
            AnimatedVisibility(visible = attachedImageUri != null) {
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
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { attachedImageUri = null },
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.6f), shape = androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
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
                        if (focusState.isFocused) {
                            kaomojiMenuExpanded = false
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
                val imagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    attachedImageUri = uri
                }

                // Cookie selector
                val currentCookie = cookies.getOrNull(selectedCookieIndex) ?: "选择饼干"
                var displayName = currentCookie
                try {
                    if (currentCookie.startsWith("{")) {
                        val json = org.json.JSONObject(currentCookie)
                        displayName = json.optString("name", "")
                        if (displayName.isEmpty()) displayName = json.optString("cookie", "")
                        if (displayName.isEmpty()) displayName = json.optString("userhash", currentCookie)
                    }
                } catch (e: Exception) {}
                val displayText = if (displayName.length > 8) displayName.take(8) + "..." else displayName

                Box {
                    OutlinedButton(
                        onClick = { cookieMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(displayText, style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(
                        expanded = cookieMenuExpanded,
                        onDismissRequest = { cookieMenuExpanded = false }
                    ) {
                        cookies.forEachIndexed { index, cookie ->
                            val isSelected = index == selectedCookieIndex
                            var itemDisplayName = cookie
                            try {
                                if (cookie.startsWith("{")) {
                                    val json = org.json.JSONObject(cookie)
                                    itemDisplayName = json.optString("name", "")
                                    if (itemDisplayName.isEmpty()) itemDisplayName = json.optString("cookie", "")
                                    if (itemDisplayName.isEmpty()) itemDisplayName = json.optString("userhash", cookie)
                                }
                            } catch (e: Exception) {}
                            val itemDisplayText = if (itemDisplayName.length > 8) itemDisplayName.take(8) + "..." else itemDisplayName
                            
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = itemDisplayText,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                onClick = {
                                    onCookieSelect(index)
                                    cookieMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Smiley face (Face)
                IconButton(onClick = { 
                    kaomojiMenuExpanded = !kaomojiMenuExpanded 
                    if (kaomojiMenuExpanded) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "选择颜文字",
                        tint = if (kaomojiMenuExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Attach Image
                IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "添加图片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button
                val isSendActive = (replyText.isNotBlank() || attachedImageUri != null) && !isReplying
                val sendIconColor = if (isSendActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                
                IconButton(
                    onClick = { 
                        val file = attachedImageUri?.toFile(context)
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

            // 3. Kaomoji Panel Row
            AnimatedVisibility(visible = kaomojiMenuExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(180.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val rows = KAOMOJI_LIST.chunked(3)
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { kaomoji ->
                                TextButton(
                                    onClick = {
                                        val text = textFieldValue.text
                                        val selection = textFieldValue.selection
                                        val start = selection.start
                                        val end = selection.end
                                        val newText = text.substring(0, start) + kaomoji + text.substring(end)
                                        val newCursorPos = start + kaomoji.length
                                        
                                        textFieldValue = TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newCursorPos)
                                        )
                                        onReplyTextChange(newText)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = kaomoji,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            // Fill empty spaces if row has < 3 elements
                            if (row.size < 3) {
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
