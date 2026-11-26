package dev.eymistaken.parabolic.legacy.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.eymistaken.parabolic.core.YtdlpUpdateProgress
import kotlinx.coroutines.delay

/**
 * The bar of a running download, shared by the download rows and the yt-dlp
 * update so both look and move the same: determinate for a known fraction,
 * the standard sweeping bar while it is unknown (null).
 */
@Composable
fun TaskProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    }
}

/** What the yt-dlp update row shows; [progress] stays set while the row folds away. */
data class ShownUpdate(val visible: Boolean, val progress: YtdlpUpdateProgress?)

/**
 * Follows [update] for an animated row. Once a new build has been installed
 * the row stays a moment longer so the full bar is seen; a check that found
 * nothing new goes away at once.
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
