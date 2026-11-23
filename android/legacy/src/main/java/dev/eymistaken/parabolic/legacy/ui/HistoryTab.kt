package dev.eymistaken.parabolic.legacy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.eymistaken.parabolic.core.HistoryEntry
import dev.eymistaken.parabolic.core.MediaKind

@Composable
fun HistoryTab(
    entries: List<HistoryEntry>,
    contentPadding: PaddingValues,
    onOpen: (HistoryEntry) -> Unit,
    onRedownload: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(
            title = "Geçmiş boş",
            body = "Tamamlanan indirmeler burada listelenir. Dokununca oynatıcıda açılır.",
            contentPadding = contentPadding,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 8.dp,
        ),
    ) {
        items(entries, key = { it.id }) { entry ->
            HistoryRow(
                entry = entry,
                onOpen = { onOpen(entry) },
                onRedownload = { onRedownload(entry) },
                onDelete = { onDelete(entry) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = modifier.clickable(onClick = onOpen),
        leadingContent = { Thumbnail(url = entry.thumbnail, kind = entry.kind, width = 88.dp) },
        headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(
                    if (entry.kind == MediaKind.AUDIO) "Ses" else "Video",
                    entry.sizeBytes.takeIf { it > 0 }?.let(::formatBytes),
                    formatRelativeTime(entry.completedAt),
                ).joinToString(" · "),
                maxLines = 1,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Seçenekler")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Yeniden indir") },
                        leadingIcon = { Icon(Icons.Filled.Replay, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRedownload()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Geçmişten sil") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}
