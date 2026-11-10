package com.mioo.dao.ui.components

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Effective IME bottom offset in px: max(WindowInsets.ime, visible-frame fallback).
 *
 * Some OEM / emulator builds never dispatch IME insets to edge-to-edge windows
 * (source stays invisible with an empty frame), which silently breaks imePadding().
 * The fallback measures the keyboard with the classic getWindowVisibleDisplayFrame
 * trick, latched when it exceeds 25% of the screen so navigation-bar residue
 * never counts as a keyboard.
 *
 * Inside a Dialog window the fallback alone under-reports (~90px): the dialog's
 * own rootView is resized by adjustResize, and visibleFrame.bottom lags the real
 * keyboard top. So the listener ALSO samples the host Activity's decor view
 * (never resized, always reports the true keyboard top) and takes the max of
 * both measurements.
 */
@Composable
fun rememberEffectiveImeBottomPx(): Int {
    val view = LocalView.current
    val density = LocalDensity.current
    val insetsIme = WindowInsets.ime.getBottom(density)

    var fallback by remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view) {
        // Host activity decor: unwrap ContextThemeWrapper chains (dialogs wrap).
        var ctx: android.content.Context? = view.context
        var activity: android.app.Activity? = null
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                activity = ctx
                break
            }
            ctx = ctx.baseContext
        }
        val activityDecor = activity?.window?.decorView

        fun measure(root: android.view.View, screenH: Int): Int {
            val frame = Rect()
            root.getWindowVisibleDisplayFrame(frame)
            val covered = (screenH - frame.bottom).coerceAtLeast(0)
            return if (covered > screenH * 25 / 100) covered else 0
        }

        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val metrics = view.resources.displayMetrics
            val screenH = metrics.heightPixels
            if (screenH <= 0) return@OnGlobalLayoutListener
            val own = measure(view.rootView, screenH)
            val host = activityDecor?.let { measure(it, screenH) } ?: 0
            fallback = maxOf(own, host)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        activityDecor?.viewTreeObserver?.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            val observer = view.viewTreeObserver
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
            activityDecor?.viewTreeObserver?.let { o ->
                if (o.isAlive) o.removeOnGlobalLayoutListener(listener)
            }
        }
    }

    return maxOf(insetsIme, fallback)
}

/**
 * Composer inset: keyboard-height bottom padding while the keyboard is open,
 * otherwise [navigationBarsPadding].
 *
 * The keyboard covers the gesture pill, so the two paddings are never stacked.
 * Uses [rememberEffectiveImeBottomPx] so the lift also works where IME insets
 * dispatch is broken. Pair with `windowSoftInputMode="adjustNothing"`.
 *
 * 抬升量用动画抹平：insets 失派发的设备上兜底值来自 GlobalLayout，
 * 会整帧跳变（键盘还在半途、底栏已瞬移到终点，看起来像"飞起来"）。
 */
fun Modifier.imePaddingAndNavigationBars(): Modifier = composed {
    val density = LocalDensity.current
    val imeBottom = rememberEffectiveImeBottomPx()
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val liftTarget = with(density) { maxOf(imeBottom, navBottom).toDp() }
    val lift by animateDpAsState(
        targetValue = liftTarget,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "imeLiftPadding"
    )
    Modifier.padding(bottom = lift)
}

/** @see imePaddingAndNavigationBars */
fun Modifier.imeLiftOverNavigationBars(): Modifier = imePaddingAndNavigationBars()

fun Modifier.navBarContentPadding(): Modifier = this.navigationBarsPadding()

/** Convenience: keyboard visible by effective height (ignores the gesture-pill band). */
@Composable
fun rememberImeVisible(): Boolean = rememberEffectiveImeBottomPx() > 0

/** Both input surfaces share one slot; focus handoff must not collapse it first. */
@androidx.compose.runtime.Stable
class ComposerInputPanelState {
    var expanded by androidx.compose.runtime.mutableStateOf(false)
        private set
    internal var awaitingKeyboard by androidx.compose.runtime.mutableStateOf(false)
    internal var editing by androidx.compose.runtime.mutableStateOf(false)

    fun toggle() {
        awaitingKeyboard = false
        expanded = !expanded
    }

    fun close() {
        awaitingKeyboard = false
        expanded = false
    }

    fun onEditorFocused() {
        if (expanded) {
            awaitingKeyboard = true
            expanded = false
        }
    }
}

@Composable
fun rememberComposerInputPanelState(): ComposerInputPanelState {
    val view = LocalView.current
    DisposableEffect(view) {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) {
            context = context.baseContext
        }
        val window = (context as? android.app.Activity)?.window
        val previousMode = window?.attributes?.softInputMode
        // Edge-to-edge keeps the root full size while adjustResize enables animated
        // IME insets on older Android/OEM builds. Only our slot applies the inset.
        window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (previousMode != null) window?.setSoftInputMode(previousMode) }
    }
    return remember { ComposerInputPanelState() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposerInputPanelSlot(state: ComposerInputPanelState, onInsert: (String) -> Unit) {
    val density = LocalDensity.current
    val nav = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density)
    val ime = WindowInsets.ime
    val imeTarget = WindowInsets.imeAnimationTarget
    val reducedMotion = com.mioo.dao.ui.theme.isReducedMotionEnabled()
    val panelMotion = tween<Float>(if (reducedMotion) 0 else 200, easing = FastOutSlowInEasing)
    val defaultHeight = with(density) { 280.dp.roundToPx() } + nav
    var keyboardHeight by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(defaultHeight) }
    val reserve = remember { androidx.compose.animation.core.Animatable(nav.toFloat()) }
    var panelMounted by remember { androidx.compose.runtime.mutableStateOf(false) }
    var handoffSawKeyboard by remember(state.awaitingKeyboard) {
        androidx.compose.runtime.mutableStateOf(false)
    }

    LaunchedEffect(ime, imeTarget, density, nav, state.expanded, state.editing, state.awaitingKeyboard) {
        if (state.expanded || state.editing) return@LaunchedEffect
        snapshotFlow { ime.getBottom(density) to imeTarget.getBottom(density) }.collectLatest { (height, target) ->
            if (state.awaitingKeyboard) {
                if (target > nav) handoffSawKeyboard = true
                if (handoffSawKeyboard && target <= nav) {
                    // The user closed the IME before the handoff settled.
                    if (height <= nav) state.awaitingKeyboard = false
                    return@collectLatest
                }
            }
            if (height <= nav || height != target) return@collectLatest
            // The IME can change its outer height again as its toolbar settles.
            delay(100)
            keyboardHeight = height
            if (state.awaitingKeyboard) {
                if (reserve.value.toInt() != height) {
                    reserve.animateTo(height.toFloat(), panelMotion)
                }
                state.awaitingKeyboard = false
            }
        }
    }
    LaunchedEffect(state.awaitingKeyboard) {
        if (state.awaitingKeyboard) {
            // A hardware keyboard or rejected show request must not leave an empty slot.
            delay(1500)
            if (ime.getBottom(density) <= nav) {
                reserve.animateTo(nav.toFloat(), panelMotion)
            }
            state.awaitingKeyboard = false
        }
    }
    LaunchedEffect(state.expanded, state.awaitingKeyboard) {
        when {
            state.expanded -> {
                val currentIme = ime.getBottom(density)
                panelHeight = keyboardHeight.takeIf { it > nav }
                    ?: currentIme.takeIf { it > nav }
                    ?: defaultHeight
                if (currentIme > nav) reserve.snapTo(currentIme.toFloat())
                panelMounted = true
                reserve.animateTo(panelHeight.toFloat(), panelMotion)
            }
            state.awaitingKeyboard -> {
                // Keep the existing slot height throughout the IME handoff. A transient
                // IME inset must not move the editor up and then back down.
                panelMounted = false
            }
            else -> {
                reserve.animateTo(nav.toFloat(), panelMotion)
                panelMounted = false
            }
        }
    }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
            val reserved = reserve.value.toInt()
            val occupied = when {
                state.awaitingKeyboard && handoffSawKeyboard && imeTarget.getBottom(density) <= nav ->
                    minOf(reserved, ime.getBottom(density))
                state.awaitingKeyboard || state.editing || (state.expanded && panelMounted) -> reserved
                state.expanded || panelMounted -> maxOf(reserved, ime.getBottom(density))
                else -> ime.getBottom(density)
            }
            val height = maxOf(nav, occupied).coerceIn(constraints.minHeight, constraints.maxHeight)
            val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            layout(placeable.width, height) { placeable.placeRelative(0, 0) }
        }
    ) {
        if (panelMounted || state.expanded) {
            KaomojiQuickPanel(
                onInsert = onInsert,
                onEditingChange = { state.editing = it },
                heightDp = with(density) { (panelHeight - nav).coerceAtLeast(0).toDp().value.toInt() },
                modifier = Modifier.wrapContentHeight(androidx.compose.ui.Alignment.Top, unbounded = true)
                    .navigationBarsPadding()
            )
        }
    }
}
