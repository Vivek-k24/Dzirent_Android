package com.dzirent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dzirent.app.ui.theme.DzirentAccent
import com.dzirent.app.ui.theme.OnDzirentAccent
import com.dzirent.app.util.GuideStep
import com.dzirent.app.util.GuideTarget
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where each guide target is on screen, reported by the real UI elements. */
@Stable
class GuideTargets {
    val bounds = mutableStateMapOf<GuideTarget, Rect>()
}

val LocalGuideTargets = staticCompositionLocalOf { GuideTargets() }

/** Reports this element's position so the guide can point at it. */
@Composable
fun Modifier.guideTarget(target: GuideTarget): Modifier {
    val targets = LocalGuideTargets.current
    return onGloballyPositioned { targets.bounds[target] = it.boundsInRoot() }
}

private val ScrimColor = Color.Black.copy(alpha = 0.65f)

/**
 * Dims the whole screen except the current step's element and shows a bubble next to it
 * with step dots, Skip and Next ("Done" on the last step). Touches don't reach the app
 * underneath, and Back means Skip.
 */
@Composable
fun GuideOverlay(steps: List<GuideStep>, targets: GuideTargets, onFinish: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val isLast = index >= steps.lastIndex

    BackHandler(onBack = onFinish)

    // Root coordinates of the target, in this overlay's coordinates, with some breathing room.
    val hole = targets.bounds[step.target]?.translate(-origin)?.inflate(with(density) { 6.dp.toPx() })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawBehind {
                drawRect(ScrimColor)
                // Kept on screen; a pill or circle, so round buttons get a round cutout.
                val cut = hole?.intersect(Rect(Offset.Zero, size))
                if (cut != null && !cut.isEmpty) {
                    val radius = minOf(cut.width, cut.height) / 2
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = cut.topLeft,
                        size = cut.size,
                        cornerRadius = CornerRadius(radius),
                        blendMode = BlendMode.Clear
                    )
                }
            }
            .semantics { paneTitle = "App tour" }
    ) {
        BubbleLayout(hole = hole) {
            GuideBubble(
                text = step.text,
                stepIndex = index,
                stepCount = steps.size,
                isLast = isLast,
                onSkip = onFinish,
                onNext = { if (isLast) onFinish() else index++ }
            )
        }
    }
}

/**
 * Puts the bubble below the hole or above it, whichever has more room, kept inside the
 * safe area. Without a hole (target not on screen yet) it goes in the middle.
 */
@Composable
private fun BubbleLayout(hole: Rect?, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = 16.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val top = insets.getTop(this) + margin
        val bottom = height - insets.getBottom(this) - margin
        val maxWidth = min(width - 2 * margin, 360.dp.roundToPx()).coerceAtLeast(0)

        val below = hole == null || (bottom - hole.bottom) >= (hole.top - top)
        val available = when {
            hole == null -> bottom - top
            below -> bottom - (hole.bottom.roundToInt() + gap)
            else -> (hole.top.roundToInt() - gap) - top
        }
        val placeable = measurables.first().measure(
            Constraints(maxWidth = maxWidth, maxHeight = max(available, 120.dp.roundToPx()))
        )
        val x = if (hole == null) (width - placeable.width) / 2
        else (hole.center.x.roundToInt() - placeable.width / 2).coerceIn(margin, max(margin, width - margin - placeable.width))
        val y = when {
            hole == null -> top + (bottom - top - placeable.height) / 2
            below -> hole.bottom.roundToInt() + gap
            else -> hole.top.roundToInt() - gap - placeable.height
        }.coerceIn(top, max(top, bottom - placeable.height))
        layout(width, height) { placeable.place(x, y) }
    }
}

@Composable
private fun GuideBubble(
    text: String,
    stepIndex: Int,
    stepCount: Int,
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // The text scrolls if a huge font leaves little room; the buttons always show.
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Step ${stepIndex + 1} of $stepCount"
                }
            ) {
                repeat(stepCount) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (i == stepIndex) DzirentAccent else MaterialTheme.colorScheme.outline)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Own row under the dots, so Skip and Next fit on narrow phones with large text.
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onSkip) { Text("Skip") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = DzirentAccent, contentColor = OnDzirentAccent)
                ) {
                    Text(if (isLast) "Done" else "Next")
                }
            }
        }
    }
}
