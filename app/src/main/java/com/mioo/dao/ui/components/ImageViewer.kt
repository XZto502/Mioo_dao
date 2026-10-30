package com.mioo.dao.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.size.Precision
import coil.size.Size
import com.mioo.dao.ui.theme.findActivity
import com.mioo.dao.ui.theme.isActivitySystemBarsOverridden
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import com.mioo.dao.utils.QrDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** 鐪嬪浘鍣ㄦ潯鐩細鍥剧墖鍦板潃 + 鎵€灞炴ゼ灞傦紙涔濆鏍兼爣娉ㄤ笌銆屽畾浣嶆ゼ灞傘€嶇敤锛涙棤妤煎眰淇℃伅鍙彧缁?url锛夈€?*/
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
        get() = ((cumulativeRotation.roundToInt() % 360) + 360) % 360

    val isTransformed: Boolean
        get() = normalizedAngle != 0 || flippedH || flippedV
}

private data class ViewerPalette(
    val canvas: Color,
    val dockBg: Color,
    val icon: Color,
    val border: Color,
    val panelBg: Color,
    val trackBg: Color,
    val buttonIdle: Color,
    val lightBars: Boolean
)

private val SauceProviders = listOf(
    "SauceNAO" to "https://saucenao.com/search.php?url=",
    "Ascii2d" to "https://ascii2d.net/search/url/",
    "Yandex" to "https://yandex.com/images/search?rpt=imageview&url="
)

private const val StripRatioThreshold = 2.2f

/** 默认纯黑。浅色衬底只在用户点药丸里的对比按钮后打开，药丸本身保持深炭黑。 */
private fun viewerPalette(lightCanvas: Boolean): ViewerPalette {
    val dockBg = Color(0xFF181A20).copy(alpha = 0.88f)
    val border = Color.White.copy(alpha = 0.12f)
    return ViewerPalette(
        canvas = if (lightCanvas) Color(0xFFE8ECF2) else Color.Black,
        dockBg = dockBg,
        icon = Color.White,
        border = border,
        panelBg = dockBg,
        trackBg = Color(0xFF101216),
        buttonIdle = Color(0xFF22252C),
        lightBars = lightCanvas
    )
}

/**
 * ContentScale.Fit 后的屏幕占位。旋转 90°/270° 再乘 [fitRotationScale] 得到可拖动边界。
 * zoomScale <= 1 且图片已完整 Fit 时 max 恒为 0，图片锁在正中。
 */
private fun calculateBounds(
    containerSize: IntSize,
    intrinsicSize: IntSize,
    zoomScale: Float,
    normalizedAngle: Int,
    fitRotationScale: Float
): Pair<Float, Float> {
    if (containerSize.width == 0 || containerSize.height == 0 ||
        intrinsicSize.width == 0 || intrinsicSize.height == 0
    ) return 0f to 0f

    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    val imgAspect = intrinsicSize.width.toFloat() / intrinsicSize.height.toFloat()
    val containerAspect = cw / ch

    val (fitW, fitH) = if (containerAspect > imgAspect) {
        (ch * imgAspect) to ch
    } else {
        cw to (cw / imgAspect)
    }

    val isRotated90or270 = normalizedAngle == 90 || normalizedAngle == 270
    val baseW = (if (isRotated90or270) fitH else fitW) * fitRotationScale
    val baseH = (if (isRotated90or270) fitW else fitH) * fitRotationScale

    val currentW = baseW * zoomScale
    val currentH = baseH * zoomScale

    val maxX = ((currentW - cw) / 2f).coerceAtLeast(0f)
    val maxY = ((currentH - ch) / 2f).coerceAtLeast(0f)
    return maxX to maxY
}

private fun contentFitWidth(containerSize: IntSize, intrinsicSize: IntSize): Float? {
    if (containerSize.width == 0 || containerSize.height == 0 ||
        intrinsicSize.width == 0 || intrinsicSize.height == 0
    ) return null
    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    val imgAspect = intrinsicSize.width.toFloat() / intrinsicSize.height.toFloat()
    val containerAspect = cw / ch
    return if (containerAspect > imgAspect) ch * imgAspect else cw
}

/** 90\u00B0/270\u00B0 时把旋转后的 Fit 矩形缩回容器内；0\u00B0/180\u00B0 为 1。 */
private fun fitRotationScaleOf(
    containerSize: IntSize,
    intrinsicSize: IntSize,
    normalizedAngle: Int
): Float {
    if (normalizedAngle != 90 && normalizedAngle != 270) return 1f
    if (containerSize.width == 0 || containerSize.height == 0 ||
        intrinsicSize.width == 0 || intrinsicSize.height == 0
    ) return 1f
    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    val imgAspect = intrinsicSize.width.toFloat() / intrinsicSize.height.toFloat()
    val containerAspect = cw / ch
    val (fitW, fitH) = if (containerAspect > imgAspect) {
        (ch * imgAspect) to ch
    } else {
        cw to (cw / imgAspect)
    }
    if (fitW <= 0f || fitH <= 0f) return 1f
    return min(cw / fitH, ch / fitW)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(
    imageUrl: String? = null,
    initialImageUrl: String = imageUrl ?: "",
    imageUrls: List<String> = listOf(initialImageUrl),
    onDismiss: () -> Unit,
    imageMeta: List<ViewerImage> = emptyList(),
    onLocateFloor: (String) -> Unit = {},
    /** 婊戝埌宸插姞杞藉浘鐗囩殑鏈熬闄勮繎鏃讹紝璇峰閮ㄥ啀鎷変竴椤典覆鍐呭銆?*/
    onRequestMorePages: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = isReducedMotionEnabled()

    val currentImages by rememberUpdatedState(
        if (imageMeta.isNotEmpty()) {
            imageMeta.filter { it.url.isNotBlank() }.distinctBy { it.url }
        } else {
            (if (imageUrls.isNotEmpty()) imageUrls else listOf(initialImageUrl))
                .filter { it.isNotBlank() }
                .distinct()
                .map { ViewerImage(url = it) }
        }
    )
    if (currentImages.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val initialIndex = remember(initialImageUrl) {
        currentImages.indexOfFirst { it.url == initialImageUrl }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (currentImages.size - 1).coerceAtLeast(0)),
        pageCount = { currentImages.size }
    )
    val requestMore by rememberUpdatedState(onRequestMorePages)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to currentImages.size }
            .collect { (page, total) ->
                if (total > 0 && page >= total - 2) {
                    requestMore()
                }
            }
    }

    var transform by remember { mutableStateOf(ViewerTransform()) }
    var stripMode by remember { mutableStateOf(false) }
    var stripRequest by remember { mutableIntStateOf(0) }
    var resetTick by remember { mutableIntStateOf(0) }
    var currentRatio by remember { mutableStateOf(1f) }
    var zoomed by remember { mutableStateOf(false) }
    var lockPager by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showTransformPanel by remember { mutableStateOf(false) }
    var showSauceMenu by remember { mutableStateOf(false) }
    var lightCanvas by remember { mutableStateOf(false) }
    var handledPage by remember { mutableIntStateOf(pagerState.currentPage) }
    if (pagerState.currentPage != handledPage) {
        handledPage = pagerState.currentPage
        transform = ViewerTransform()
        stripMode = false
        zoomed = false
        lockPager = false
        showTransformPanel = false
        showSauceMenu = false
        currentRatio = 1f
    }

    val palette = viewerPalette(lightCanvas)
    val backgroundAlpha = remember { mutableFloatStateOf(1f) }
    val qrTexts = remember { androidx.compose.runtime.mutableStateMapOf<String, String?>() }
    val currentMeta = currentImages.getOrNull(pagerState.currentPage)
    val currentUrl = currentMeta?.url.orEmpty()

    LaunchedEffect(currentUrl) {
        if (currentUrl.isBlank() || qrTexts.containsKey(currentUrl)) return@LaunchedEffect
        val text = withContext(Dispatchers.IO) {
            runCatching { QrDetector.detect(context, currentUrl, maxDim = 1280) }.getOrNull()
        }
        qrTexts[currentUrl] = text
    }

    val panelOpen = showTransformPanel || showSauceMenu
    val hasAnyTransform = transform.isTransformed || zoomed

    val openProgress = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }
    val dismissCallback by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        openProgress.animateTo(1f, if (reducedMotion) snap() else tween(230, easing = FastOutSlowInEasing))
    }
    fun triggerSmoothClose(afterClose: () -> Unit = {}) {
        if (isClosing) return
        isClosing = true
        scope.launch {
            openProgress.animateTo(0f, if (reducedMotion) snap() else tween(180, easing = FastOutLinearInEasing))
            dismissCallback()
            afterClose()
        }
    }

    BackHandler(enabled = true) { triggerSmoothClose() }
    ViewerBarsEffect(lightCanvas = lightCanvas)
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .pointerInput(isClosing) {
                if (!isClosing) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.forEach { change ->
                            if (!change.isConsumed) change.consume()
                        }
                    }
                }
            }
    ) {
            ViewerCanvas(
                color = palette.canvas,
                alpha = backgroundAlpha,
                openProgress = openProgress.asState()
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !lockPager && !isClosing,
                beyondBoundsPageCount = 0,
                key = { currentImages.getOrNull(it)?.url ?: it }
            ) { pageIndex ->
                val page = currentImages.getOrNull(pageIndex)
                if (page == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    ZoomablePage(
                        imageUrl = page.url,
                        transform = transform,
                        stripMode = stripMode,
                        stripRequest = stripRequest,
                        resetTick = resetTick,
                        isActive = pagerState.currentPage == pageIndex,
                        scrimColor = palette.icon,
                        backgroundAlpha = backgroundAlpha,
                        openProgress = openProgress.asState(),
                        isClosing = isClosing,
                        onZoomed = { zoomed = it },
                        onPagerLocked = { lockPager = it },
                        onRatio = { if (pagerState.currentPage == pageIndex) currentRatio = it },
                        onStripBroken = { if (stripMode) stripMode = false },
                        onToggleChrome = {
                            if (panelOpen) {
                                showTransformPanel = false
                                showSauceMenu = false
                            } else {
                                showControls = !showControls
                            }
                        },
                        onDismiss = { triggerSmoothClose() }
                    )
                }
            }

            val chromeFadeIn = if (reducedMotion) snap<Float>() else tween(durationMillis = 180, easing = LinearOutSlowInEasing)
            val chromeFadeOut = if (reducedMotion) snap<Float>() else tween(durationMillis = 140, easing = FastOutLinearInEasing)
            val chromeSlideIn = if (reducedMotion) snap<IntOffset>() else tween(durationMillis = 220, easing = FastOutSlowInEasing)
            val chromeSlideOut = if (reducedMotion) snap<IntOffset>() else tween(durationMillis = 200, easing = FastOutSlowInEasing)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val dragProgress = ((1f - backgroundAlpha.floatValue) / 0.85f).coerceIn(0f, 1f)
                        val controlsAlpha = ((openProgress.value - 0.25f) / 0.75f).coerceIn(0f, 1f) *
                            (1f - dragProgress * 2f).coerceIn(0f, 1f)
                        alpha = controlsAlpha
                        translationY = if (reducedMotion) 0f else (1f - controlsAlpha) * 12.dp.toPx()
                    }
            ) {
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(chromeFadeIn) + slideInVertically(chromeSlideIn) { -it / 2 },
                    exit = fadeOut(chromeFadeOut) + slideOutVertically(chromeSlideOut) { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ViewerTopBar(
                        palette = palette,
                        onClose = { triggerSmoothClose() }
                    )
                }

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(chromeFadeIn) + slideInVertically(chromeSlideIn) { it },
                    exit = fadeOut(chromeFadeOut) + slideOutVertically(chromeSlideOut) { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val qrText = qrTexts[currentUrl]
                    if (!qrText.isNullOrBlank() && !showTransformPanel) {
                        Surface(
                            shape = CircleShape,
                            color = palette.dockBg,
                            contentColor = palette.icon,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = BorderStroke(1.dp, palette.border),
                            onClick = { qrText?.let { QrDetector.openResult(context, it) } },
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "[识别图中二维码]",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                    if (currentRatio > StripRatioThreshold && !showTransformPanel) {
                        Surface(
                            onClick = {
                                if (stripMode) {
                                    stripMode = false
                                    resetTick++
                                } else {
                                    stripMode = true
                                    stripRequest++
                                }
                            },
                            shape = CircleShape,
                            color = if (stripMode) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                palette.dockBg
                            },
                            contentColor = if (stripMode) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                palette.icon
                            },
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "长图撑满",
                                fontSize = 12.5.sp,
                                fontWeight = if (stripMode) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    if (!showTransformPanel && currentMeta?.postId != null) {
                        FloorChip(
                            palette = palette,
                            postId = currentMeta?.postId.orEmpty(),
                            snippet = currentMeta?.snippet,
                            onLocate = {
                                currentMeta?.postId?.let { postId ->
                                    triggerSmoothClose { onLocateFloor(postId) }
                                }
                            }
                        )
                    }
                    AnimatedVisibility(
                        visible = showTransformPanel,
                        enter = fadeIn(chromeFadeIn) + slideInVertically(chromeSlideIn) { it / 3 },
                        exit = fadeOut(chromeFadeOut) + slideOutVertically(chromeSlideOut) { it / 3 }
                    ) {
                        Column {
                            TransformPanel(
                                palette = palette,
                                transform = transform,
                                canReset = hasAnyTransform,
                                onRotateTo = { targetAngle ->
                                    val deltaRaw = ((targetAngle - transform.normalizedAngle) + 360) % 360
                                    val delta = if (deltaRaw > 180) deltaRaw - 360f else deltaRaw.toFloat()
                                    transform = transform.copy(
                                        cumulativeRotation = transform.cumulativeRotation + delta
                                    )
                                },
                                onFlipH = { transform = transform.copy(flippedH = !transform.flippedH) },
                                onFlipV = { transform = transform.copy(flippedV = !transform.flippedV) },
                                onReset = {
                                    transform = ViewerTransform()
                                    stripMode = false
                                    resetTick++
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                        ViewerDock(
                            palette = palette,
                            rotateActive = showTransformPanel || transform.isTransformed,
                            lightCanvas = lightCanvas,
                            imageUrl = currentUrl,
                            showSauce = showSauceMenu,
                            onRotate = {
                                showSauceMenu = false
                                showTransformPanel = !showTransformPanel
                            },
                            onCanvas = { lightCanvas = !lightCanvas },
                            onSauce = {
                                showTransformPanel = false
                                showSauceMenu = !showSauceMenu
                            },
                            onDismissSauce = { showSauceMenu = false },
                            onDownload = {
                                val image = currentImages.getOrNull(pagerState.currentPage) ?: return@ViewerDock
                                com.mioo.dao.utils.ImageDownloader.downloadImageTransformed(
                                    context = context,
                                    imageUrl = image.url,
                                    rotationDegrees = transform.normalizedAngle,
                                    flipX = transform.flippedH,
                                    flipY = transform.flippedV,
                                    scope = scope
                                )
                            },
                            onShare = {
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

        }
    }

@Composable
private fun ViewerCanvas(
    color: Color,
    alpha: MutableFloatState,
    openProgress: State<Float>
) {
    val fade = (openProgress.value * alpha.floatValue).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = fade))
    )
}

/**
 * 鏌ョ湅鍣ㄩ摵鍦?Activity 涓婏紝绯荤粺鏍忎繚鎸侀€忔槑锛屽彧鏀瑰浘鏍囨槑鏆椼€? * 绾粦鐢诲竷鐢ㄧ櫧鍥炬爣锛涙祬鑹茶‖搴曠敤娣辫壊鍥炬爣銆傞€€鍑烘椂杩樺師銆? */
@Composable
private fun ViewerBarsEffect(lightCanvas: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(lightCanvas) {
        val window = (context.findActivity() ?: view.context.findActivity())?.window
            ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val originalStatus = window.statusBarColor
        val originalNav = window.navigationBarColor
        val originalLightStatus = controller.isAppearanceLightStatusBars
        val originalLightNav = controller.isAppearanceLightNavigationBars
        val originalDivider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor
        } else {
            null
        }
        isActivitySystemBarsOverridden = true
        fun apply() {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightCanvas
                isAppearanceLightNavigationBars = lightCanvas
            }
        }
        apply()
        val decor = window.decorView
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener { apply() }
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            if (decor.viewTreeObserver.isAlive) {
                decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
            isActivitySystemBarsOverridden = false
            window.statusBarColor = originalStatus
            window.navigationBarColor = originalNav
            if (originalDivider != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = originalDivider
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = originalLightStatus
                isAppearanceLightNavigationBars = originalLightNav
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    imageUrl: String,
    transform: ViewerTransform,
    stripMode: Boolean,
    stripRequest: Int,
    resetTick: Int,
    isActive: Boolean,
    scrimColor: Color,
    backgroundAlpha: MutableFloatState,
    openProgress: State<Float>,
    isClosing: Boolean,
    onZoomed: (Boolean) -> Unit,
    onPagerLocked: (Boolean) -> Unit,
    onRatio: (Float) -> Unit,
    onStripBroken: () -> Unit,
    onToggleChrome: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hapticView = LocalView.current
    val containerSize = remember { mutableStateOf(IntSize.Zero) }
    val intrinsicSize = remember(imageUrl) { mutableStateOf(IntSize.Zero) }
    val zoom = remember(imageUrl) { mutableFloatStateOf(1f) }
    val offsetX = remember(imageUrl) { mutableFloatStateOf(0f) }
    val offsetY = remember(imageUrl) { mutableFloatStateOf(0f) }
    val dismissY = remember(imageUrl) { mutableFloatStateOf(0f) }
    val dismissScale = remember(imageUrl) { mutableFloatStateOf(1f) }
    var closingFromDrag by remember(imageUrl) { mutableStateOf(false) }
    val animScope = rememberCoroutineScope()
    val motionJob = remember(imageUrl) { mutableStateOf<Job?>(null) }
    fun spawnMotion(block: suspend CoroutineScope.() -> Unit) {
        motionJob.value?.cancel()
        motionJob.value = animScope.launch(block = block)
    }
    LaunchedEffect(isClosing) {
        if (isClosing) motionJob.value?.cancel()
    }
    var loading by remember(imageUrl) { mutableStateOf(true) }
    var failed by remember(imageUrl) { mutableStateOf(false) }

    val reducedMotion = isReducedMotionEnabled()
    val motion = if (reducedMotion) snap<Float>() else tween<Float>(220)
    val animatedRotation by animateFloatAsState(transform.cumulativeRotation, motion, label = "rot")
    val animatedFlipX by animateFloatAsState(if (transform.flippedH) -1f else 1f, motion, label = "fx")
    val animatedFlipY by animateFloatAsState(if (transform.flippedV) -1f else 1f, motion, label = "fy")
    val targetFit = fitRotationScaleOf(
        containerSize.value,
        intrinsicSize.value,
        transform.normalizedAngle
    )
    val fitRotationScale by animateFloatAsState(targetFit, motion, label = "fitRot")

    val latest = rememberUpdatedState(
        PageInputs(
            angle = transform.normalizedAngle,
            fit = fitRotationScale,
            isActive = isActive,
            onZoomed = onZoomed,
            onPagerLocked = onPagerLocked,
            onStripBroken = onStripBroken,
            onToggleChrome = onToggleChrome,
            onDismiss = onDismiss
        )
    )

    LaunchedEffect(transform.normalizedAngle, containerSize.value, intrinsicSize.value) {
        if (zoom.value <= 1.01f) {
            offsetX.value = 0f
            offsetY.value = 0f
            return@LaunchedEffect
        }
        val fit = fitRotationScaleOf(containerSize.value, intrinsicSize.value, transform.normalizedAngle)
        val (maxX, maxY) = calculateBounds(
            containerSize.value,
            intrinsicSize.value,
            zoom.value,
            transform.normalizedAngle,
            fit
        )
        offsetX.value = offsetX.value.coerceIn(-maxX, maxX)
        offsetY.value = offsetY.value.coerceIn(-maxY, maxY)
    }

    LaunchedEffect(stripRequest, stripMode) {
        if (!latest.value.isActive || stripRequest == 0 || !stripMode) return@LaunchedEffect
        while (containerSize.value.width == 0 || intrinsicSize.value.width == 0) {
            withFrameNanos { }
        }
        if (!stripMode || !latest.value.isActive) return@LaunchedEffect
        val fitW = contentFitWidth(containerSize.value, intrinsicSize.value) ?: return@LaunchedEffect
        if (fitW <= 0f) return@LaunchedEffect
        val cw = containerSize.value.width.toFloat()
        val z = (cw / fitW).coerceIn(1f, 5f)
        val fit = fitRotationScaleOf(containerSize.value, intrinsicSize.value, latest.value.angle)
        val (_, maxY) = calculateBounds(containerSize.value, intrinsicSize.value, z, latest.value.angle, fit)
        coroutineScope {
            launch { zoom.animateTo(z, spring()) }
            launch { offsetX.animateTo(0f, spring()) }
            launch { offsetY.animateTo(maxY, spring()) }
        }
        latest.value.onZoomed(z > 1.01f)
        latest.value.onPagerLocked(false)
    }

    LaunchedEffect(resetTick) {
        if (resetTick == 0 || !latest.value.isActive) return@LaunchedEffect
        coroutineScope {
            launch { zoom.animateTo(1f, spring()) }
            launch { offsetX.animateTo(0f, spring()) }
            launch { offsetY.animateTo(0f, spring()) }
        }
        latest.value.onZoomed(false)
        latest.value.onPagerLocked(false)
    }

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
            .allowHardware(false)
            .memoryCacheKey("viewer:$maxPx:$imageUrl")
            .diskCacheKey(imageUrl)
            .build()
    }
    val thumbUrl = remember(imageUrl) { IslandImage.listUrl(imageUrl).takeIf { it != imageUrl } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { alpha = openProgress.value }
            .onSizeChanged { containerSize.value = it }
            .pointerInput(imageUrl, isClosing) {
                if (isClosing) return@pointerInput
                detectTapGestures(
                    onTap = {
                        latest.value.onToggleChrome()
                    },
                    onDoubleTap = { tap ->
                        val inputs = latest.value
                        inputs.onStripBroken()
                        val s0 = zoom.value.coerceAtLeast(0.01f)
                        if (s0 > 1.05f) {
                            spawnMotion {
                                coroutineScope {
                                    launch { zoom.animateTo(1f, spring()) }
                                    launch { offsetX.animateTo(0f, spring()) }
                                    launch { offsetY.animateTo(0f, spring()) }
                                }
                                inputs.onZoomed(false)
                                inputs.onPagerLocked(false)
                            }
                        } else {
                            val s1 = 2.5f
                            val ratio = s1 / s0
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            var nextX = (tap.x - cx) * (1f - ratio) + ratio * offsetX.value
                            var nextY = (tap.y - cy) * (1f - ratio) + ratio * offsetY.value
                            val (maxX, maxY) = calculateBounds(
                                containerSize.value,
                                intrinsicSize.value,
                                s1,
                                inputs.angle,
                                inputs.fit
                            )
                            nextX = nextX.coerceIn(-maxX, maxX)
                            nextY = nextY.coerceIn(-maxY, maxY)
                            spawnMotion {
                                coroutineScope {
                                    launch { zoom.animateTo(s1, spring()) }
                                    launch { offsetX.animateTo(nextX, spring()) }
                                    launch { offsetY.animateTo(nextY, spring()) }
                                }
                                inputs.onZoomed(true)
                                val atEdge = maxX <= 1f || nextX <= -maxX + 1f || nextX >= maxX - 1f
                                inputs.onPagerLocked(!atEdge)
                            }
                        }
                    }
                )
            }
            .pointerInput(imageUrl, isClosing) {
                if (isClosing) return@pointerInput
                detectViewerGestures(
                    zoom = zoom,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    dismissY = dismissY,
                    dismissScale = dismissScale,
                    backgroundAlpha = backgroundAlpha,
                    containerSize = { containerSize.value },
                    intrinsicSize = { intrinsicSize.value },
                    angle = { latest.value.angle },
                    fitScale = { latest.value.fit },
                    onZoomed = { latest.value.onZoomed(it) },
                    onPagerLocked = { latest.value.onPagerLocked(it) },
                    onStripBroken = { latest.value.onStripBroken() },
                    onDismiss = {
                        closingFromDrag = true
                        latest.value.onDismiss()
                    },
                    hapticView = hapticView,
                    animScope = animScope,
                    onMotionStarted = { motionJob.value = it },
                    onCancelMotion = { motionJob.value?.cancel() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    val progress = openProgress.value
                    val exitScale = if (reducedMotion) 1f else if (closingFromDrag) {
                        0.88f + 0.12f * progress
                    } else {
                        0.86f + 0.14f * progress
                    }
                    translationY = offsetY.value + dismissY.value +
                        if (reducedMotion || closingFromDrag) 0f else (1f - progress) * 18.dp.toPx()
                    shape = RoundedCornerShape(if (reducedMotion) 0.dp else ((1f - progress) * 16f).dp)
                    clip = true
                    val scale = zoom.value * dismissScale.value * exitScale
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = animatedRotation
                        scaleX = fitRotationScale * animatedFlipX
                        scaleY = fitRotationScale * animatedFlipY
                    }
            ) {
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    onSuccess = { state ->
                        loading = false
                        failed = false
                        val drawable = state.result.drawable
                        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                            intrinsicSize.value = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
                            if (latest.value.isActive) {
                                onRatio(drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth.toFloat())
                            }
                        }
                    },
                    onLoading = { loading = true },
                    onError = {
                        loading = false
                        failed = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (loading) {
            CircularProgressIndicator(
                color = scrimColor,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (failed) {
            Text(
                text = "图片加载失败",
                color = scrimColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private data class PageInputs(
    val angle: Int,
    val fit: Float,
    val isActive: Boolean,
    val onZoomed: (Boolean) -> Unit,
    val onPagerLocked: (Boolean) -> Unit,
    val onStripBroken: () -> Unit,
    val onToggleChrome: () -> Unit,
    val onDismiss: () -> Unit
)

private suspend fun MutableFloatState.animateTo(
    target: Float,
    spec: androidx.compose.animation.core.AnimationSpec<Float>
) {
    val state = this
    val start = state.value
    if (start == target) return
    val anim = Animatable(start)
    anim.animateTo(target, spec) {
        state.floatValue = anim.value
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectViewerGestures(
    zoom: MutableFloatState,
    offsetX: MutableFloatState,
    offsetY: MutableFloatState,
    dismissY: MutableFloatState,
    dismissScale: MutableFloatState,
    backgroundAlpha: MutableFloatState,
    containerSize: () -> IntSize,
    intrinsicSize: () -> IntSize,
    angle: () -> Int,
    fitScale: () -> Float,
    onZoomed: (Boolean) -> Unit,
    onPagerLocked: (Boolean) -> Unit,
    onStripBroken: () -> Unit,
    onDismiss: () -> Unit,
    hapticView: View,
    animScope: CoroutineScope,
    onMotionStarted: (Job) -> Unit,
    onCancelMotion: () -> Unit
) {
    val touchSlop = viewConfiguration.touchSlop
    val fling = android.view.ViewConfiguration.get(hapticView.context).scaledMinimumFlingVelocity.toFloat()
    val dismissThreshold = 120.dp.toPx()
    awaitEachGesture {
        onCancelMotion()
        val tracker = VelocityTracker()
        var mode = 0
        var total = Offset.Zero
        var hapticFired = false
        val down = awaitFirstDown(requireUnconsumed = false)
        tracker.addPointerInputChange(down)
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { tracker.addPointerInputChange(it) }
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) break
            val zoomChange = event.calculateZoom()
            val pan = event.calculatePan()
            if (!zoomChange.isFinite() || zoomChange <= 0f || !pan.x.isFinite() || !pan.y.isFinite()) {
                continue
            }
            if (pressed >= 2) {
                mode = 4
                onStripBroken()
                val next = (zoom.value * zoomChange).coerceIn(1f, 5f)
                zoom.value = next
                if (next > 1.01f) {
                    offsetX.value += pan.x
                    offsetY.value += pan.y
                }
                clampZoomOffsets(zoom, offsetX, offsetY, containerSize, intrinsicSize, angle, fitScale, onZoomed, onPagerLocked)
                if (dismissY.value != 0f) {
                    dismissY.value = 0f
                    dismissScale.value = 1f
                    backgroundAlpha.value = 1f
                }
                event.changes.forEach { if (it.pressed) it.consume() }
                continue
            }
            if (mode == 4) mode = if (zoom.value > 1.01f) 3 else 0
            if (mode == 0) {
                total += pan
                if (total.getDistance() > touchSlop) {
                    val ax = abs(total.x)
                    val ay = abs(total.y)
                    mode = when {
                        zoom.value > 1.01f -> 3
                        ax > ay * 1.2f -> 1
                        ay > ax * 1.4f -> 2
                        else -> 0
                    }
                }
            }
            when (mode) {
                1 -> Unit
                2 -> {
                    onPagerLocked(true)
                    event.changes.forEach { if (it.pressed) it.consume() }
                    val y = dismissY.value + pan.y
                    dismissY.value = y
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val progress = (abs(y) / (h * 0.35f)).coerceIn(0f, 1f)
                    dismissScale.value = 1f - 0.35f * progress
                    backgroundAlpha.value = 1f - 0.85f * progress
                    if (!hapticFired && abs(y) >= dismissThreshold) {
                        hapticFired = true
                        hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
                3 -> {
                    val (maxX, maxY) = calculateBounds(
                        containerSize(), intrinsicSize(), zoom.value, angle(), fitScale()
                    )
                    val newX = offsetX.value + pan.x
                    val newY = (offsetY.value + pan.y).coerceIn(-maxY, maxY)
                    val pushingOut = (newX < -maxX && pan.x < 0f) || (newX > maxX && pan.x > 0f)
                    if (pushingOut && abs(pan.x) >= abs(pan.y)) {
                        offsetX.value = offsetX.value.coerceIn(-maxX, maxX)
                        onPagerLocked(false)
                    } else {
                        offsetX.value = newX.coerceIn(-maxX, maxX)
                        offsetY.value = newY
                        onZoomed(true)
                        val atEdge = maxX <= 1f ||
                            offsetX.value <= -maxX + 1f ||
                            offsetX.value >= maxX - 1f
                        onPagerLocked(!atEdge)
                        event.changes.forEach { if (it.pressed) it.consume() }
                    }
                }
            }
        }
        val velocity = tracker.calculateVelocity()
        when (mode) {
            2 -> {
                val y = dismissY.value
                val flung = velocity.y * y > 0f && abs(velocity.y) > fling
                if (abs(y) >= dismissThreshold || flung) {
                    // Preserve the release pose; the viewer owns the exit lifetime.
                    onDismiss()
                } else {
                    onMotionStarted(animScope.launch {
                        coroutineScope {
                            launch { dismissY.animateTo(0f, spring()) }
                            launch { dismissScale.animateTo(1f, spring()) }
                            launch { backgroundAlpha.animateTo(1f, spring()) }
                        }
                        onPagerLocked(zoom.value > 1.01f)
                    })
                }
            }
            4 -> {
                if (zoom.value < 1.02f) {
                    onMotionStarted(animScope.launch {
                        coroutineScope {
                            launch { zoom.animateTo(1f, spring()) }
                            launch { offsetX.animateTo(0f, spring()) }
                            launch { offsetY.animateTo(0f, spring()) }
                        }
                        onZoomed(false)
                        onPagerLocked(false)
                    })
                }
            }
        }
    }
}

private fun clampZoomOffsets(
    zoom: MutableFloatState,
    offsetX: MutableFloatState,
    offsetY: MutableFloatState,
    containerSize: () -> IntSize,
    intrinsicSize: () -> IntSize,
    angle: () -> Int,
    fitScale: () -> Float,
    onZoomed: (Boolean) -> Unit,
    onPagerLocked: (Boolean) -> Unit
) {
    val z = zoom.value
    if (z <= 1.01f) {
        offsetX.value = 0f
        offsetY.value = 0f
        onZoomed(false)
        onPagerLocked(false)
        return
    }
    val (maxX, maxY) = calculateBounds(containerSize(), intrinsicSize(), z, angle(), fitScale())
    offsetX.value = offsetX.value.coerceIn(-maxX, maxX)
    offsetY.value = offsetY.value.coerceIn(-maxY, maxY)
    onZoomed(true)
    val atEdge = maxX <= 1f || offsetX.value <= -maxX + 1f || offsetX.value >= maxX - 1f
    onPagerLocked(!atEdge)
}

@Composable
private fun ViewerTopBar(
    palette: ViewerPalette,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        IconButton(onClick = onClose) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.dockBg)
                    .border(1.dp, palette.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = palette.icon
                )
            }
        }
    }
}

@Composable
private fun FloorChip(
    palette: ViewerPalette,
    postId: String,
    snippet: String?,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (snippet.isNullOrBlank()) {
        "No.$postId"
    } else {
        "No.$postId · $snippet"
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, bottom = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            onClick = onLocate,
            shape = RoundedCornerShape(14.dp),
            color = palette.dockBg,
            contentColor = palette.icon,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .wrapContentWidth()
                .widthIn(max = 220.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun ViewerDock(
    palette: ViewerPalette,
    rotateActive: Boolean,
    lightCanvas: Boolean,
    imageUrl: String,
    showSauce: Boolean,
    onRotate: () -> Unit,
    onCanvas: () -> Unit,
    onSauce: () -> Unit,
    onDismissSauce: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = palette.dockBg,
        contentColor = palette.icon,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewerIconButton(
                icon = Icons.Outlined.Rotate90DegreesCcw,
                description = "旋转与镜像",
                palette = palette,
                active = rotateActive,
                onClick = onRotate
            )
            ViewerIconButton(
                icon = Icons.Outlined.Contrast,
                description = "切换画布底色",
                palette = palette,
                active = lightCanvas,
                onClick = onCanvas
            )
            Box {
                ViewerIconButton(
                    icon = Icons.Outlined.ImageSearch,
                    description = "以图搜源",
                    palette = palette,
                    active = showSauce,
                    onClick = onSauce
                )
                SauceSourceMenu(
                    expanded = showSauce,
                    imageUrl = imageUrl,
                    palette = palette,
                    onDismiss = onDismissSauce
                )
            }
            ViewerIconButton(
                icon = Icons.Outlined.Download,
                description = "保存图片",
                palette = palette,
                active = false,
                onClick = onDownload
            )
            ViewerIconButton(
                icon = Icons.Outlined.Share,
                description = "分享",
                palette = palette,
                active = false,
                onClick = onShare
            )
        }
    }
}

@Composable
private fun ViewerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    palette: ViewerPalette,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else palette.icon,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}


/**
 * 绱у噾鍦嗚鑿滃崟銆侻aterial3 1.2 鐨?DropdownMenu 涓嶈兘璁?surfaceTint / containerColor锛? * 鑷粯 Surface 鐢ㄦ樉寮忓簳鑹诧紝閬垮厤 elevation 鎶婅彍鍗曠僵鎴愮伆鐧姐€? */
@Composable
private fun SauceSourceMenu(
    expanded: Boolean,
    imageUrl: String,
    palette: ViewerPalette,
    onDismiss: () -> Unit
) {
    if (!expanded || imageUrl.isBlank()) return
    val context = LocalContext.current
    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    val gap = 8
                    val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2)
                        .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
                    val above = anchorBounds.top - popupContentSize.height - gap
                    val y = if (above >= 8) above else (anchorBounds.bottom + gap)
                    return IntOffset(x, y)
                }
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.panelBg,
            contentColor = palette.icon,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SauceProviders.forEach { (name, baseUrl) ->
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val encoded = URLEncoder.encode(imageUrl, "UTF-8")
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("$baseUrl$encoded"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransformPanel(
    palette: ViewerPalette,
    transform: ViewerTransform,
    canReset: Boolean,
    onRotateTo: (Int) -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(22.dp),
        color = palette.panelBg,
        contentColor = palette.icon,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = CircleShape,
                color = palette.trackBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
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
                                text = "${angle}\u00B0",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    palette.icon
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransformActionButton(
                    palette = palette,
                    icon = Icons.Filled.SwapHoriz,
                    label = "水平镜像",
                    selected = transform.flippedH,
                    enabled = true,
                    onClick = onFlipH,
                    modifier = Modifier.weight(1f)
                )
                TransformActionButton(
                    palette = palette,
                    icon = Icons.Filled.SwapVert,
                    label = "垂直镜像",
                    selected = transform.flippedV,
                    enabled = true,
                    onClick = onFlipV,
                    modifier = Modifier.weight(1f)
                )
                TransformActionButton(
                    palette = palette,
                    icon = Icons.Filled.Refresh,
                    label = "复原",
                    selected = false,
                    enabled = canReset,
                    iconSize = 15.dp,
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TransformActionButton(
    palette: ViewerPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else palette.buttonIdle
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else palette.icon
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .height(38.dp)
            .alpha(if (enabled) 1f else 0.38f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.5.sp,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

