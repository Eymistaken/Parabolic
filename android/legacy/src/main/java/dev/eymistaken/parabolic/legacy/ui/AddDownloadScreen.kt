package dev.eymistaken.parabolic.legacy.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.eymistaken.parabolic.core.AudioFormat
import dev.eymistaken.parabolic.core.DownloadSpec
import dev.eymistaken.parabolic.core.MediaKind
import dev.eymistaken.parabolic.core.MediaProbe
import dev.eymistaken.parabolic.core.Prefs
import dev.eymistaken.parabolic.core.VideoContainer
import dev.eymistaken.parabolic.core.VideoQuality
import dev.eymistaken.parabolic.core.YtdlpEngine
import dev.eymistaken.parabolic.core.lastMeaningfulLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Everything the "new download" form knows. Same flow as the main app's form. */
@Stable
class AddDownloadState(initialUrl: String, private val prefs: Prefs) {
    var url by mutableStateOf(initialUrl)
        private set
    var probing by mutableStateOf(false)
        private set
    var probe by mutableStateOf<MediaProbe?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var kind by mutableStateOf(prefs.defaultKind)
    var quality by mutableStateOf(prefs.defaultQuality)
    var container by mutableStateOf(prefs.defaultContainer)
    var audioFormat by mutableStateOf(prefs.defaultAudioFormat)
    var playlist by mutableStateOf(false)
    var embedThumbnail by mutableStateOf(prefs.embedThumbnail)
    var embedMetadata by mutableStateOf(prefs.embedMetadata)
    var embedSubtitles by mutableStateOf(prefs.embedSubtitles)
    var subtitleLanguages by mutableStateOf(prefs.subtitleLanguages)
    var sponsorBlock by mutableStateOf(prefs.sponsorBlock)

    /** Per-download folder (SAF tree URI); null = the default for the kind. */
    var targetFolder by mutableStateOf<String?>(null)

    /** Set when the link belongs to a playlist (a list page, or watch?v=…&list=…). */
    var playlistTitle by mutableStateOf<String?>(null)
        private set
    var playlistCount by mutableIntStateOf(0)
        private set
    var playlistLookup by mutableStateOf(false)
        private set
    val offersPlaylist: Boolean get() = playlistTitle != null || playlistLookup

    fun defaultFolder(): String? = prefs.folderFor(kind)

    private var job: Job? = null
    private var listJob: Job? = null

    /** Editing the link makes any earlier result stale. */
    fun onUrlChange(value: String) {
        url = value
        error = null
        if (probe != null || probing) clearProbe()
    }

    private fun clearProbe() {
        job?.cancel()
        listJob?.cancel()
        probe = null
        probing = false
        playlist = false
        playlistTitle = null
        playlistCount = 0
        playlistLookup = false
    }

    fun startProbe(context: Context, scope: CoroutineScope) {
        val target = extractUrl(url) ?: url.trim()
        if (target.isBlank()) return
        clearProbe()
        url = target
        probing = true
        error = null
        job = scope.launch {
            runCatching { YtdlpEngine.probe(context, target) }
                .onSuccess { result ->
                    probe = result
                    playlist = result.isPlaylist
                    if (result.isPlaylist) {
                        playlistTitle = result.title
                        playlistCount = result.playlistCount
                    } else if (LIST_PARAM.containsMatchIn(target)) {
                        // A single video shared from inside a playlist: offer the whole list.
                        playlistLookup = true
                        listJob = scope.launch {
                            runCatching { YtdlpEngine.probe(context, target, wholePlaylist = true) }
                                .onSuccess { list ->
                                    if (list.isPlaylist) {
                                        playlistTitle = list.title
                                        playlistCount = list.playlistCount
                                    }
                                }
                            playlistLookup = false
                        }
                    }
                    result.availableHeights.maxOrNull()?.let { maxHeight ->
                        if ((quality.maxHeight ?: Int.MAX_VALUE) > maxHeight) {
                            quality = VideoQuality.entries
                                .filter { it.maxHeight != null && it.maxHeight <= maxHeight }
                                .maxByOrNull { it.maxHeight ?: 0 } ?: VideoQuality.BEST
                        }
                    }
                }
                .onFailure { error = it.message?.lastMeaningfulLine() ?: "Bilgi alınamadı" }
            probing = false
        }
    }

    fun offeredQualities(): List<VideoQuality> {
        val max = probe?.availableHeights?.maxOrNull()
        return VideoQuality.entries.filter { it.maxHeight == null || max == null || it.maxHeight <= max }
    }

    fun buildSpec(): DownloadSpec? {
        val info = probe ?: return null
        return DownloadSpec(
            url = info.url,
            title = if (playlist) playlistTitle ?: info.title else info.title,
            thumbnail = info.thumbnail,
            kind = kind,
            quality = quality,
            container = container,
            audioFormat = audioFormat,
            playlist = playlist,
            embedThumbnail = embedThumbnail,
            embedMetadata = embedMetadata,
            embedSubtitles = embedSubtitles,
            subtitleLanguages = subtitleLanguages,
            sponsorBlock = sponsorBlock,
            targetFolder = targetFolder,
        )
    }
}

private val LIST_PARAM = Regex("""[?&]list=[A-Za-z0-9_-]+""")

@Composable
fun AddDownloadScreen(
    initialUrl: String,
    prefs: Prefs,
    onClose: () -> Unit,
    onEnqueue: (DownloadSpec) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val state = remember { AddDownloadState(initialUrl, prefs) }
    var showLanguages by remember { mutableStateOf(false) }
    val pickFolder = rememberFolderPicker { state.targetFolder = it }

    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Opened empty: put the cursor in the link field, keyboard up, ready to paste.
        if (initialUrl.isNotBlank()) state.startProbe(context, scope) else runCatching { urlFocus.requestFocus() }
    }

    fun fetch() {
        focus.clearFocus()
        state.startProbe(context, scope)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni indirme") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (state.probe != null) {
                        Button(
                            onClick = { state.buildSpec()?.let(onEnqueue) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("İndir")
                        }
                    } else {
                        Button(
                            onClick = ::fetch,
                            enabled = state.url.isNotBlank() && !state.probing,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.probing) "Bilgiler alınıyor…" else "Bilgileri getir")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = state::onUrlChange,
                label = { Text("Bağlantı") },
                placeholder = { Text("https://…") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        readClipboardUrl(context)?.let {
                            state.onUrlChange(it)
                            fetch()
                        }
                    }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Panodan yapıştır")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { fetch() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(urlFocus),
            )

            if (state.probing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Bağlantı inceleniyor", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Biçimler ve kalite seçenekleri alınıyor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.error?.let { message ->
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            state.probe?.let { info ->
                MediaSummary(info, modifier = Modifier.padding(16.dp))
                Options(state, onPickFolder = pickFolder, onPickLanguages = { showLanguages = true })
            }
        }
    }

    if (showLanguages) {
        LanguageDialog(
            initial = state.subtitleLanguages,
            onDismiss = { showLanguages = false },
            onConfirm = {
                state.subtitleLanguages = it
                showLanguages = false
            },
        )
    }
}

@Composable
private fun MediaSummary(info: MediaProbe, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        if (info.thumbnail != null) {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                info.uploader,
                info.durationSeconds.takeIf { it > 0 }?.let(::formatDuration),
                if (info.isPlaylist) (if (info.playlistCount > 0) "${info.playlistCount} öğe" else "Oynatma listesi") else null,
                info.extractor,
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Options(state: AddDownloadState, onPickFolder: () -> Unit, onPickLanguages: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Tür")
        Segmented(
            options = listOf(MediaKind.VIDEO, MediaKind.AUDIO),
            selected = state.kind,
            onSelect = { state.kind = it },
            label = ::kindLabel,
            icon = { if (it == MediaKind.VIDEO) Icons.Filled.Videocam else Icons.Filled.MusicNote },
        )

        if (state.kind == MediaKind.VIDEO) {
            Label("Kalite")
            Chips(state.offeredQualities(), state.quality, { state.quality = it }, { it.label })
            Label("Biçim")
            Segmented(VideoContainer.entries, state.container, { state.container = it }, { it.label })
        } else {
            Label("Ses biçimi")
            Chips(AudioFormat.entries, state.audioFormat, { state.audioFormat = it }, { it.label })
        }
    }

    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

    if (state.offersPlaylist) {
        SwitchItem(
            title = "Oynatma listesinin tamamı",
            subtitle = when {
                state.playlistTitle != null && state.playlistCount > 0 -> "${state.playlistTitle} · ${state.playlistCount} öğe"
                state.playlistTitle != null -> state.playlistTitle
                else -> "Liste bilgisi alınıyor…"
            },
            checked = state.playlist,
            onCheckedChange = { state.playlist = it },
            enabled = state.playlistTitle != null || state.probe?.isPlaylist == true,
        )
    }

    FolderItem(
        title = "Kayıt yeri",
        value = state.targetFolder ?: state.defaultFolder(),
        defaultLabel = if (state.kind == MediaKind.VIDEO) "Movies/Parabolic" else "Music/Parabolic",
        onPick = onPickFolder,
        onReset = if (state.targetFolder != null) ({ state.targetFolder = null }) else null,
    )

    val coverSupported = !(state.kind == MediaKind.VIDEO && state.container == VideoContainer.WEBM)
    SwitchItem(
        title = "Kapak resmini göm",
        subtitle = when {
            !coverSupported -> "WebM'de desteklenmiyor"
            state.kind == MediaKind.AUDIO -> "Kare albüm kapağı olarak"
            else -> null
        },
        checked = state.embedThumbnail && coverSupported,
        onCheckedChange = { state.embedThumbnail = it },
        enabled = coverSupported,
    )
    SwitchItem("Üstveriyi göm", state.embedMetadata, { state.embedMetadata = it }, "Başlık, sanatçı, bölümler")
    if (state.kind == MediaKind.VIDEO) {
        SwitchItem("Altyazıları göm", state.embedSubtitles, { state.embedSubtitles = it })
        if (state.embedSubtitles) {
            ValueItem("Altyazı dilleri", describeLanguages(state.subtitleLanguages), onClick = onPickLanguages)
        }
    }
    SwitchItem("SponsorBlock", state.sponsorBlock, { state.sponsorBlock = it }, "Sponsor ve tanıtım bölümlerini kes")
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    icon: ((T) -> ImageVector)? = null,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    if (option == selected || icon == null) {
                        SegmentedButtonDefaults.Icon(active = option == selected)
                    } else {
                        Icon(icon(option), contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                    }
                },
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

@Composable
private fun <T> Chips(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                leadingIcon = if (option == selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}
