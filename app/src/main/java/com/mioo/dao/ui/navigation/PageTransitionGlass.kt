package com.mioo.dao.ui.navigation

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mioo.dao.ui.components.GlassStyle
import com.mioo.dao.ui.theme.DaoTheme
import com.mioo.dao.ui.theme.MiooMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Which page is moving, and which page sits underneath it, for the current slide.
 * Cleared when the slide duration ends so settled screens keep their own background.
 */
@Stable
internal class PageTransitionGlassState {
    var slidingEntryId by mutableStateOf<String?>(null)
        private set
    var coveredEntryId by mutableStateOf<String?>(null)
        private set

    fun begin(slidingId: String, coveredId: String?) {
        slidingEntryId = slidingId
        coveredEntryId = coveredId
    }

    fun end(slidingId: String) {
        if (slidingEntryId == slidingId) {
            slidingEntryId = null
            coveredEntryId = null
        }
    }

    fun clear() {
        slidingEntryId = null
        coveredEntryId = null
    }
}

@Composable
internal fun rememberPageTransitionGlass(
    navController: NavHostController,
    reducedMotion: Boolean
): PageTransitionGlassState {
    val state = remember { PageTransitionGlassState() }
    val context = LocalContext.current
    // Compose scales tweens by animator duration scale. delay() does not.
    val durationMillis = remember(context) {
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Exception) {
            1f
        }
        (MiooMotion.PageDurationMillis * scale).toLong()
    }
    LaunchedEffect(navController, reducedMotion, durationMillis) {
        if (reducedMotion || durationMillis <= 0L) {
            state.clear()
            return@LaunchedEffect
        }
        var previousIds = emptyList<String>()
        var job: Job? = null
        navController.currentBackStack.collect { stack ->
            val ids = stack.map { it.id }
            if (previousIds.isEmpty()) {
                previousIds = ids
                return@collect
            }
            val removed = previousIds.lastOrNull { it !in ids }
            val added = ids.lastOrNull { it !in previousIds }
            val snapshot = previousIds
            previousIds = ids
            val slidingId: String
            val coveredId: String?
            when {
                // Push or replace: the new page slides in over the one leaving.
                added != null -> {
                    slidingId = added
                    coveredId = removed ?: snapshot.lastOrNull()
                }
                // Pop: the leaving page moves. Blur the page it reveals.
                removed != null -> {
                    slidingId = removed
                    coveredId = ids.lastOrNull()
                }
                else -> return@collect
            }
            job?.cancel()
            job = launch {
                state.begin(slidingId, coveredId)
                delay(durationMillis)
                state.end(slidingId)
            }
        }
    }
    return state
}

/**
 * Frosted glass behind every page.
 *
 * Scaffolds are transparent, so a slide otherwise shows the previous page
 * straight through the gaps. The tint is always in the composition: the
 * leaving page does not reliably recompose after a pop, so a scrim that
 * appears only when the transition starts never makes it onto that frame.
 * Over the normal backdrop the tint matches the page background. Over the
 * previous page it reads as glass. On API 31+ that page is recorded once and
 * the blur runs on that frozen image, so the list underneath is not redrawn
 * on every animation frame.
 */
@Composable
internal fun TransitionGlassPage(
    entryId: String,
    state: PageTransitionGlassState,
    content: @Composable () -> Unit
) {
    val glassOn = DaoTheme.glassEffectEnabled
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val sliding = state.slidingEntryId == entryId
    val covered = glassOn && canBlur && state.coveredEntryId == entryId
    val blurredBackdrop = glassOn && canBlur && state.coveredEntryId != null
    Box(
        modifier = Modifier.fillMaxSize(),
        propagateMinConstraints = true
    ) {
        TransitionGlassScrim(
            glassOn = glassOn,
            blurredBackdrop = blurredBackdrop,
            showEdge = sliding
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (covered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.frozenPageBlur()
                    } else {
                        Modifier
                    }
                ),
            propagateMinConstraints = true
        ) {
            content()
        }
    }
}

private class FrostSnapshot {
    var node: RenderNode? = null
    var failed: Boolean = false
    var disposed: Boolean = false
    var liveBlurPosted: Boolean = false
}

private class RecordedFrost(
    val node: RenderNode?,
    val drewContent: Boolean,
    val failed: Boolean
)

private val frostMainHandler = Handler(Looper.getMainLooper())

/**
 * Records the page once into a [RenderNode] and blurs that recording.
 * [content] stays composed so list scroll state survives, but after the
 * first frame it is not drawn again until the transition ends.
 * If recording fails, falls back to a live blur for this one slide.
 * The fallback flag is posted out of the draw pass. Compose rejects snapshot
 * writes that happen while a frame is being drawn.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun Modifier.frozenPageBlur(): Modifier {
    val holder = remember { FrostSnapshot() }
    var useLiveBlur by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            holder.disposed = true
            holder.node?.discardDisplayList()
            holder.node = null
        }
    }
    val recorded = this.drawWithContent {
        val node = holder.node
        if (node != null) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRenderNode(node)
            }
            return@drawWithContent
        }
        if (holder.failed) {
            drawContent()
            return@drawWithContent
        }
        val recordedFrost = recordBlurredPage(16.dp.toPx())
        val created = recordedFrost.node
        if (created != null) {
            holder.node = created
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRenderNode(created)
            }
        } else if (recordedFrost.failed) {
            holder.failed = true
            requestLiveBlur(holder) { useLiveBlur = true }
            if (!recordedFrost.drewContent) drawContent()
        } else {
            drawContent()
        }
    }
    return if (useLiveBlur) recorded.blur(16.dp) else recorded
}

private fun requestLiveBlur(holder: FrostSnapshot, onFail: () -> Unit) {
    if (holder.liveBlurPosted) return
    holder.liveBlurPosted = true
    frostMainHandler.post {
        if (!holder.disposed) onFail()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun ContentDrawScope.recordBlurredPage(radiusPx: Float): RecordedFrost {
    val width = size.width.toInt()
    val height = size.height.toInt()
    if (width <= 0 || height <= 0 || radiusPx <= 0f) {
        return RecordedFrost(node = null, drewContent = false, failed = false)
    }
    val node = RenderNode("page-frost")
    node.setPosition(0, 0, width, height)
    val previous = drawContext.canvas
    var began = false
    var drewContent = false
    var ok = false
    try {
        val recording = node.beginRecording()
        began = true
        // drawContent() reads the canvas at entry, so the swap has to happen first.
        drawContext.canvas = Canvas(recording)
        drewContent = true
        drawContent()
        ok = true
    } catch (_: Throwable) {
        // ok stays false; the recording is discarded below.
    } finally {
        drawContext.canvas = previous
        if (began) {
            runCatching { node.endRecording() }
        }
    }
    if (!ok) {
        node.discardDisplayList()
        return RecordedFrost(node = null, drewContent = drewContent, failed = true)
    }
    return try {
        node.setRenderEffect(
            RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
        )
        RecordedFrost(node = node, drewContent = true, failed = false)
    } catch (_: Throwable) {
        node.discardDisplayList()
        RecordedFrost(node = null, drewContent = true, failed = true)
    }
}

@Composable
private fun TransitionGlassScrim(
    glassOn: Boolean,
    blurredBackdrop: Boolean,
    showEdge: Boolean
) {
    val background = MaterialTheme.colorScheme.background
    val isDark = background.luminance() < 0.5f
    val fill = if (!glassOn) {
        background
    } else {
        // Dense enough to hide sharp text when the backdrop cannot blur.
        val alpha = if (blurredBackdrop) 0.72f else 0.96f
        background.copy(alpha = alpha)
    }
    val edge = if (showEdge && glassOn) {
        Color.White.copy(alpha = if (isDark) 0.16f else 0.45f)
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fill)
            .then(
                if (glassOn && (blurredBackdrop || showEdge)) {
                    Modifier.background(
                        if (isDark) GlassStyle.darkGradient else GlassStyle.lightGradient
                    )
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = edge,
                    start = Offset(stroke / 2f, 0f),
                    end = Offset(stroke / 2f, size.height),
                    strokeWidth = stroke
                )
            }
    )
}
