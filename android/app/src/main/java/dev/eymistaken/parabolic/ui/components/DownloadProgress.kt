package dev.eymistaken.parabolic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.eymistaken.parabolic.core.YtdlpUpdateProgress
import kotlinx.coroutines.delay

/**
 * The bar of a running download, shared by the download cards and the yt-dlp
 * update so both move the same way: long, slow waves easing towards a known
 * fraction, and the calm bouncing pill while it is unknown (null).
 */
@Composable
fun DownloadProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    if (progress != null) {
        val thick = with(LocalDensity.current) { Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round) }
        val animated by animateFloatAsState(
            targetValue = progress,
            animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
            label = "bar",
        )
        // Long, slow waves: the filled part leans gently instead of wriggling.
        LinearWavyProgressIndicator(
            progress = { animated },
            modifier = modifier.fillMaxWidth().height(18.dp),
            stroke = thick,
            trackStroke = thick,
            wavelength = 48.dp,
            waveSpeed = 14.dp,
        )
    } else {
        Box(modifier = modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
            CalmIndeterminateBar(trackHeight = 8.dp)
        }
    }
}

/** The large percentage next to a download's stage, counting up with the bar. */
@Composable
fun ProgressPercent(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "pct")
    Text(
        text = "${(animated * 100).toInt()}%",
        style = MaterialTheme.typography.headlineMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** What the yt-dlp update panel shows; [progress] stays set while the panel folds away. */
data class ShownUpdate(val visible: Boolean, val progress: YtdlpUpdateProgress?)

/**
 * Follows [update] for an animated panel. Once a new build has been installed
 * the panel stays a moment longer, so the bar visibly reaches the end before it
 * folds away; a check that found nothing new folds away at once. The last
 * state is kept while the panel animates out, so it does not go blank first.
 */
@Composable
fun rememberShownUpdate(update: YtdlpUpdateProgress?): ShownUpdate {
    var last by remember { mutableStateOf(update) }
    var visible by remember { mutableStateOf(update != null) }
    LaunchedEffect(update) {
        if (update != null) {
            last = update
            visible = true
        } else {
            if (visible && last?.stage == YtdlpUpdateProgress.Stage.INSTALLING) delay(900)
            visible = false
        }
    }
    return ShownUpdate(visible, last)
}
