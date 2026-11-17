package dev.eymistaken.parabolic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.eymistaken.parabolic.core.HistoryEntry
import dev.eymistaken.parabolic.core.MediaKind
import dev.eymistaken.parabolic.ui.components.Pill
import dev.eymistaken.parabolic.ui.components.toShapeSafe
import dev.eymistaken.parabolic.ui.formatBytes
import dev.eymistaken.parabolic.ui.formatRelativeTime

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    contentPadding: PaddingValues,
    onOpen: (HistoryEntry) -> Unit,
    onRedownload: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        EmptyState(
            title = "Geçmiş boş",
            body = "Tamamlanan indirmeler burada birikir; dokununca oynatıcıda açılır.",
            contentPadding = contentPadding,
            modifier = modifier,
        )
        return
    }

    LazyVerticalGrid(
        // One column on phones, two to four on tablets.
        columns = GridCells.Adaptive(300.dp),
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
        items(entries, key = { it.id }) { entry ->
            HistoryCard(
                entry = entry,
                onOpen = { onOpen(entry) },
                onRedownload = { onRedownload(entry) },
                onDelete = { onDelete(entry) },
                modifier = Modifier.animateItem(),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "clear") {
            TextButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth().animateItem()) {
                Text("Geçmişi temizle")
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 7.5f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (entry.thumbnail != null) {
                    AsyncImage(
                        model = entry.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShapeSafe())
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (entry.kind == MediaKind.AUDIO) Icons.Filled.MusicNote else Icons.Filled.PlayArrow,
                        contentDescription = "Aç",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Pill(
                        text = if (entry.kind == MediaKind.AUDIO) "Ses" else "Video",
                        container = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                        content = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    if (entry.sizeBytes > 0) {
                        Pill(
                            text = formatBytes(entry.sizeBytes),
                            container = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                            content = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatRelativeTime(entry.completedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onRedownload) {
                    Icon(Icons.Filled.Replay, contentDescription = "Yeniden indir")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Geçmişten sil")
                }
            }
        }
    }
}
