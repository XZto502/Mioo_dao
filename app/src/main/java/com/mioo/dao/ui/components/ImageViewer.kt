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
import androidx.compose.ui.window.DialogProperties
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

    // Find initial page index
    val initialPage = remember(initialImageUrl, imageUrls) {
        val idx = imageUrls.indexOf(initialImageUrl)
        if (idx == -1) 0 else idx
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { imageUrls.size }
    // Boolean only: continuous scale floats must not recompose the pager shell every frame
    var isZoomed by remember { mutableStateOf(false) }

    // Reset zoom lock when page changes
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed,
                // Do not pre-compose far pages — only the settled page (+ swipe peer via layout)
                beyondBoundsPageCount = 0
            ) { pageIndex ->
                val pageUrl = imageUrls.getOrNull(pageIndex) ?: return@HorizontalPager
                val currentPage = pagerState.currentPage
                val isCurrentPage = currentPage == pageIndex
                // Decode only current page; allow ±1 while finger is dragging for swipe preview
                val distance = abs(pageIndex - currentPage)
                val allowDecode = distance == 0 ||
                    (pagerState.isScrollInProgress && distance <= 1)

                ZoomableImageContainer(
                    imageUrl = pageUrl,
                    isActive = isCurrentPage,
                    allowDecode = allowDecode,
                    onZoomedChanged = { zoomed ->
                        if (isCurrentPage) {
                            isZoomed = zoomed
                        }
                    },
                    onTapDismiss = onDismiss
                )
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
            val currentImageUrl = imageUrls.getOrNull(pagerState.currentPage)
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
            if (imageUrls.size > 1) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 22.dp)
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
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
    isActive: Boolean,
    allowDecode: Boolean,
    onZoomedChanged: (Boolean) -> Unit,
    onTapDismiss: () -> Unit
) {
    // Float states read inside graphicsLayer { } only invalidate draw, not composition/layout
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageUrl) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageUrl) { mutableFloatStateOf(0f) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    val context = LocalContext.current
    val onZoomedChangedState = rememberUpdatedState(onZoomedChanged)
    val onTapDismissState = rememberUpdatedState(onTapDismiss)

    // Reset transform when this page becomes inactive (pager moved away)
    LaunchedEffect(isActive, imageUrl) {
        if (!isActive) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            onZoomedChangedState.value(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isActive, imageUrl) {
                fun applyScale(newScale: Float, pan: Offset = Offset.Zero) {
                    val clamped = newScale.coerceIn(1f, 5f)
                    val wasZoomed = scale > 1.01f
                    scale = clamped
                    if (clamped > 1.01f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
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
            .pointerInput(isActive, imageUrl) {
                detectZoomableTransformGestures(
                    onGesture = { pan, zoom ->
                        val clamped = (scale * zoom).coerceIn(1f, 5f)
                        val wasZoomed = scale > 1.01f
                        scale = clamped
                        if (clamped > 1.01f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                        val nowZoomed = clamped > 1.01f
                        if (nowZoomed != wasZoomed) {
                            onZoomedChangedState.value(nowZoomed)
                        }
                    },
                    canConsume = { scale > 1.01f }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (!allowDecode) {
            // Off-screen: no Coil request / no bitmap decode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
            return@Box
        }

        // Cap decode size to ~2x screen to keep zoom sharp without full-res OOM risk
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val maxPx = remember(configuration, density) {
            val w = with(density) { configuration.screenWidthDp.dp.roundToPx() }
            val h = with(density) { configuration.screenHeightDp.dp.roundToPx() }
            // 2× screen for pinch zoom headroom, hard cap 4096
            (maxOf(w, h) * 2).coerceIn(1080, 4096)
        }
        val imageRequest = remember(imageUrl, maxPx) {
            coil.request.ImageRequest.Builder(context)
                .data(imageUrl)
                .size(Size(maxPx, maxPx))
                .precision(Precision.INEXACT)
                .crossfade(false)
                .allowHardware(true)
                .memoryCacheKey(imageUrl)
                .diskCacheKey(imageUrl)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = "Zoomable Image",
            contentScale = ContentScale.Fit,
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Snapshot reads here skip composition — only re-draw the layer
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )

        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectZoomableTransformGestures(
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    canConsume: () -> Boolean
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(false)
        do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.size
            val isZoomingOrPanned = canConsume() || pointerCount > 1
            val canceled = event.changes.any { it.isConsumed } && !isZoomingOrPanned

            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = kotlin.math.abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(panChange, zoomChange)
                    }
                    if (isZoomingOrPanned) {
                        event.changes.forEach {
                            if (it.position != it.previousPosition) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}
