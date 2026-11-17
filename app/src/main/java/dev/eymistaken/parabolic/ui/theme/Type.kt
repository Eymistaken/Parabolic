package dev.eymistaken.parabolic.ui.theme

import android.content.res.AssetManager
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/** Set once from the Application, before any UI composes. */
object FontAssets {
    lateinit var assets: AssetManager
}

/**
 * Roboto Flex is variable on weight *and* width, which is what gives Material 3
 * Expressive's emphasized type its wide, heavy display look. Each [Font] below is
 * the same file pinned to a different point in that design space.
 *
 * The file is loaded from assets rather than res/font on purpose: an asset font is
 * built with Typeface.Builder and its axes are baked in at construction, whereas a
 * resource font gets its axes through Paint later, which some OEM skins
 * (HyperOS among them) silently drop.
 */
@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int, width: Float = 100f): Font = Font(
    path = "fonts/roboto_flex.ttf",
    assetManager = FontAssets.assets,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
    ),
)

val FlexFamily = FontFamily(
    flex(300), flex(400), flex(500), flex(600), flex(700), flex(800),
)

/** Wide + heavy: headlines, big numbers, emphasized titles. */
val FlexWide = FontFamily(
    flex(500, 118f), flex(600, 124f), flex(700, 132f), flex(800, 140f), flex(900, 151f),
)

/** Narrow, for dense meta lines next to wide titles. */
val FlexCondensed = FontFamily(
    flex(400, 80f), flex(500, 80f), flex(600, 84f),
)

private val base = Typography()

private fun TextStyle.flex() = copy(fontFamily = FlexFamily)

private fun TextStyle.wide(weight: Int, tracking: Float = -0.01f) = copy(
    fontFamily = FlexWide,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private fun TextStyle.strong(weight: Int = 600) = copy(
    fontFamily = FlexFamily,
    fontWeight = FontWeight(weight),
)

val ParabolicTypography = Typography(
    displayLarge = base.displayLarge.flex(),
    displayMedium = base.displayMedium.flex(),
    displaySmall = base.displaySmall.flex(),
    headlineLarge = base.headlineLarge.flex(),
    headlineMedium = base.headlineMedium.flex(),
    headlineSmall = base.headlineSmall.flex(),
    titleLarge = base.titleLarge.flex(),
    titleMedium = base.titleMedium.flex(),
    titleSmall = base.titleSmall.flex(),
    bodyLarge = base.bodyLarge.flex(),
    bodyMedium = base.bodyMedium.flex(),
    bodySmall = base.bodySmall.flex(),
    labelLarge = base.labelLarge.flex(),
    labelMedium = base.labelMedium.flex(),
    labelSmall = base.labelSmall.flex(),
    displayLargeEmphasized = base.displayLarge.wide(900, -0.03f),
    displayMediumEmphasized = base.displayMedium.wide(900, -0.025f),
    displaySmallEmphasized = base.displaySmall.wide(800, -0.02f),
    headlineLargeEmphasized = base.headlineLarge.wide(800, -0.02f),
    headlineMediumEmphasized = base.headlineMedium.wide(800, -0.015f),
    headlineSmallEmphasized = base.headlineSmall.wide(700),
    titleLargeEmphasized = base.titleLarge.wide(700),
    titleMediumEmphasized = base.titleMedium.wide(700, 0f),
    titleSmallEmphasized = base.titleSmall.wide(600, 0f),
    bodyLargeEmphasized = base.bodyLarge.strong(600),
    bodyMediumEmphasized = base.bodyMedium.strong(600),
    bodySmallEmphasized = base.bodySmall.strong(600),
    labelLargeEmphasized = base.labelLarge.strong(700),
    labelMediumEmphasized = base.labelMedium.strong(700),
    labelSmallEmphasized = base.labelSmall.strong(700),
)
