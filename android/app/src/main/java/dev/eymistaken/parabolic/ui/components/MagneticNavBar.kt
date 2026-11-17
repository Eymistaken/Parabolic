package dev.eymistaken.parabolic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

data class NavDestination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)


/**
 * Navigation for the floating toolbar, built on the Jetsnack bottom-bar technique
 * (android/compose-samples): the row has a *fixed* width split into n + 1 slots,
 * the selected item owns two of them, and every item's share is driven by one
 * spring per item that all run with the same spec. Because the old item's
 * fraction falls exactly as fast as the new one's rises, the shares always sum
 * to one, so the toolbar never changes width and never shifts on screen.
 *
 * On top of that the selection pill is "magnetic": its two edges are separate
 * springs. The edge facing the direction of travel snaps over on the fast
 * spatial token while the far edge lets go on the default one, so the pill
 * stretches toward the new tab, thins out a little while stretched (keeping its
 * volume), then contracts onto it.
 *
 * Springs follow m3.material.io/styles/motion: spatial tokens (which may
 * overshoot) for position and size, effects-style clamped values for color and
 * opacity, and "fast" because this is a small component. Animatable retargets
 * from its current value and velocity, so rapid taps never jump.
 */
@Composable
fun MagneticNavBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    unselectedSlot: Dp = 56.dp,
    height: Dp = 56.dp,
) {
    val count = destinations.size
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLargeEmphasized
    val fastSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val defaultSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // The selected slot is as wide as the longest "icon + label", so no label
    // is ever cut and the bar width is a constant.
    val measurer = rememberTextMeasurer()
    val selectedSlot: Dp = remember(destinations, labelStyle, density) {
        val widest = destinations.maxOf { measurer.measure(it.label, labelStyle).size.width }
        with(density) { (widest.toDp() + 24.dp + 8.dp + 36.dp) }
    }.coerceAtLeast(unselectedSlot * 2)
    val totalWidth = unselectedSlot * (count - 1) + selectedSlot

    // One fraction per item, identical spec -> fractions always sum to 1.
    val fractions = remember(count) { List(count) { Animatable(if (it == selectedIndex) 1f else 0f) } }
    // The pill's edges, in slot units: left edge sits at index * unselected slot.
    val leftEdge = remember { Animatable(selectedIndex.toFloat()) }
    val rightEdge = remember { Animatable(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex) {
        val target = selectedIndex.toFloat()
        val movingRight = target > (leftEdge.value + rightEdge.value) / 2f
        coroutineScope {
            fractions.forEachIndexed { index, fraction ->
                launch { fraction.animateTo(if (index == selectedIndex) 1f else 0f, fastSpatial) }
            }
            launch { leftEdge.animateTo(target, if (movingRight) defaultSpatial else fastSpatial) }
            launch { rightEdge.animateTo(target, if (movingRight) fastSpatial else defaultSpatial) }
        }
    }

    Layout(
        modifier = modifier
            .width(totalWidth)
            .height(height),
        content = {
            Canvas(modifier = Modifier.layoutId("indicator").fillMaxSize()) {
                val unit = unselectedSlot.roundToPx().toFloat()
                // Derived from the width the bar really got, not the requested
                // one, so the last tab's pill ends exactly at the bar's edge.
                val baseWidth = size.width - unit * (count - 1)
                // An overshooting edge meets the bar's end like a wall: the pill
                // squashes against it instead of being clipped.
                var left = (leftEdge.value * unit).coerceIn(0f, size.width)
                var right = (rightEdge.value * unit + baseWidth).coerceIn(0f, size.width)
                if (right - left < size.height) {
                    val mid = ((left + right) / 2f).coerceIn(size.height / 2f, size.width - size.height / 2f)
                    left = mid - size.height / 2f
                    right = mid + size.height / 2f
                }
                // Stretch -> thinner, like a blob keeping its volume.
                val stretch = ((right - left) / baseWidth - 1f).coerceIn(0f, 1.5f)
                val pillHeight = size.height * (1f - 0.16f * stretch)
                val top = (size.height - pillHeight) / 2f
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, pillHeight),
                    cornerRadius = CornerRadius(pillHeight / 2f),
                )
            }
            destinations.forEachIndexed { index, destination ->
                val fraction = fractions[index].value.coerceIn(0f, 1f)
                NavItem(
                    destination = destination,
                    fraction = fraction,
                    selected = index == selectedIndex,
                    tint = lerp(contentColor, selectedContentColor, fraction),
                    onClick = {
                        if (index != selectedIndex) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                    },
                    modifier = Modifier.layoutId("item"),
                )
            }
        },
    ) { measurables, constraints ->
        val unit = unselectedSlot.roundToPx()
        val fullWidth = constraints.maxWidth
        val big = fullWidth - unit * (count - 1)
        val h = constraints.maxHeight

        val items = measurables.filter { it.layoutId == "item" }
        val widths = IntArray(count) { index ->
            lerp(unit.toFloat(), big.toFloat(), fractions[index].value).roundToInt().coerceAtLeast(0)
        }
        // Rounding can leave a pixel or two; give it to the widest item so the
        // row always fills exactly the same width.
        val drift = fullWidth - widths.sum()
        if (drift != 0) {
            val widest = widths.indices.maxBy { widths[it] }
            widths[widest] = max(0, widths[widest] + drift)
        }
        val itemPlaceables = items.mapIndexed { index, measurable ->
            measurable.measure(constraints.copy(minWidth = widths[index], maxWidth = widths[index], minHeight = h, maxHeight = h))
        }
        val indicator = measurables.first { it.layoutId == "indicator" }
            .measure(constraints.copy(minWidth = fullWidth, maxWidth = fullWidth, minHeight = h, maxHeight = h))

        layout(fullWidth, h) {
            indicator.place(0, 0)
            var x = 0
            itemPlaceables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: NavDestination,
    fraction: Float,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "press",
    )
    val latestFraction by rememberUpdatedState(fraction)
    val labelStyle = MaterialTheme.typography.labelLargeEmphasized

    Layout(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        content = {
            Box(modifier = Modifier.layoutId("icon")) {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                    contentDescription = destination.label,
                    tint = tint,
                )
            }
            Box(
                modifier = Modifier
                    .layoutId("label")
                    .graphicsLayer {
                        val f = latestFraction
                        alpha = f * f
                        val s = lerp(0.7f, 1f, f)
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            ) {
                Text(destination.label, style = labelStyle, color = tint, maxLines = 1, softWrap = false)
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val icon = measurables.first { it.layoutId == "icon" }.measure(loose)
        val label = measurables.first { it.layoutId == "label" }
            .measure(loose.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
        val gap = 8.dp.roundToPx()
        val f = latestFraction
        // Icon + the visible share of the label, centered as one group.
        val groupWidth = icon.width + (gap + label.width) * f
        val iconX = ((constraints.maxWidth - groupWidth) / 2f).roundToInt()
        val labelX = iconX + icon.width + gap
        layout(constraints.maxWidth, constraints.maxHeight) {
            icon.placeRelative(iconX, (constraints.maxHeight - icon.height) / 2)
            if (f > 0.01f) {
                label.placeRelative(labelX, (constraints.maxHeight - label.height) / 2)
            }
        }
    }
}

