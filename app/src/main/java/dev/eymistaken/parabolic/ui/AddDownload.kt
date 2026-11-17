package dev.eymistaken.parabolic.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import dev.eymistaken.parabolic.ui.components.ConnectedChoiceRow
import dev.eymistaken.parabolic.ui.components.FolderRow
import dev.eymistaken.parabolic.ui.components.rememberFolderPicker
import dev.eymistaken.parabolic.ui.components.LanguagePickerSheet
import dev.eymistaken.parabolic.ui.components.LanguageSummaryRow
import dev.eymistaken.parabolic.ui.components.PanelShape
import dev.eymistaken.parabolic.ui.components.Pill
import dev.eymistaken.parabolic.ui.components.SectionLabel
import dev.eymistaken.parabolic.ui.components.SwitchRow
import dev.eymistaken.parabolic.ui.components.ToggleFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Everything the "new download" form knows; shared by the sheet and the tablet panel. */
@Stable
class AddDownloadState(initialUrl: String, private val prefs: Prefs) {
    var url by mutableStateOf(initialUrl)
    var probing by mutableStateOf(false)
        private set
    var probe by mutableStateOf<MediaProbe?>(null)
        private set
    var error by mutableStateOf<String?>(null)

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

    /** Per-download folder override (SAF tree URI); null = the default for the kind. */
    var targetFolder by mutableStateOf<String?>(null)

    /** Set when the link belongs to a playlist (a list page, or watch?v=…&list=…). */
    var playlistTitle by mutableStateOf<String?>(null)
        private set
    var playlistCount by mutableStateOf(0)
        private set
    var playlistLookup by mutableStateOf(false)
        private set
    val offersPlaylist: Boolean get() = playlistTitle != null || playlistLookup

    fun defaultFolder(): String? = prefs.folderFor(kind)

    private var job: Job? = null
    private var listJob: Job? = null

    fun startProbe(context: Context, scope: CoroutineScope) {
        val target = extractUrl(url) ?: url.trim()
        if (target.isBlank()) return
        url = target
        probing = true
        error = null
        probe = null
        playlistTitle = null
        playlistCount = 0
        job?.cancel()
        listJob?.cancel()
        job = scope.launch {
            runCatching { YtdlpEngine.probe(context, target) }
                .onSuccess { result ->
                    probe = result
                    playlist = result.isPlaylist
                    if (result.isPlaylist) {
                        playlistTitle = result.title
                        playlistCount = result.playlistCount
                    } else if (LIST_PARAM.containsMatchIn(target)) {
                        // A single video shared from inside a playlist: offer the
                        // whole list, looked up in the background (flat, so quick).
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

    fun reset() {
        job?.cancel()
        listJob?.cancel()
        playlistTitle = null
        playlistCount = 0
        playlistLookup = false
        targetFolder = null
        url = ""
        probe = null
        error = null
        probing = false
        playlist = false
    }
}

private val LIST_PARAM = Regex("""[?&]list=[A-Za-z0-9_-]+""")

private enum class Preset(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    BestVideo("En iyi kalite (MKV)", Icons.Filled.HighQuality),
    Phone1080("1080p MP4", Icons.Filled.Videocam),
    Mp3("Sadece ses (MP3)", Icons.Filled.MusicNote),
    Opus("Sadece ses (Opus)", Icons.Filled.AudioFile),
}

private fun AddDownloadState.usePreset(preset: Preset) {
    when (preset) {
        Preset.BestVideo -> { kind = MediaKind.VIDEO; quality = VideoQuality.BEST; container = VideoContainer.MKV }
        Preset.Phone1080 -> { kind = MediaKind.VIDEO; quality = VideoQuality.FHD; container = VideoContainer.MP4 }
        Preset.Mp3 -> { kind = MediaKind.AUDIO; audioFormat = AudioFormat.MP3 }
        Preset.Opus -> { kind = MediaKind.AUDIO; audioFormat = AudioFormat.OPUS }
    }
}

@Composable
fun AddDownloadForm(
    state: AddDownloadState,
    onEnqueue: (DownloadSpec) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = "Yeni indirme",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExtras by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    val pickFolder = rememberFolderPicker { state.targetFolder = it }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        title?.let {
            Text(it, style = MaterialTheme.typography.headlineMediumEmphasized)
        }

        // -- link field ----------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.url,
                onValueChange = { state.url = it; state.error = null },
                placeholder = { Text("Bağlantı yapıştır") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.startProbe(context, scope) }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = {
                    readClipboardUrl(context)?.let {
                        state.url = it
                        state.startProbe(context, scope)
                    }
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = "Panodan yapıştır")
            }
        }

        AnimatedVisibility(
            visible = state.probe == null && !state.probing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Button(
                onClick = { state.startProbe(context, scope) },
                enabled = state.url.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bilgileri getir", style = MaterialTheme.typography.titleMedium)
            }
        }

        // -- probing / error -----------------------------------------------
        AnimatedVisibility(
            visible = state.probing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
                Column {
                    Text("Kaynak çözümleniyor", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        "Biçimler ve kalite seçenekleri alınıyor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(visible = state.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        // -- result ----------------------------------------------------------
        AnimatedVisibility(
            visible = state.probe != null,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val info = state.probe ?: return@AnimatedVisibility
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MediaHeader(info)

                ConnectedChoiceRow(
                    options = listOf(MediaKind.VIDEO, MediaKind.AUDIO),
                    selected = state.kind,
                    onSelect = { state.kind = it },
                    label = { if (it == MediaKind.VIDEO) "Video" else "Ses" },
                    icon = { if (it == MediaKind.VIDEO) Icons.Filled.Videocam else Icons.Filled.MusicNote },
                )

                val enterFx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val exitFx = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedContent(
                    targetState = state.kind,
                    transitionSpec = { fadeIn(enterFx) togetherWith fadeOut(exitFx) },
                    label = "kindOptions",
                ) { kind ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (kind == MediaKind.VIDEO) {
                            SectionLabel("Kalite")
                            ToggleFlow(
                                options = state.offeredQualities(),
                                selected = state.quality,
                                onSelect = { state.quality = it },
                                label = { it.label },
                            )
                            SectionLabel("Kap")
                            ConnectedChoiceRow(
                                options = VideoContainer.entries,
                                selected = state.container,
                                onSelect = { state.container = it },
                                label = { it.label },
                            )
                        } else {
                            SectionLabel("Ses biçimi")
                            ToggleFlow(
                                options = AudioFormat.entries,
                                selected = state.audioFormat,
                                onSelect = { state.audioFormat = it },
                                label = { it.label },
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = state.offersPlaylist,
                    enter = fadeIn() + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    SwitchRow(
                        title = "Oynatma listesinin tamamı",
                        subtitle = when {
                            state.playlistTitle != null && state.playlistCount > 0 ->
                                "${state.playlistTitle} · ${state.playlistCount} öğe"
                            state.playlistTitle != null -> state.playlistTitle
                            else -> "Liste bilgisi alınıyor…"
                        },
                        checked = state.playlist,
                        onCheckedChange = { state.playlist = it },
                        enabled = state.playlistTitle != null || state.probe?.isPlaylist == true,
                    )
                }

                FolderRow(
                    title = "Kayıt yeri",
                    value = state.targetFolder ?: state.defaultFolder(),
                    defaultLabel = if (state.kind == MediaKind.VIDEO) "Movies/Parabolic" else "Music/Parabolic",
                    onPick = pickFolder,
                    onReset = if (state.targetFolder != null) ({ state.targetFolder = null }) else null,
                )

                ExtrasHeader(expanded = showExtras, onToggle = { showExtras = !showExtras })
                AnimatedVisibility(
                    visible = showExtras,
                    enter = fadeIn() + expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                    exit = fadeOut() + shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val coverSupported = !(state.kind == MediaKind.VIDEO && state.container == VideoContainer.WEBM)
                        SwitchRow(
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
                        SwitchRow("Üstveriyi göm", "Başlık, sanatçı, bölümler", state.embedMetadata, { state.embedMetadata = it })
                        if (state.kind == MediaKind.VIDEO) {
                            SwitchRow("Altyazıları göm", null, state.embedSubtitles, { state.embedSubtitles = it })
                            AnimatedVisibility(visible = state.embedSubtitles) {
                                LanguageSummaryRow(
                                    value = state.subtitleLanguages,
                                    onClick = { showLanguages = true },
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                        }
                        SwitchRow("SponsorBlock", "Sponsor ve tanıtım bölümlerini kes", state.sponsorBlock, { state.sponsorBlock = it })
                    }
                }

                // -- split action: download + quick presets --------------------
                Box {
                    SplitButtonLayout(
                        modifier = Modifier.fillMaxWidth(),
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                onClick = { state.buildSpec()?.let(onEnqueue) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = SplitButtonDefaults.MediumContainerHeight),
                                shapes = SplitButtonDefaults.leadingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(SplitButtonDefaults.MediumContainerHeight),
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Text("İndir", style = MaterialTheme.typography.titleMediumEmphasized)
                            }
                        },
                        trailingButton = {
                            val rotation by animateFloatAsState(
                                if (presetMenu) 180f else 0f,
                                MaterialTheme.motionScheme.fastSpatialSpec(),
                                label = "chevron",
                            )
                            SplitButtonDefaults.TrailingButton(
                                checked = presetMenu,
                                onCheckedChange = { presetMenu = it },
                                modifier = Modifier.heightIn(min = SplitButtonDefaults.MediumContainerHeight),
                                shapes = SplitButtonDefaults.trailingButtonShapesFor(SplitButtonDefaults.MediumContainerHeight),
                                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(SplitButtonDefaults.MediumContainerHeight),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Hızlı seçenekler",
                                    modifier = Modifier.rotate(rotation),
                                )
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = presetMenu,
                        onDismissRequest = { presetMenu = false },
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Preset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                leadingIcon = { Icon(preset.icon, contentDescription = null) },
                                onClick = {
                                    presetMenu = false
                                    state.usePreset(preset)
                                    state.buildSpec()?.let(onEnqueue)
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showLanguages) {
        LanguagePickerSheet(
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
private fun ExtrasHeader(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "extras",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text("Ek seçenekler", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(rotation))
    }
}

@Composable
private fun MediaHeader(info: MediaProbe) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(36.dp)),
            ) {
                if (info.thumbnail != null) {
                    AsyncImage(
                        model = info.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (info.durationSeconds > 0) {
                        Pill(
                            text = formatDuration(info.durationSeconds),
                            container = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                            content = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                    if (info.isPlaylist) {
                        Pill(
                            text = if (info.playlistCount > 0) "${info.playlistCount} öğe" else "Liste",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            leading = {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            },
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(info.uploader, info.extractor).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Phone / small tablet: the form in a bottom sheet. */
@Composable
fun AddDownloadSheet(
    initialUrl: String,
    prefs: Prefs,
    onDismiss: () -> Unit,
    onEnqueue: (DownloadSpec) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { AddDownloadState(initialUrl, prefs) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) {
        if (initialUrl.isNotBlank()) state.startProbe(context, scope)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AddDownloadForm(
            state = state,
            onEnqueue = onEnqueue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding(),
        )
    }
}

/** Wide screens: the form lives permanently beside the list. */
@Composable
fun AddDownloadPanel(
    state: AddDownloadState,
    onEnqueue: (DownloadSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AddDownloadForm(
            state = state,
            onEnqueue = onEnqueue,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .imePadding(),
        )
    }
}

private fun readClipboardUrl(context: Context): String? {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
    val text = manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    return extractUrl(text) ?: text?.trim()?.takeIf { it.startsWith("http") }
}
