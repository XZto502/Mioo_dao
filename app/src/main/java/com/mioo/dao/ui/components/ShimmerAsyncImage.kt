package com.mioo.dao.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.mioo.dao.ui.theme.MiooMotion
import com.mioo.dao.ui.theme.isReducedMotionEnabled
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size

/**
 * Shared list-thumbnail decode size (px). Keep prefetch + card requests aligned
 * so Coil memory hits instead of re-decoding.
 */
object ListThumbImage {
    /**
     * Decode size for list thumbs (UI is 120.dp ≈ 360px @3x).
     * Use 288 so decode/upload is cheaper while still sharp on xxhdpi; Coil INEXACT keeps cache hits stable.
     */
    const val SIZE_PX: Int = 288

    fun request(context: Context, imageUrl: String): ImageRequest {
        return ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size(SIZE_PX, SIZE_PX))
            .precision(Precision.INEXACT)
            .memoryCacheKey(imageUrl)
            .diskCacheKey(imageUrl)
            .crossfade(false)
            .allowHardware(true)
            .build()
    }
}

/**
 * Lightweight list thumbnail: [AsyncImage] + static placeholder while loading.
 * Avoids Subcompose overhead and infinite shimmer work for every visible cell.
 */
@Composable
fun ListThumbAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    preserveRatio: Boolean = false
) {
    var ratio by remember(imageUrl) { mutableStateOf(1.2f) }
    val frame = if (preserveRatio) modifier.previewFrame(ratio, 190.dp, 230.dp) else modifier
    val dataSaver = com.mioo.dao.ui.theme.LocalDataSaverEnabled.current
    var manualLoaded by rememberSaveable(imageUrl) { mutableStateOf(false) }
    if (dataSaver && !manualLoaded) {
        // 省流量模式：占位不加载，点击才拉这一张
        Box(
            modifier = frame
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable { manualLoaded = true },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "点击加载图片",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "点击加载",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return@ListThumbAsyncImage
    }
    val context = LocalContext.current
    val listUrl = remember(imageUrl) { IslandImage.listUrl(imageUrl) }
    val fullUrl = remember(imageUrl) { IslandImage.fullSize(imageUrl) }
    var useFull by remember(imageUrl) { mutableStateOf(listUrl == fullUrl) }
    val currentUrl = if (useFull) fullUrl else listUrl
    val request = remember(currentUrl) {
        ListThumbImage.request(context, currentUrl)
    }

    Box(modifier = frame.background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = if (preserveRatio && ratio >= 0.45f) ContentScale.Fit else contentScale,
            alignment = if (preserveRatio && ratio < 0.45f) Alignment.TopCenter else Alignment.Center,
            modifier = Modifier.matchParentSize(),
            onSuccess = { if (preserveRatio) ratio = it.result.drawable.previewRatio() },
            onError = {
                if (!useFull) useFull = true
            }
        )
    }
}

private fun android.graphics.drawable.Drawable.previewRatio(): Float =
    if (intrinsicWidth > 0 && intrinsicHeight > 0) intrinsicWidth.toFloat() / intrinsicHeight else 1.2f

@Composable
private fun Modifier.previewFrame(ratio: Float, maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp): Modifier {
    val boundedRatio = ratio.coerceIn(0.45f, 2.4f)
    val shape = RoundedCornerShape(10.dp)
    return widthIn(max = minOf(maxWidth, maxHeight * boundedRatio))
        .aspectRatio(boundedRatio)
        .clip(shape)
        .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape)
}

@Composable
fun LocalImagePreview(
    model: Any,
    modifier: Modifier = Modifier,
    attachment: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    var ratio by remember(model) { mutableStateOf(1.2f) }
    val maxWidth = if (attachment) 132.dp else 190.dp
    val maxHeight = if (attachment) 112.dp else 230.dp
    Box(modifier.previewFrame(ratio, maxWidth, maxHeight)) {
        AsyncImage(model, "附图预览",
            modifier = Modifier.matchParentSize().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentScale = if (ratio < 0.45f) ContentScale.Crop else ContentScale.Fit,
            alignment = if (ratio < 0.45f) Alignment.TopCenter else Alignment.Center,
            onSuccess = { ratio = it.result.drawable.previewRatio() })
        if (onRemove != null) {
            Surface(onClick = onRemove, shape = CircleShape, color = Color.Black.copy(alpha = 0.62f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, "移除图片", Modifier.size(13.dp), tint = Color.White)
                }
            }
        }
    }
}

/**
 * Optional shimmer for non-list surfaces (dialogs / one-off previews).
 * Animation only runs while the image is in [AsyncImagePainter.State.Loading].
 */
@Composable
fun ShimmerAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                ShimmerPlaceholder(modifier = Modifier.matchParentSize())
            }
            else -> {
                SubcomposeAsyncImageContent()
            }
        }
    }
}

@Composable
private fun ShimmerPlaceholder(modifier: Modifier = Modifier) {
    val reducedMotion = isReducedMotionEnabled()
    // Continuous shimmer uses linear (correct for perpetual motion).
    // Faster cycle (~900ms) makes loading feel snappier than a slow sweep.
    if (reducedMotion) {
        Box(modifier = modifier.background(Color(0xFFE4E4E7)))
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MiooMotion.DurationShimmer,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    // drawWithCache rebuilds the gradient brush only when size/animation value requires it
    Box(
        modifier = modifier.drawWithCache {
            val brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset.Zero,
                end = Offset(x = translateAnim, y = translateAnim)
            )
            onDrawBehind {
                drawRect(brush)
            }
        }
    )
}

private val shimmerColors = listOf(
    Color(0xFFE4E4E7),
    Color(0xFFF4F4F5),
    Color(0xFFE4E4E7)
)
