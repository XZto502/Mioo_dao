package com.mioo.dao.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

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
    var currentScale by remember { mutableStateOf(1f) }

    // Reset zoom scale when page changes
    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
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
                userScrollEnabled = currentScale == 1f
            ) { pageIndex ->
                val imageUrl = imageUrls.getOrNull(pageIndex) ?: return@HorizontalPager
                val isCurrentPage = pagerState.currentPage == pageIndex
                
                ZoomableImageContainer(
                    imageUrl = imageUrl,
                    isActive = isCurrentPage,
                    onScaleChanged = { scale ->
                        if (isCurrentPage) {
                            currentScale = scale
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
    onScaleChanged: (Float) -> Unit,
    onTapDismiss: () -> Unit
) {
    var scale by remember(isActive) { mutableStateOf(1f) }
    var offset by remember(isActive) { mutableStateOf(Offset.Zero) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Notify parent scale changes
    LaunchedEffect(scale) {
        onScaleChanged(scale)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 3f
                            offset = Offset.Zero
                        }
                    },
                    onTap = {
                        onTapDismiss()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val imageRequest = remember(imageUrl) {
            coil.request.ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .allowHardware(true)
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
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )

        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White
            )
        }
    }
}
