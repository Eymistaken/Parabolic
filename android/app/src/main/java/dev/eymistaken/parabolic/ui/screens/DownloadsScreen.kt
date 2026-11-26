package dev.eymistaken.parabolic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.eymistaken.parabolic.core.DownloadState
import dev.eymistaken.parabolic.core.DownloadTask
import dev.eymistaken.parabolic.core.MediaKind
import dev.eymistaken.parabolic.ui.components.DownloadProgressBar
import dev.eymistaken.parabolic.ui.components.ParabolicLogo
import dev.eymistaken.parabolic.ui.components.Pill
import dev.eymistaken.parabolic.ui.components.ProgressPercent
import dev.eymistaken.parabolic.ui.components.toShapeSafe
import dev.eymistaken.parabolic.ui.formatEta

@Composable
fun DownloadsScreen(
    tasks: List<DownloadTask>,
    contentPadding: PaddingValues,
    onCancel: (String) -> Unit,
    onRetry: (DownloadTask) -> Unit,
    onDismiss: (String) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onClearFinished: () -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
    minCardWidth: androidx.compose.ui.unit.Dp = 360.dp,
) {
    if (tasks.isEmpty()) {
        EmptyState(
            title = "Henüz indirme yok",
            body = "Bir bağlantı yapıştır ya da YouTube'dan Parabolic'e paylaş.",
            contentPadding = contentPadding,
            primaryAction = "Bağlantı ekle" to onAdd,
            secondaryAction = "Panodan yapıştır" to onPaste,
            modifier = modifier,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCardWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 120.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            DownloadCard(
                task = task,
                onCancel = { onCancel(task.id) },
                onRetry = { onRetry(task) },
                onDismiss = { onDismiss(task.id) },
                onOpen = { onOpen(task) },
                modifier = Modifier.animateItem(),
            )
        }
        if (tasks.any { it.state.isTerminal }) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "clear") {
                TextButton(
                    onClick = onClearFinished,
                    modifier = Modifier.fillMaxWidth().animateItem(),
                ) { Text("Bitenleri listeden kaldır") }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDetails by rememberSaveable(task.id) { mutableStateOf(false) }
    val done = task.state == DownloadState.DONE
    val failed = task.state == DownloadState.FAILED
    val container by animateColorAsState(
        targetValue = when {
            failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            done -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "cardColor",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        onClick = { if (done) onOpen() else showDetails = !showDetails },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Thumbnail(task, done)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.spec.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (task.spec.kind == MediaKind.AUDIO) Icons.Filled.MusicNote else Icons.Filled.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = listOfNotNull(
                                if (task.spec.kind == MediaKind.AUDIO) task.spec.audioFormat.label
                                else "${task.spec.quality.label} · ${task.spec.container.label}",
                                task.totalSize,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                when {
                    task.state.isActive -> FilledTonalIconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "İptal et")
                    }
                    else -> IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Listeden kaldır")
                    }
                }
            }

            val enterFx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val enterScale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val exitFx = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = when {
                    task.state.isActive -> CardMode.Active
                    done -> CardMode.Done
                    failed -> CardMode.Failed
                    else -> CardMode.Canceled
                },
                transitionSpec = {
                    (fadeIn(enterFx) + scaleIn(enterScale, initialScale = 0.92f)) togetherWith fadeOut(exitFx)
                },
                label = "cardMode",
            ) { mode ->
                when (mode) {
                    CardMode.Active -> ActiveSection(task)
                    CardMode.Done -> DoneSection(task, onOpen)
                    CardMode.Failed -> FailedSection(task, onRetry)
                    CardMode.Canceled -> Text(
                        "İptal edildi",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            if (task.log.isNotEmpty()) {
                DetailsToggle(expanded = showDetails, onToggle = { showDetails = !showDetails })
                AnimatedVisibility(
                    visible = showDetails,
                    enter = fadeIn() + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut() + shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    LogPanel(task.log)
                }
            }
        }
    }
}

private enum class CardMode { Active, Done, Failed, Canceled }

@Composable
private fun Thumbnail(task: DownloadTask, done: Boolean) {
    Box(
        modifier = Modifier
            .width(116.dp)
            .height(66.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (task.spec.thumbnail != null) {
            AsyncImage(
                model = task.spec.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            visible = done,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.4f) + fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShapeSafe())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun ActiveSection(task: DownloadTask) {
    val queued = task.state == DownloadState.QUEUED
    val progress = task.progress
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(
                text = when {
                    queued -> "Sırada"
                    task.itemCount > 0 -> "${task.itemIndex}/${task.itemCount} · ${task.stage.label}"
                    else -> task.stage.label
                },
                leading = if (progress == null && !queued) {
                    { LoadingIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSecondaryContainer) }
                } else {
                    null
                },
            )
            Spacer(Modifier.weight(1f))
            if (progress != null) ProgressPercent(progress)
        }
        Spacer(Modifier.height(10.dp))
        DownloadProgressBar(progress)
        val meta = listOfNotNull(task.speed, formatEta(task.etaSeconds))
        AnimatedVisibility(visible = meta.isNotEmpty()) {
            Text(
                text = meta.joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DoneSection(task: DownloadTask, onOpen: () -> Unit) {
    Column {
    Row(
        modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill(
            text = "Tamamlandı",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            leading = {
                Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = task.savedPath?.substringBeforeLast('/') ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onOpen, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (task.spec.kind == MediaKind.AUDIO) "Dinle" else "Oynat")
        }
    }
    task.notice?.let { notice ->
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
    }
}

@Composable
private fun FailedSection(task: DownloadTask, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                task.error ?: "Bilinmeyen hata",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tekrar dene")
        }
    }
}

@Composable
private fun DetailsToggle(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "details",
    )
    Row(
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Terminal, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text("Ayrıntılar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.Filled.KeyboardArrowDown,
            null,
            Modifier.size(18.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogPanel(lines: List<String>) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size, lines.lastOrNull()) { scroll.animateScrollTo(scroll.maxValue) }
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .verticalScroll(scroll)
            .padding(12.dp),
    ) {
        Text(
            text = lines.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EmptyState(
    title: String,
    body: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            ParabolicLogo(modifier = Modifier.size(128.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (primaryAction != null || secondaryAction != null) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    secondaryAction?.let { (label, action) ->
                        FilledTonalButton(onClick = action, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 52.dp)) {
                            Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                    primaryAction?.let { (label, action) ->
                        Button(onClick = action, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 52.dp)) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}
