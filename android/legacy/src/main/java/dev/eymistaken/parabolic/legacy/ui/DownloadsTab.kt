package dev.eymistaken.parabolic.legacy.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.eymistaken.parabolic.R
import dev.eymistaken.parabolic.core.DownloadState
import dev.eymistaken.parabolic.core.DownloadTask
import dev.eymistaken.parabolic.core.MediaKind

@Composable
fun DownloadsTab(
    tasks: List<DownloadTask>,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onCancel: (String) -> Unit,
    onRetry: (DownloadTask) -> Unit,
    onDismiss: (String) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onAdd: () -> Unit,
    onPaste: () -> Unit,
) {
    if (tasks.isEmpty()) {
        EmptyState(
            title = "Henüz indirme yok",
            body = "Bir bağlantı yapıştır ya da YouTube'da Paylaş menüsünden Parabolic'i seç.",
            contentPadding = contentPadding,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPaste) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Panodan yapıştır")
                }
                Button(onClick = onAdd) { Text("Bağlantı ekle") }
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
    var showLog by rememberSaveable(task.id) { mutableStateOf(false) }
    val done = task.state == DownloadState.DONE

    Card(
        onClick = { if (done) onOpen() else showLog = !showLog },
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Thumbnail(url = task.spec.thumbnail, kind = task.spec.kind, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                    Text(
                        text = task.spec.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = listOfNotNull(
                            if (task.spec.kind == MediaKind.AUDIO) "Ses · ${task.spec.audioFormat.label}"
                            else "${task.spec.quality.label} · ${task.spec.container.label}",
                            task.totalSize,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (task.state.isActive) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "İptal et")
                    }
                } else {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Listeden kaldır")
                    }
                }
            }

            Column(modifier = Modifier.padding(end = 12.dp)) {
                when (task.state) {
                    DownloadState.DONE -> DoneSection(task, onOpen)
                    DownloadState.FAILED -> FailedSection(task, onRetry)
                    DownloadState.CANCELED -> {
                        StatusLine(Icons.Filled.Cancel, "İptal edildi", MaterialTheme.colorScheme.onSurfaceVariant)
                        task.notice?.let { Notice(it) }
                    }
                    else -> ActiveSection(task)
                }

                if (task.log.isNotEmpty()) {
                    TextButton(
                        onClick = { showLog = !showLog },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text(if (showLog) "Ayrıntıları gizle" else "Ayrıntılar") }
                    if (showLog) LogPanel(task.log)
                }
            }
        }
    }
}

@Composable
private fun ActiveSection(task: DownloadTask) {
    val queued = task.state == DownloadState.QUEUED
    val progress = task.progress
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    queued -> "Sırada"
                    task.itemCount > 0 -> "${task.itemIndex}/${task.itemCount} · ${task.stage.label}"
                    else -> task.stage.label
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (progress != null) {
                Text(
                    text = "%${(progress * 100).toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else if (queued) {
            LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        val meta = listOfNotNull(task.speed, formatEta(task.etaSeconds))
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DoneSection(task: DownloadTask, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            StatusLine(Icons.Filled.CheckCircle, "Tamamlandı", MaterialTheme.colorScheme.primary, topPadding = 0.dp)
            task.savedPath?.substringBeforeLast('/')?.let { folder ->
                Text(
                    text = folder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onOpen) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (task.spec.kind == MediaKind.AUDIO) "Dinle" else "Oynat")
        }
    }
    task.notice?.let { Notice(it) }
}

@Composable
private fun FailedSection(task: DownloadTask, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        StatusLine(Icons.Filled.ErrorOutline, "Başarısız", MaterialTheme.colorScheme.error, topPadding = 0.dp)
        Text(
            text = task.error ?: "Bilinmeyen hata",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tekrar dene")
        }
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color,
    topPadding: androidx.compose.ui.unit.Dp = 12.dp,
) {
    Row(modifier = Modifier.padding(top = topPadding), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun LogPanel(lines: List<String>) {
    val vertical = rememberScrollState()
    LaunchedEffect(lines.size, lines.lastOrNull()) { vertical.scrollTo(vertical.maxValue) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .verticalScroll(vertical)
            .horizontalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        Text(
            text = lines.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
        )
    }
}

/** 16:9 preview with a kind icon underneath while it loads, or when there is none. */
@Composable
internal fun Thumbnail(url: String?, kind: MediaKind, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 96.dp) {
    Box(
        modifier = modifier
            .width(width)
            .height(width * 9 / 16)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (kind == MediaKind.AUDIO) Icons.Filled.MusicNote else Icons.Filled.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun EmptyState(
    title: String,
    body: String,
    contentPadding: PaddingValues,
    actions: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.parabolic_logo),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actions != null) {
                Spacer(Modifier.height(24.dp))
                actions()
            }
        }
    }
}
