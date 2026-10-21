package com.mioo.dao.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mioo.dao.ui.theme.ImmersiveDialogEffect
import com.mioo.dao.ui.theme.immersiveDialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.size.Precision
import coil.size.Size
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(
    imageUrl: String? = null,
    initialImageUrl: String = imageUrl ?: "",
    imageUrls: List<String> = listOf(initialImageUrl),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Freeze the page list for the life of this dialog. Thread pagination keeps
    // rebuilding imageUrls behind the viewer; a pageCount change mid-fling throws
    // inside PagerState.
    val pages = remember {
        val raw = if (imageUrls.isNotEmpty()) imageUrls else listOf(initialImageUrl)
        raw.filter { it.isNotBlank() }.distinct()
    }
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val initialPage = remember(initialImageUrl) {
        val idx = pages.indexOf(initialImageUrl)
        if (idx == -1) 0 else idx
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    // Boolean only: continuous scale floats must not recompose the pager shell every frame
    var isZoomed by remember { mutableStateOf(false) }

    // Reset zoom lock when page changes. Same value is a no-op, so a normal swipe
    // does not restart the pager's scrollable.
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
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
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed,
                // Neighbour is composed by the pager prefetcher while the finger moves.
                // Do not also subscribe to scroll state here: currentPage flips at the
                // halfway point of a drag and would restart pointerInput under the finger.
                beyondBoundsPageCount = 0,
                key = { pages.getOrNull(it) ?: it }
            ) { pageIndex ->
                val pageUrl = pages.getOrNull(pageIndex)
                if (pageUrl == null) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                } else {
                    ZoomableImageContainer(
                        imageUrl = pageUrl,
                        onZoomedChanged = { zoomed ->
                            // Read at event time. A value captured when this page was
                            // composed goes stale once the swipe crosses the snap point.
                            if (pagerState.currentPage == pageIndex) {
                                isZoomed = zoomed
                            }
                        },
                        onTapDismiss = onDismiss
                    )
                }
            }

            // Close button overlay
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close viewer",
                    tint = Color.White
                )
            }

            // Download button overlay
            val currentImageUrl = pages.getOrNull(pagerState.currentPage)
            if (currentImageUrl != null) {
                IconButton(
                    onClick = {
                        com.mioo.dao.utils.ImageDownloader.downloadImage(
                            context = context,
                            imageUrl = currentImageUrl,
                            scope = scope
                        )
                    },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 16.dp, end = 72.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Image",
                        tint = Color.White
                    )
                }
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
        }
    }
}

@Composable
private fun ZoomableImageContainer(
    imageUrl: String,
    onZoomedChanged: (Boolean) -> Unit,
    onTapDismiss: () -> Unit
) {
    // Float states read inside graphicsLayer { } only invalidate draw, not composition/layout
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUrl) { mutableFloatStateOf(0f) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    var failed by remember(imageUrl) { mutableStateOf(false) }
    val context = LocalContext.current
    val onZoomedChangedState = rememberUpdatedState(onZoomedChanged)
    val onTapDismissState = rememberUpdatedState(onTapDismiss)

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
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            applyScale(1f)
                        } else {
                            applyScale(3f)
                        }
                    },
                    onTap = {
                        onTapDismissState.value()
                    }
                )
            }
            .pointerInput(imageUrl) {
                detectZoomableTransformGestures(
                    onGesture = { pan, zoom ->
                        if (zoom.isFinite() && zoom > 0f && pan.x.isFinite() && pan.y.isFinite()) {
                            val clamped = (scale * zoom).coerceIn(1f, 5f)
                            val wasZoomed = scale > 1.01f
                            scale = clamped
                            if (clamped > 1.01f) {
                                val nextX = offsetX + pan.x
                                val nextY = offsetY + pan.y
                                offsetX = if (nextX.isFinite()) nextX else 0f
                                offsetY = if (nextY.isFinite()) nextY else 0f
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
        AsyncImage(
            model = imageRequest,
            contentDescription = "Zoomable Image",
            contentScale = ContentScale.Fit,
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
                failed = state is AsyncImagePainter.State.Error
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val safeScale = if (scale.isFinite()) scale.coerceIn(1f, 5f) else 1f
                    scaleX = safeScale
                    scaleY = safeScale
                    translationX = if (offsetX.isFinite()) offsetX else 0f
                    translationY = if (offsetY.isFinite()) offsetY else 0f
                }
        )

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
