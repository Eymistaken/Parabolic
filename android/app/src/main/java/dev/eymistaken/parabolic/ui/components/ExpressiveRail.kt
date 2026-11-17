package dev.eymistaken.parabolic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import dev.eymistaken.parabolic.ui.theme.FlexWide
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

// Material 3 Expressive navigation rail measurements (collapsed / expanded).
private val CollapsedWidth = 96.dp
private val ExpandedWidth = 256.dp
private val CollapsedItemHeight = 64.dp
private val ExpandedItemHeight = 56.dp
private val ItemGap = 4.dp
private const val CollapsedLabelScale = 0.86f

/**
 * Side navigation for tablets, built on the M3 Expressive motion physics system
 * (m3.material.io/styles/motion/overview/how-it-works).
 *
 * The rail is not two layouts swapped at the end of an animation. It is one
 * layout driven by a single progress value, `morph`, that runs on the *default
 * spatial* spring, the token Material assigns to the expanded navigation rail.
 * Width, item height, the indicator, the icon and the label all interpolate
 * from that one value, so the label travels from under the icon to beside it
 * and everything overshoots and settles together. Content that only appears in
 * the expanded rail (the wordmark, the action label) fades on a separate
 * `reveal` value that runs on the *default effects* spring, because opacity
 * must never overshoot.
 *
 * Small parts follow the same rules at the *fast* speed: the selection pill is
 * magnetic (its leading edge snaps on fast spatial while the trailing edge
 * follows on default spatial), presses squash on fast spatial, and colors and
 * state layers change on fast effects.
 */
@Composable
fun ExpressiveNavigationRail(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val spatial = motion.defaultSpatialSpec<Float>()
    val effects = motion.defaultEffectsSpec<Float>()
    val fastEffects = motion.fastEffectsSpec<Float>()

    val morph = remember { Animatable(if (expanded) 1f else 0f) }
    val reveal = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        val target = if (expanded) 1f else 0f
        coroutineScope {
            launch { morph.animateTo(target, spatial) }
            // Expanded-only content fades in at the default pace, but gets out
            // of the way quickly when the rail starts to close.
            launch { reveal.animateTo(target, if (expanded) effects else fastEffects) }
        }
    }
    // Read in the layout and draw phases only, so the rail never recomposes
    // per frame while it morphs.
    val morphValue = { morph.value }
    val revealValue = { reveal.value }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .layout { measurable, constraints ->
                val width = lerp(CollapsedWidth, ExpandedWidth, morph.value).roundToPx().coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(width, placeable.height) { placeable.place(0, 0) }
            }
            .clipToBounds()
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        RailHeader(
            expanded = expanded,
            onToggle = { onExpandedChange(!expanded) },
            morph = morphValue,
            reveal = revealValue,
        )
        Spacer(Modifier.height(16.dp))
        RailAction(label = actionLabel, icon = actionIcon, onClick = onAction, morph = morphValue, reveal = revealValue)
        Spacer(Modifier.height(28.dp))
        RailItems(
            destinations = destinations,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            morph = morphValue,
        )
    }
}

// ---------------------------------------------------------------------------
// Header: menu toggle + wordmark
// ---------------------------------------------------------------------------

@Composable
private fun RailHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    morph: () -> Float,
    reveal: () -> Float,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "menuPress",
    )
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .padding(start = 24.dp)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    role = Role.Button,
                    onClickLabel = if (expanded) "Menüyü daralt" else "Menüyü genişlet",
                    onClick = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Rotation is spatial (it may overshoot), the crossfade is an effect.
            Icon(
                Icons.Filled.Menu,
                contentDescription = if (expanded) null else "Menüyü genişlet",
                tint = tint,
                modifier = Modifier.graphicsLayer {
                    rotationZ = morph() * 90f
                    alpha = 1f - reveal()
                },
            )
            Icon(
                Icons.AutoMirrored.Filled.MenuOpen,
                contentDescription = if (expanded) "Menüyü daralt" else null,
                tint = tint,
                modifier = Modifier.graphicsLayer {
                    rotationZ = (morph() - 1f) * 90f
                    alpha = reveal()
                },
            )
        }

        Row(
            modifier = Modifier
                .padding(start = 8.dp)
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { })
                .graphicsLayer {
                    alpha = reveal()
                    translationX = (1f - morph()) * -24.dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ParabolicLogo(modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Parabolic",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FlexWide,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Primary action: a FAB that grows into an extended FAB with the rail
// ---------------------------------------------------------------------------

@Composable
private fun RailAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    morph: () -> Float,
    reveal: () -> Float,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fastSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    // Rounded corners are a spatial property: the square-ish FAB morphs toward
    // a full pill while pressed and bounces back on release.
    val corner by animateFloatAsState(if (pressed) 28f else 16f, fastSpatial, label = "fabCorner")
    val press by animateFloatAsState(if (pressed) 0.94f else 1f, fastSpatial, label = "fabPress")
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val content = MaterialTheme.colorScheme.onTertiaryContainer

    Layout(
        modifier = Modifier
            .padding(start = 20.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
                shape = RoundedCornerShape(corner.dp)
                clip = true
            }
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                role = Role.Button,
                onClick = onClick,
            ),
        content = {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.layoutId("icon"))
            Text(
                label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = content,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .layoutId("label")
                    .graphicsLayer {
                        alpha = reveal()
                        translationX = (1f - reveal()) * -8.dp.toPx()
                    },
            )
        },
    ) { measurables, constraints ->
        val iconPlaceable = measurables.first { it.layoutId == "icon" }.measure(Constraints())
        val labelPlaceable = measurables.first { it.layoutId == "label" }.measure(Constraints())
        val height = 56.dp.roundToPx()
        val collapsed = 56.dp.toPx()
        val labelStart = 52.dp.toPx()
        val expandedWidth = labelStart + labelPlaceable.width + 20.dp.toPx()
        val width = lerp(collapsed, expandedWidth, morph()).roundToInt()
            .coerceIn(height / 2, constraints.maxWidth.coerceAtLeast(height / 2))
        layout(width, height) {
            iconPlaceable.place(16.dp.roundToPx(), (height - iconPlaceable.height) / 2)
            labelPlaceable.place(labelStart.roundToInt(), (height - labelPlaceable.height) / 2)
        }
    }
}

// ---------------------------------------------------------------------------
// Destinations with a magnetic, shared selection pill
// ---------------------------------------------------------------------------

/** The indicator's bounds inside one item, for a given morph progress. */
private fun Density.indicatorRect(p: Float, itemWidth: Float): Rect {
    val left = lerp(20.dp.toPx(), 12.dp.toPx(), p)
    val right = lerp(76.dp.toPx(), itemWidth - 12.dp.toPx(), p)
    val top = lerp(4.dp.toPx(), 0f, p)
    val height = lerp(32.dp.toPx(), 56.dp.toPx(), p)
    return Rect(left, top, right, top + height)
}

private fun Density.itemHeightPx(p: Float): Int =
    lerp(CollapsedItemHeight.toPx(), ExpandedItemHeight.toPx(), p).roundToInt()

@Composable
private fun RailItems(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    morph: () -> Float,
) {
    val count = destinations.size
    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val fastSpatial = motion.fastSpatialSpec<Float>()
    val defaultSpatial = motion.defaultSpatialSpec<Float>()
    val fastEffects = motion.fastEffectsSpec<Float>()
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer

    val selection = remember(count) { List(count) { Animatable(if (it == selectedIndex) 1f else 0f) } }
    val pops = remember(count) { List(count) { Animatable(1f) } }
    val topEdge = remember { Animatable(selectedIndex.toFloat()) }
    val bottomEdge = remember { Animatable(selectedIndex.toFloat()) }
    val lastSelected = remember { intArrayOf(selectedIndex) }

    val interactions = remember(count) { List(count) { MutableInteractionSource() } }
    val pressScales = interactions.map { source ->
        val pressed by source.collectIsPressedAsState()
        animateFloatAsState(if (pressed) 0.9f else 1f, fastSpatial, label = "itemPress")
    }
    val stateLayers = interactions.map { source ->
        val pressed by source.collectIsPressedAsState()
        val hovered by source.collectIsHoveredAsState()
        val focused by source.collectIsFocusedAsState()
        val target = when {
            pressed || focused -> 0.10f
            hovered -> 0.08f
            else -> 0f
        }
        animateFloatAsState(target, fastEffects, label = "stateLayer")
    }

    LaunchedEffect(selectedIndex) {
        val target = selectedIndex.toFloat()
        val movingDown = target > (topEdge.value + bottomEdge.value) / 2f
        val changed = lastSelected[0] != selectedIndex
        lastSelected[0] = selectedIndex
        coroutineScope {
            selection.forEachIndexed { index, fraction ->
                launch { fraction.animateTo(if (index == selectedIndex) 1f else 0f, fastEffects) }
            }
            // The edge facing the direction of travel leads; the far edge lets
            // go a beat later, so the pill stretches toward the new item.
            launch { topEdge.animateTo(target, if (movingDown) defaultSpatial else fastSpatial) }
            launch { bottomEdge.animateTo(target, if (movingDown) fastSpatial else defaultSpatial) }
            // A spring is stiffness, damping *and* initial velocity: kicking the
            // icon's scale makes it pop and settle back without a keyframe.
            if (changed) launch { pops[selectedIndex].animateTo(1f, fastSpatial, initialVelocity = 3f) }
        }
    }

    Layout(
        modifier = Modifier.selectableGroup(),
        content = {
            Canvas(modifier = Modifier.layoutId("indicator")) {
                val p = morph()
                val step = itemHeightPx(p) + ItemGap.roundToPx()
                val base = indicatorRect(p, size.width)
                var top = topEdge.value * step + base.top
                var bottom = bottomEdge.value * step + base.bottom
                if (bottom - top < base.height) {
                    val mid = (top + bottom) / 2f
                    top = mid - base.height / 2f
                    bottom = mid + base.height / 2f
                }
                // Stretch -> narrower, like a blob keeping its volume.
                val stretch = ((bottom - top) / base.height - 1f).coerceIn(0f, 1f)
                val press = pressScales[selectedIndex].value
                val width = base.width * (1f - 0.14f * stretch) * press
                val height = (bottom - top) * press
                val cx = base.center.x
                val cy = (top + bottom) / 2f
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(cx - width / 2f, cy - height / 2f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(min(width, height) / 2f),
                )
            }
            destinations.forEachIndexed { index, destination ->
                RailItem(
                    destination = destination,
                    selected = index == selectedIndex,
                    selection = { selection[index].value },
                    pop = { pops[index].value },
                    press = { pressScales[index].value },
                    stateLayer = { stateLayers[index].value },
                    morph = morph,
                    interaction = interactions[index],
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
        val width = constraints.maxWidth
        val itemHeight = itemHeightPx(morph())
        val gap = ItemGap.roundToPx()
        val total = itemHeight * count + gap * (count - 1).coerceAtLeast(0)
        val items = measurables.filter { it.layoutId == "item" }
            .map { it.measure(Constraints.fixed(width, itemHeight)) }
        val indicator = measurables.first { it.layoutId == "indicator" }
            .measure(Constraints.fixed(width, total))
        layout(width, total) {
            indicator.place(0, 0)
            items.forEachIndexed { index, placeable -> placeable.place(0, index * (itemHeight + gap)) }
        }
    }
}

@Composable
private fun RailItem(
    destination: NavDestination,
    selected: Boolean,
    selection: () -> Float,
    pop: () -> Float,
    press: () -> Float,
    stateLayer: () -> Float,
    morph: () -> Float,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val idle = colors.onSurfaceVariant
    val active = colors.onSecondaryContainer
    val layerColor = colors.onSurface
    val tint = lerp(idle, active, selection().coerceIn(0f, 1f))

    Layout(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
            .drawBehind {
                val alpha = stateLayer()
                if (alpha > 0.001f) {
                    val r = indicatorRect(morph(), size.width)
                    drawRoundRect(
                        color = layerColor.copy(alpha = alpha),
                        topLeft = r.topLeft,
                        size = r.size,
                        cornerRadius = CornerRadius(min(r.width, r.height) / 2f),
                    )
                }
            },
        content = {
            Icon(
                imageVector = if (selected) destination.selectedIcon else destination.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .layoutId("icon")
                    .graphicsLayer {
                        val s = pop() * press()
                        scaleX = s
                        scaleY = s
                    },
            )
            Text(
                destination.label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .layoutId("label")
                    .graphicsLayer {
                        val s = lerp(CollapsedLabelScale, 1f, morph().coerceIn(0f, 1f)) * press()
                        scaleX = s
                        scaleY = s
                    },
            )
        },
    ) { measurables, constraints ->
        val p = morph()
        val icon = measurables.first { it.layoutId == "icon" }.measure(Constraints())
        val label = measurables.first { it.layoutId == "label" }.measure(Constraints())
        // Collapsed: icon inside a 56x32 pill, label centered underneath.
        // Expanded: icon and label side by side inside a full-width pill.
        val iconCx = lerp(48.dp.toPx(), 40.dp.toPx(), p)
        val iconCy = lerp(20.dp.toPx(), 28.dp.toPx(), p)
        val labelCx = lerp(48.dp.toPx(), 64.dp.toPx() + label.width / 2f, p)
        val labelCy = lerp(48.dp.toPx(), 28.dp.toPx(), p)
        layout(constraints.maxWidth, constraints.maxHeight) {
            icon.place((iconCx - icon.width / 2f).roundToInt(), (iconCy - icon.height / 2f).roundToInt())
            label.place((labelCx - label.width / 2f).roundToInt(), (labelCy - label.height / 2f).roundToInt())
        }
    }
}
