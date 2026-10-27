package com.mioo.dao.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.findDialogWindow
import com.mioo.dao.ui.theme.immersiveDialogProperties
import com.mioo.dao.utils.QrDetector
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.size.Precision
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** 看图器条目：图片地址 + 所属楼层（九宫格标注与「定位楼层」用；无楼层信息可只给 url）。 */
@Immutable
data class ViewerImage(
    val url: String,
    val postId: String? = null,
    val snippet: String? = null
)

/**
 * 单张图的几何变换状态。[cumulativeRotation] 连续累加（270°→360° 而非倒转回 0），
 * 对外展示用 [normalizedAngle] 归一化到 0/90/180/270。
 */
data class ViewerTransform(
    val cumulativeRotation: Float = 0f,
    val flippedH: Boolean = false,
    val flippedV: Boolean = false
) {
    val normalizedAngle: Int
        get() = (((cumulativeRotation.roundToInt() % 360) + 360) % 360)

    val isTransformed: Boolean
        get() = normalizedAngle != 0 || flippedH || flippedV
}

/** 识图源。 */
private val SAUCE_PROVIDERS = listOf(
    "SauceNAO" to "https://saucenao.com/search.php?url=",
    "Ascii2d" to "https://ascii2d.net/search/url/",
    "Yandex" to "https://yandex.com/images/search?rpt=imageview&url="
)

/** 纵横比超过该阈值按条漫长图处理（可一键进入撑满屏宽的纵向滚动阅读）。 */
private const val STRIP_RATIO_THRESHOLD = 2.2f

/** 看图器深色底/胶囊底色（与顶栏胶囊菜单同源的炭黑）。 */
private val ViewerSurfaceColor = Color(0xF217191E)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(
    imageUrl: String? = null,
    initialImageUrl: String = imageUrl ?: "",
    imageUrls: List<String> = listOf(initialImageUrl),
    onDismiss: () -> Unit,
    /** 楼层元数据（可选）：提供时九宫格显示楼层号、底部显示原楼层并支持「定位楼层」。 */
    imageMeta: List<ViewerImage> = emptyList(),
    /** 关闭看图器并把串列表滚动到指定楼层。 */
    onLocateFloor: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 页面列表不再完全冻结：打开时的索引保持稳定（防 PagerState 滑动中变更页数崩溃），
    // 但串继续加载出的新图在非滑动状态下只追加到尾部，划到底还能继续往后划
    val initialPages = remember {
        val raw: List<ViewerImage> = if (imageMeta.isNotEmpty()) {
            imageMeta
        } else {
            (if (imageUrls.isNotEmpty()) imageUrls else listOf(initialImageUrl))
                .filter { it.isNotBlank() }
                .distinct()
                .map { ViewerImage(url = it) }
        }
        raw.filter { it.url.isNotBlank() }.distinctBy { it.url }
    }
    var pages by remember { mutableStateOf(initialPages) }
    val incomingPages = rememberUpdatedState(
        if (imageMeta.isNotEmpty()) {
            imageMeta.filter { it.url.isNotBlank() }.distinctBy { it.url }
        } else {
            (if (imageUrls.isNotEmpty()) imageUrls else listOf(initialImageUrl))
                .filter { it.isNotBlank() }
                .distinct()
                .map { ViewerImage(url = it) }
        }
    )
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val initialPage = remember(initialImageUrl) {
        val idx = pages.indexOfFirst { it.url == initialImageUrl }
        if (idx == -1) 0 else idx
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    // 串继续加载出的新图：仅在非滑动状态下追加（滑动中变更页数会让 PagerState 抛异常）
    LaunchedEffect(incomingPages.value, pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        val incoming = incomingPages.value
        val known = pages.mapTo(HashSet(pages.size)) { it.url }
        val additions = incoming.filter { it.url !in known }
        if (additions.isNotEmpty()) pages = pages + additions
    }
    // Boolean only: continuous scale floats must not recompose the pager shell every frame
    var isZoomed by remember { mutableStateOf(false) }
    // 几何变换：切页即重置回初始正视图（规格要求）
    var transform by remember { mutableStateOf(ViewerTransform()) }
    // 条漫长图模式开关
    var stripMode by remember { mutableStateOf(false) }
    // 当前页图片高宽比（ZoomableImageContainer 回传），用于长图入口
    var currentRatio by remember { mutableStateOf(1f) }
    // 二维码静默检测结果：url → 文本（null = 已检无码）
    val qrTexts = remember { mutableStateMapOf<String, String?>() }
    var showTransformPanel by remember { mutableStateOf(false) }
    var showSaucePanel by remember { mutableStateOf(false) }
    var showGridSheet by remember { mutableStateOf(false) }
    var hasTransformOrZoom by remember { mutableStateOf(false) }

    // Reset transient state when page changes. Same value is a no-op, so a normal swipe
    // does not restart the pager's scrollable.
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        transform = ViewerTransform()
        stripMode = false
        showTransformPanel = false
        showSaucePanel = false
        hasTransformOrZoom = false
    }

    val currentUrl = pages.getOrNull(pagerState.currentPage)?.url ?: ""
    val currentMeta = pages.getOrNull(pagerState.currentPage)

    // 静默二维码检测：进图即检（IO 线程），命中才浮现胶囊
    LaunchedEffect(currentUrl) {
        if (currentUrl.isBlank() || qrTexts.containsKey(currentUrl)) return@LaunchedEffect
        val text = withContext(Dispatchers.IO) {
            runCatching { QrDetector.detect(context, currentUrl, maxDim = 1280) }.getOrNull()
        }
        qrTexts[currentUrl] = text
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = immersiveDialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        // Black viewer: light (white) system icons, no opaque nav strip.
        ImmersiveDialogEffect(lightAppearance = false)
        // 部分 ROM 上 Dialog 内容不铺进导航条 inset 区，底部会露出底下的串内容——
        // 断言窗口全屏并把导航区涂成不透明黑（看图器本身纯黑，手势条白点照常显示）。
        // 该监听注册在 ImmersiveDialogEffect 的透明重申之后，每次布局都最后执行、保证黑色生效。
        val viewerView = LocalView.current
        DisposableEffect(viewerView) {
            val window = viewerView.findDialogWindow()
            window?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                viewerView.findDialogWindow()?.navigationBarColor =
                    android.graphics.Color.BLACK
            }
            val decor = window?.decorView
            decor?.viewTreeObserver?.addOnGlobalLayoutListener(listener)
            onDispose {
                decor?.viewTreeObserver?.let { observer ->
                    if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
                }
            }
        }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (stripMode && currentUrl.isNotBlank()) {
                StripReader(
                    imageUrl = currentUrl,
                    onDismiss = onDismiss
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !isZoomed,
                    // Neighbour is composed by the pager prefetcher while the finger moves.
                    // Do not also subscribe to scroll state here: currentPage flips at the
                    // halfway point of a drag and would restart pointerInput under the finger.
                    beyondBoundsPageCount = 0,
                    key = { pages.getOrNull(it)?.url ?: it }
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex)
                    if (page == null) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    } else {
                        ZoomableImageContainer(
                            imageUrl = page.url,
                            transform = transform,
                            onZoomedChanged = { zoomed ->
                                // Read at event time. A value captured when this page was
                                // composed goes stale once the swipe crosses the snap point.
                                if (pagerState.currentPage == pageIndex) {
                                    isZoomed = zoomed
                                    hasTransformOrZoom = zoomed || transform.isTransformed
                                }
                            },
                            onImageRatio = { ratio ->
                                if (pagerState.currentPage == pageIndex) currentRatio = ratio
                            },
                            onTapDismiss = onDismiss
                        )
                    }
                }
            }

            // Top bar overlay: 相册 / 保存 / 关闭
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pages.size > 1) {
                    ViewerCircleButton(
                        icon = Icons.Outlined.GridView,
                        contentDescription = "本串相册",
                        onClick = { showGridSheet = true }
                    )
                }
                if (currentUrl.isNotBlank()) {
                    ViewerCircleButton(
                        icon = if (transform.isTransformed) Icons.Default.DownloadDone else Icons.Default.Download,
                        contentDescription = "保存图片",
                        onClick = {
                            com.mioo.dao.utils.ImageDownloader.downloadImageTransformed(
                                context = context,
                                imageUrl = currentUrl,
                                rotationDegrees = transform.normalizedAngle,
                                flipX = transform.flippedH,
                                flipY = transform.flippedV,
                                scope = scope
                            )
                        }
                    )
                }
                ViewerCircleButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close viewer",
                    onClick = onDismiss
                )
            }

            // Page Indicator overlay (e.g., 2 / 10)
            if (pages.size > 1) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 22.dp)
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${pages.size}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            // 底部：原楼层微文案 + 定位楼层
            if (!stripMode && currentMeta?.postId != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = 84.dp)
                        .fillMaxWidth(0.72f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clickable {
                                currentMeta.postId?.let(onLocateFloor)
                            }
                    ) {
                        Text(
                            text = "No.${currentMeta.postId}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        if (!currentMeta.snippet.isNullOrBlank()) {
                            Text(
                                text = currentMeta.snippet,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "定位楼层 ›",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 二维码胶囊（命中才显示）
            val qrText = qrTexts[currentUrl]
            AnimatedVisibility(
                visible = !qrText.isNullOrBlank() && !stripMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ViewerSurfaceColor,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.25f)
                    ),
                    onClick = {
                        val text = qrText ?: return@Surface
                        QrDetector.openResult(context, text)
                    }
                ) {
                    Text(
                        text = "[识别图中二维码]",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // 底部悬浮工具栏：胶囊底座（变换 / 长图 / 识图 / 分享）
            if (!stripMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    shape = CircleShape,
                    color = Color(0xFF16181D).copy(alpha = 0.92f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            ViewerCircleButton(
                                icon = Icons.Outlined.Rotate90DegreesCcw,
                                contentDescription = "旋转与镜像",
                                containerColor = if (showTransformPanel || transform.isTransformed) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                                tint = if (showTransformPanel || transform.isTransformed) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    Color.White
                                },
                                badge = if (transform.normalizedAngle != 0) "${transform.normalizedAngle}°" else null,
                                onClick = {
                                    showSaucePanel = false
                                    showTransformPanel = !showTransformPanel
                                }
                            )
                        }
                        if (currentRatio > STRIP_RATIO_THRESHOLD) {
                            ViewerCircleButton(
                                icon = Icons.Outlined.UnfoldMore,
                                contentDescription = "条漫长图模式",
                                containerColor = Color.Transparent,
                                onClick = {
                                    showTransformPanel = false
                                    showSaucePanel = false
                                    stripMode = true
                                }
                            )
                        }
                        ViewerCircleButton(
                            icon = Icons.Outlined.ImageSearch,
                            contentDescription = "以图搜源",
                            containerColor = if (showSaucePanel) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                            tint = if (showSaucePanel) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                Color.White
                            },
                            onClick = {
                                showTransformPanel = false
                                showSaucePanel = !showSaucePanel
                            }
                        )
                        ViewerCircleButton(
                            icon = Icons.Outlined.Share,
                            contentDescription = "分享图片",
                            containerColor = Color.Transparent,
                            onClick = {
                                if (currentUrl.isNotBlank()) {
                                    com.mioo.dao.utils.ImageDownloader.shareImageTransformed(
                                        context = context,
                                        imageUrl = currentUrl,
                                        rotationDegrees = transform.normalizedAngle,
                                        flipX = transform.flippedH,
                                        flipY = transform.flippedV,
                                        scope = scope
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // 双排变换面板（第一排角度分段胶囊，第二排镜像/复原）
            AnimatedVisibility(
                visible = showTransformPanel && !stripMode,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 76.dp)
            ) {
                TransformPanel(
                    transform = transform,
                    canReset = transform.isTransformed || hasTransformOrZoom,
                    onRotateTo = { targetAngle ->
                        // 按最短路径旋转到位（±180° 内）
                        val deltaRaw = ((targetAngle - transform.normalizedAngle) + 360) % 360
                        val delta = if (deltaRaw > 180) deltaRaw - 360f else deltaRaw.toFloat()
                        transform = transform.copy(cumulativeRotation = transform.cumulativeRotation + delta)
                    },
                    onFlipH = { transform = transform.copy(flippedH = !transform.flippedH) },
                    onFlipV = { transform = transform.copy(flippedV = !transform.flippedV) },
                    onReset = {
                        transform = ViewerTransform()
                        isZoomed = false
                        hasTransformOrZoom = false
                    }
                )
            }

            // 识图源面板
            AnimatedVisibility(
                visible = showSaucePanel && !stripMode,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 76.dp)
            ) {
                SaucePanel(imageUrl = currentUrl)
            }

            // 本串相册九宫格总览（底部滑出的全屏遮罩面板）
            AnimatedVisibility(
                visible = showGridSheet,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AlbumGridSheet(
                    pages = pages,
                    currentIndex = pagerState.currentPage,
                    onSelect = { index ->
                        showGridSheet = false
                        scope.launch {
                            pagerState.scrollToPage(index)
                        }
                    },
                    onClose = { showGridSheet = false }
                )
            }
        }
    }
}

/** 圆形工具按钮；[containerColor] 为底座色（胶囊底座内默认透明），[badge] 显示当前旋转角度。 */
@Composable
private fun ViewerCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    containerColor: Color = Color.Black.copy(alpha = 0.6f),
    badge: String? = null
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.background(containerColor, shape = CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 双排变换面板：一体式分段胶囊轨（0°/90°/180°/270°）+ 矢量图标镜像/复原按钮。 */
@Composable
private fun TransformPanel(
    transform: ViewerTransform,
    canReset: Boolean,
    onRotateTo: (Int) -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp) // 面板外边距：避免撑满全屏
            .fillMaxWidth()
            .background(ViewerSurfaceColor, RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 第一排：等宽一体式分段胶囊轨（四项 weight 均分，右缘不再留缺口）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            shape = CircleShape,
            color = Color(0xFF101216)
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                listOf(0, 90, 180, 270).forEach { angle ->
                    val selected = transform.normalizedAngle == angle
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .clickable { onRotateTo(angle) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${angle}°",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFeatureSettings = "tnum",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
        // 第二排：矢量图标 + 文字的镜像/复原按钮
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransformActionButton(
                icon = Icons.Default.SwapHoriz,
                label = "水平镜像",
                selected = transform.flippedH,
                onClick = onFlipH,
                modifier = Modifier.weight(1f)
            )
            TransformActionButton(
                icon = Icons.Default.SwapVert,
                label = "垂直镜像",
                selected = transform.flippedV,
                onClick = onFlipV,
                modifier = Modifier.weight(1f)
            )
            TransformActionButton(
                icon = Icons.Default.Refresh,
                iconSize = 15.dp,
                label = "复原",
                selected = false,
                enabled = canReset,
                onClick = onReset,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 变换面板的第二排按钮：选中填 secondaryContainer，未选中深灰底；禁用整钮降透明度。 */
@Composable
private fun TransformActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color(0xFF22252C)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = (if (selected) MaterialTheme.colorScheme.onSecondaryContainer else Color.White)
                .copy(alpha = alpha)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = (if (selected) MaterialTheme.colorScheme.onSecondaryContainer else Color.White)
                .copy(alpha = alpha)
        )
    }
}

/** 识图源面板：SauceNAO / Ascii2d / Yandex。 */
@Composable
private fun SaucePanel(imageUrl: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .background(ViewerSurfaceColor, RoundedCornerShape(20.dp))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "以图搜源",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SAUCE_PROVIDERS.forEach { (name, baseUrl) ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            val encoded = java.net.URLEncoder.encode(imageUrl, "UTF-8")
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("$baseUrl$encoded")
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 本串相册九宫格总览：全屏黑色遮罩 + 底部滑出的 3 列网格（标注楼层号），点击跳转。
 * 用树内 overlay 而非 ModalBottomSheet：看图器本身在 Dialog 窗口里，
 * 嵌套弹窗窗口在部分机型上有层级/手势怪癖。
 */
@Composable
private fun AlbumGridSheet(
    pages: List<ViewerImage>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .background(ViewerSurfaceColor, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* 吞掉面板内点击，不透传遮罩 */ }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本串相册（${pages.size} 张）",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭相册",
                    tint = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onClose() }
                        .padding(4.dp)
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(pages, key = { it.url }) { item ->
                    val index = pages.indexOf(item)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(index) }
                    ) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(IslandImage.listUrl(item.url))
                                .crossfade(false)
                                .size(Size(240, 240))
                                .precision(Precision.INEXACT)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp)
                        )
                        if (item.postId != null) {
                            Text(
                                text = "No.${item.postId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        if (index == currentIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 条漫长图模式：撑满屏宽、自图片顶部开始纵向滑动阅读。 */
@Composable
private fun StripReader(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(Size(1440, Int.MAX_VALUE.coerceAtMost(4096)))
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .allowHardware(false)
                    .memoryCacheKey("strip:$imageUrl")
                    .diskCacheKey(imageUrl)
                    .build(),
                contentDescription = "Strip Image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // 顶部提示：点击左上返回按钮退出长图模式（复用顶栏关闭逻辑由外层处理，这里只提示模式）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "长图模式 · 上下滑动阅读，点右上 ✕ 退出",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ZoomableImageContainer(
    imageUrl: String,
    transform: ViewerTransform,
    onZoomedChanged: (Boolean) -> Unit,
    onImageRatio: (Float) -> Unit,
    onTapDismiss: () -> Unit
) {
    // Float states read inside graphicsLayer { } only invalidate draw, not composition/layout
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUrl) { mutableFloatStateOf(0f) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    var failed by remember(imageUrl) { mutableStateOf(false) }
    // 加载完成后记录图片真实宽高，用于计算 Fit 内切矩形做拖动夹紧
    var drawnImageSize by remember(imageUrl) { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val context = LocalContext.current
    val onZoomedChangedState = rememberUpdatedState(onZoomedChanged)
    val onTapDismissState = rememberUpdatedState(onTapDismiss)

    val isRotatedQuarter = transform.normalizedAngle == 90 || transform.normalizedAngle == 270

    // 上报高宽比（长图入口判断）
    LaunchedEffect(imageUrl, drawnImageSize) {
        val s = drawnImageSize
        if (s.width > 0f && s.height > 0f) {
            onImageRatio(s.height / s.width)
        }
    }

    // 把平移量夹紧到 Fit 内切矩形始终覆盖视口：拖到图片边界就停，不再拖出黑边；
    // 某轴放大后仍小于视口（如竖图横向）则该轴强制居中。
    // [effW/effH] 为计入旋转自适应系数后的有效绘制尺寸；[scale] 只含用户手势缩放。
    fun clampOffset(
        scale: Float,
        x: Float,
        y: Float,
        wPx: Int,
        hPx: Int,
        effW: Float,
        effH: Float
    ): Pair<Float, Float> {
        if (wPx <= 0 || hPx <= 0 || !scale.isFinite() || !x.isFinite() || !y.isFinite() ||
            effW <= 0f || effH <= 0f
        ) return x to y
        val w = wPx.toFloat()
        val h = hPx.toFloat()
        val dw = effW
        val dh = effH
        val dx = (w - dw) / 2f
        val dy = (h - dh) / 2f
        fun axis(off: Float, side: Float, d: Float, drawn: Float): Float {
            return if (scale * drawn >= side) {
                val minOff = side / 2f - scale * (d + drawn - side / 2f)
                val maxOff = side / 2f * (scale - 1f) - scale * d
                off.coerceIn(minOff, maxOff)
            } else {
                -scale * (d + drawn / 2f - side / 2f)
            }
        }
        return axis(x, w, dx, dw) to axis(y, h, dy, dh)
    }

    // 计入旋转自适应系数后的有效绘制尺寸（宽高互换 × 基准缩放）；[scale] 只含用户手势缩放。
    // 定义在组合层：双击与拖动两个手势块都要用。
    fun effectiveDims(viewportW: Float, viewportH: Float): Pair<Float, Float> {
        val iw = drawnImageSize.width.takeIf { it.isFinite() && it > 0f } ?: viewportW
        val ih = drawnImageSize.height.takeIf { it.isFinite() && it > 0f } ?: viewportH
        if (!isRotatedQuarter) return iw to ih
        // 旋转 90°/270°：绘制盒宽高互换，并按互换比例调整自适应基准缩放，
        // 防止横图竖起来后被屏幕两侧/上下裁切
        val base = min(viewportW / ih, viewportH / iw).coerceIn(0.25f, 3f)
        return (ih * base) to (iw * base)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Key is the url only. Keying on the settled page restarts this node when
            // currentPage flips halfway through a drag, which crashes pointer dispatch.
            .pointerInput(imageUrl) {
                fun applyScale(newScale: Float) {
                    if (!newScale.isFinite()) return
                    val clamped = newScale.coerceIn(1f, 5f)
                    val wasZoomed = scale > 1.01f
                    scale = clamped
                    if (clamped <= 1.01f) {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    val nowZoomed = clamped > 1.01f
                    if (nowZoomed != wasZoomed) {
                        onZoomedChangedState.value(nowZoomed)
                    }
                }
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1.01f) {
                            applyScale(1f)
                        } else {
                            // 双击以指尖为锚点放大：变换是"中心缩放 + 平移"，
                            // 保持指尖下的内容点在缩放前后位置不变
                            //（t' = (tap - center)(1 - s'/s) + (s'/s)t）。
                            val s0 = scale
                            val ratio = 3f / s0
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val nextX = (tap.x - cx) * (1f - ratio) + ratio * offsetX
                            val nextY = (tap.y - cy) * (1f - ratio) + ratio * offsetY
                            if (nextX.isFinite() && nextY.isFinite()) {
                                val (effW, effH) = effectiveDims(size.width.toFloat(), size.height.toFloat())
                                val (ax, ay) = clampOffset(3f, nextX, nextY, size.width, size.height, effW, effH)
                                offsetX = ax
                                offsetY = ay
                            }
                            scale = 3f
                            onZoomedChangedState.value(true)
                        }
                    },
                    onTap = {
                        onTapDismissState.value()
                    }
                )
            }
            .pointerInput(imageUrl, isRotatedQuarter) {
                detectZoomableTransformGestures(
                    onGesture = { pan, zoom ->
                        if (zoom.isFinite() && zoom > 0f && pan.x.isFinite() && pan.y.isFinite()) {
                            val clamped = (scale * zoom).coerceIn(1f, 5f)
                            val wasZoomed = scale > 1.01f
                            scale = clamped
                            if (clamped > 1.01f) {
                                // 平移向量发生在屏幕坐标系：RenderNode 的合成顺序为
                                // Scale → Rotate → Translate，translation 在最外层（屏幕轴），
                                // 因此无论旋转/镜像多少度，屏幕方向的单指拖动天然跟手，无需再做矩阵变换。
                                val (effW, effH) = effectiveDims(size.width.toFloat(), size.height.toFloat())
                                val (cx, cy) = clampOffset(
                                    clamped,
                                    offsetX + pan.x,
                                    offsetY + pan.y,
                                    size.width,
                                    size.height,
                                    effW,
                                    effH
                                )
                                offsetX = if (cx.isFinite()) cx else 0f
                                offsetY = if (cy.isFinite()) cy else 0f
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            if (clamped > 1.01f && !wasZoomed) {
                                onZoomedChangedState.value(true)
                            }
                        }
                    },
                    onGestureEnd = {
                        // Unlock paging only after the fingers are up. Flipping
                        // userScrollEnabled while the pager is dragging cancels the scroll.
                        onZoomedChangedState.value(scale > 1.01f)
                    },
                    canConsume = { scale > 1.01f }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Cap at the long screen edge. Two software bitmaps at 4096² are enough to
        // OOM a swipe; list thumbs stay on their own memory key so this decode
        // cannot recycle the bitmap the feed is still drawing under the dialog.
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val maxPx = remember(configuration, density) {
            val w = with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val h = with(density) { configuration.screenHeightDp.dp.roundToPx() }
            maxOf(w, h).coerceIn(720, 2048)
        }
        val imageRequest = remember(imageUrl, maxPx) {
            coil.request.ImageRequest.Builder(context)
                .data(imageUrl)
                .size(Size(maxPx, maxPx))
                .precision(Precision.INEXACT)
                .crossfade(false)
                // Pager draws this through a graphics layer. Hardware bitmaps crash
                // when that layer is recorded on the software path mid-swipe.
                .allowHardware(false)
                .memoryCacheKey("viewer:$maxPx:$imageUrl")
                .diskCacheKey(imageUrl)
                .build()
        }
        // 原图加载期间先垫缩略图：列表看过的话命中内存缓存秒显，没看过也只差 6KB；
        // 非岛图（thumb 转换不动地址）跳过，避免同 URL 重复加载。
        val thumbUrl = remember(imageUrl) {
            IslandImage.listUrl(imageUrl).takeIf { it != imageUrl }
        }
        // 缩略图与原图必须共享同一个变换层：graphicsLayer 只挂原图的话，
        // 放大拖动时原图滑走会露出静止的缩略图，画面像被"劈成两半"。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val safeScale = if (scale.isFinite()) scale.coerceIn(1f, 5f) else 1f
                    // 90°/270° 自适应基准缩放：与手势层的 effectiveDims 保持同一套系数
                    val iw = drawnImageSize.width
                    val ih = drawnImageSize.height
                    var base = 1f
                    if (isRotatedQuarter && iw > 0f && ih > 0f) {
                        base = min(size.width / ih, size.height / iw).coerceIn(0.25f, 3f)
                    }
                    // 旋转/镜像直接生效，不做动画（用户指定）
                    rotationZ = transform.cumulativeRotation
                    scaleX = safeScale * base * (if (transform.flippedH) -1f else 1f)
                    scaleY = safeScale * base * (if (transform.flippedV) -1f else 1f)
                    translationX = if (offsetX.isFinite()) offsetX else 0f
                    translationY = if (offsetY.isFinite()) offsetY else 0f
                }
        ) {
            if (thumbUrl != null) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = "Zoomable Image",
                contentScale = ContentScale.Fit,
                onState = { state ->
                    isLoading = state is AsyncImagePainter.State.Loading
                    failed = state is AsyncImagePainter.State.Error
                    if (state is AsyncImagePainter.State.Success) {
                        drawnImageSize = state.painter.intrinsicSize
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White
            )
        } else if (failed) {
            Text(
                text = "图片加载失败",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectZoomableTransformGestures(
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: () -> Unit,
    canConsume: () -> Boolean
) {
    awaitEachGesture {
        var consumedGesture = false
        try {
            val touchSlop = viewConfiguration.touchSlop
            var pastTouchSlop = false
            var zoom = 1f
            var pan = Offset.Zero
            awaitFirstDown(requireUnconsumed = false)
            while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount == 0) break
                // One finger at rest scale belongs to the pager. Consuming it, or
                // treating centroid noise as a pinch, disables userScroll mid-drag.
                val zooming = canConsume() || pressedCount > 1
                if (!zooming && event.changes.any { it.isConsumed }) break

                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!zoomChange.isFinite() || zoomChange <= 0f ||
                    !panChange.x.isFinite() || !panChange.y.isFinite()
                ) {
                    continue
                }

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = if (pan.x.isFinite() && pan.y.isFinite()) pan.getDistance() else 0f
                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (!zooming || !pastTouchSlop) continue

                consumedGesture = true
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onGesture(panChange, zoomChange)
                }
                event.changes.forEach { change ->
                    if (change.pressed && change.position != change.previousPosition) {
                        change.consume()
                    }
                }
            }
        } finally {
            if (consumedGesture) onGestureEnd()
        }
    }
}
