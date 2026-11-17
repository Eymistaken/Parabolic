package dev.eymistaken.parabolic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import dev.eymistaken.parabolic.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Shape morphing
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Calm indeterminate bar
// ---------------------------------------------------------------------------

/**
 * Indeterminate bar for the "preparing" phase: one pill travelling inside a flat
 * track on a real spring, wall to wall.
 *
 * Material's spatial springs overshoot and bounce into place
 * (m3.material.io/styles/motion). Here the overshoot is spent against the wall:
 * the pill's leading edge stays pinned to the end of the track while its body
 * squashes (shorter, and a little taller to keep its volume), then the spring
 * releases it and it pushes off the other way. In flight it stretches slightly
 * with speed. The spring borrows its bounce from the fast spatial token
 * (damping 0.6) and its calm pace from the slow spatial one (stiffness 200).
 */
@Composable
fun CalmIndeterminateBar(
    modifier: Modifier = Modifier,
    trackHeight: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val position = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val bounce = spring<Float>(dampingRatio = 0.6f, stiffness = 200f)
        var target = 1f
        while (true) {
            position.animateTo(target, bounce)
            target = 1f - target
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(trackHeight * 2)) {
        val track = trackHeight.toPx()
        val cy = size.height / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, cy - track / 2f),
            size = Size(size.width, track),
            cornerRadius = CornerRadius(track / 2f),
        )

        val baseWidth = size.width * 0.3f
        val travel = size.width - baseWidth
        val p = position.value

        // Speed stretch while travelling (velocity is in track-lengths per second).
        val speed = (abs(position.velocity) / 6f).coerceIn(0f, 1f)
        var width = baseWidth * (1f + 0.14f * speed)
        var left = p.coerceIn(0f, 1f) * travel - (width - baseWidth) / 2f

        // Overshoot past either end becomes a squash against that wall.
        val squash = when {
            p > 1f -> (p - 1f) * travel
            p < 0f -> -p * travel
            else -> 0f
        }
        if (squash > 0f) {
            width = (baseWidth - squash * 1.6f).coerceAtLeast(baseWidth * 0.45f)
            left = if (p > 1f) size.width - width else 0f
        }
        left = left.coerceIn(0f, size.width - width)

        // Keep the area roughly constant: shorter/longer pill -> taller/thinner.
        val height = (track * kotlin.math.sqrt(baseWidth / width)).coerceIn(track * 0.8f, size.height)
        drawRoundRect(
            color = color,
            topLeft = Offset(left, cy - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2f),
        )
    }
}

// ---------------------------------------------------------------------------
// Logo
// ---------------------------------------------------------------------------

@Composable
fun ParabolicLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.parabolic_logo),
        contentDescription = "Parabolic",
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Slider that glides instead of teleporting between steps
// ---------------------------------------------------------------------------

/**
 * A stepped slider in the Material 3 Expressive style (tall handle, thick track,
 * stop dots) whose handle follows the finger continuously and then springs onto
 * the nearest step, with a haptic tick each time it crosses one.
 */
@Composable
fun ExpressiveStepSlider(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val latestOnChange by rememberUpdatedState(onValueChange)
    val position = remember { Animatable(value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val steps = (range.last - range.first).coerceAtLeast(1)
    val settle = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(value) {
        if (!dragging && abs(position.value - value) > 0.001f) position.animateTo(value.toFloat(), settle)
    }

    val handleWidth by animateDpAsState(
        targetValue = if (dragging) 2.dp else 4.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "handle",
    )
    val handleHeight by animateDpAsState(
        targetValue = if (dragging) 48.dp else 44.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "handleHeight",
    )

    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.secondaryContainer
    val tickOnActive = MaterialTheme.colorScheme.onPrimary
    val tickOnInactive = MaterialTheme.colorScheme.onSecondaryContainer

    var lastReported by remember { mutableStateOf(value) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { role = Role.Button }
            .pointerInput(range) {
                val inset = 10.dp.toPx()
                fun valueAt(x: Float): Float {
                    val fraction = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    return range.first + fraction * steps
                }
                fun report(raw: Float) {
                    val rounded = raw.roundToInt().coerceIn(range.first, range.last)
                    if (rounded != lastReported) {
                        lastReported = rounded
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnChange(rounded)
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        scope.launch { position.snapTo(valueAt(offset.x)) }
                    },
                    onDragEnd = {
                        dragging = false
                        val target = position.value.roundToInt().coerceIn(range.first, range.last)
                        report(target.toFloat())
                        scope.launch { position.animateTo(target.toFloat(), settle) }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { position.animateTo(lastReported.toFloat(), settle) }
                    },
                    onHorizontalDrag = { change, _ ->
                        val raw = valueAt(change.position.x)
                        scope.launch { position.snapTo(raw) }
                        report(raw)
                    },
                )
            }
            .pointerInput(range) {
                val inset = 10.dp.toPx()
                detectTapGestures { offset ->
                    val fraction = ((offset.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    val target = (range.first + fraction * steps).roundToInt()
                    if (target != lastReported) {
                        lastReported = target
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnChange(target)
                    }
                    scope.launch { position.animateTo(target.toFloat(), settle) }
                }
            },
    ) {
        val inset = 10.dp.toPx()
        val trackHeight = 16.dp.toPx()
        val gap = 6.dp.toPx()
        val outer = trackHeight / 2f
        val inner = 3.dp.toPx()
        val cy = size.height / 2f
        val top = cy - trackHeight / 2f
        val usable = size.width - 2 * inset
        val fraction = ((position.value - range.first) / steps).coerceIn(0f, 1f)
        val hx = inset + fraction * usable
        val hw = handleWidth.toPx()

        val activeEnd = hx - hw / 2f - gap
        if (activeEnd > 0f) {
            drawPath(
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f, top = top, right = activeEnd, bottom = top + trackHeight,
                            topLeftCornerRadius = CornerRadius(outer),
                            bottomLeftCornerRadius = CornerRadius(outer),
                            topRightCornerRadius = CornerRadius(inner),
                            bottomRightCornerRadius = CornerRadius(inner),
                        )
                    )
                },
                color = active,
            )
        }
        val inactiveStart = hx + hw / 2f + gap
        if (inactiveStart < size.width) {
            drawPath(
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = inactiveStart, top = top, right = size.width, bottom = top + trackHeight,
                            topLeftCornerRadius = CornerRadius(inner),
                            bottomLeftCornerRadius = CornerRadius(inner),
                            topRightCornerRadius = CornerRadius(outer),
                            bottomRightCornerRadius = CornerRadius(outer),
                        )
                    )
                },
                color = inactive,
            )
        }
        for (i in 0..steps) {
            val x = inset + usable * i / steps
            if (abs(x - hx) < hw / 2f + gap + 2.dp.toPx()) continue
            drawCircle(
                color = if (x < hx) tickOnActive else tickOnInactive,
                radius = 2.dp.toPx(),
                center = Offset(x, cy),
            )
        }
        val hh = handleHeight.toPx()
        drawRoundRect(
            color = active,
            topLeft = Offset(hx - hw / 2f, cy - hh / 2f),
            size = Size(hw, hh),
            cornerRadius = CornerRadius(hw / 2f),
        )
    }
}

/** Label + animated number + [ExpressiveStepSlider]. */
@Composable
fun SliderSetting(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    supporting: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                supporting?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "sliderValue",
            ) { shown ->
                Text(
                    text = shown.toString(),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ExpressiveStepSlider(value = value, range = range, onValueChange = onChange)
    }
}

// ---------------------------------------------------------------------------
// Button groups
// ---------------------------------------------------------------------------

/** Connected, single-select button group (the "Going / Not going / Maybe" pattern). */
@Composable
fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    // Icons only when every button has room for icon + label; in a narrow
    // column (three settings cards side by side on a tablet) the label wins.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val showIcons = icon != null && maxWidth / options.size >= 124.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton },
                shapes = when {
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                if (showIcons && icon != null) {
                    Icon(icon(option), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(label(option), maxLines = 1, softWrap = false)
            }
        }
    }
    }
}

/** Free-flowing toggle buttons that morph from pill to rounded-square when picked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ToggleFlow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                Text(label(option))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings scaffolding
// ---------------------------------------------------------------------------

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MaterialShapes.Cookie4Sided.toShapeSafe())
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            content()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            thumbContent = if (checked) {
                {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            },
        )
    }
}

/** Small rounded label used for stages, durations, counts. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(text, style = MaterialTheme.typography.labelMediumEmphasized, color = content, maxLines = 1)
    }
}

/** Polygon → Compose shape, normalized to the layout bounds. */
fun RoundedPolygon.toShapeSafe(rotation: Float = 0f): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = this@toShapeSafe.toPath()
        val matrix = android.graphics.Matrix().apply {
            setScale(size.width, size.height)
            postRotate(rotation, size.width / 2f, size.height / 2f)
        }
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}

val SheetCorner: Dp = 32.dp
val PanelShape = RoundedCornerShape(36.dp)
