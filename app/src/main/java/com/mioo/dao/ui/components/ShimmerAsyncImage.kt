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
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    const val SIZE_PX: Int = 360

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
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val request = remember(imageUrl) {
        ListThumbImage.request(context, imageUrl)
    }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }

    Box(modifier = modifier.background(Color(0xFFE8E8ED))) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.matchParentSize(),
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
            }
        )
        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFE4E4E7))
            )
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
