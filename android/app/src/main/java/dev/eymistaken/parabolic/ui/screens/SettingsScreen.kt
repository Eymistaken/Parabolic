package dev.eymistaken.parabolic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.eymistaken.parabolic.core.AudioFormat
import dev.eymistaken.parabolic.core.MediaKind
import dev.eymistaken.parabolic.core.Prefs
import dev.eymistaken.parabolic.core.ThemeOption
import dev.eymistaken.parabolic.core.UpdateChannelOption
import dev.eymistaken.parabolic.core.VideoContainer
import dev.eymistaken.parabolic.core.VideoQuality
import dev.eymistaken.parabolic.core.YtdlpEngine
import dev.eymistaken.parabolic.core.YtdlpUpdateProgress
import dev.eymistaken.parabolic.ui.components.DownloadProgressBar
import dev.eymistaken.parabolic.ui.components.ConnectedChoiceRow
import dev.eymistaken.parabolic.ui.components.FolderRow
import dev.eymistaken.parabolic.ui.components.rememberFolderPicker
import dev.eymistaken.parabolic.ui.components.LanguagePickerSheet
import dev.eymistaken.parabolic.ui.components.LanguageSummaryRow
import dev.eymistaken.parabolic.ui.components.ParabolicLogo
import dev.eymistaken.parabolic.ui.components.Pill
import dev.eymistaken.parabolic.ui.components.ProgressPercent
import dev.eymistaken.parabolic.ui.components.SectionCard
import dev.eymistaken.parabolic.ui.components.SectionLabel
import dev.eymistaken.parabolic.ui.components.SliderSetting
import dev.eymistaken.parabolic.ui.components.SwitchRow
import dev.eymistaken.parabolic.ui.components.ToggleFlow
import dev.eymistaken.parabolic.ui.components.rememberShownUpdate
import dev.eymistaken.parabolic.ui.formatBytes
import dev.eymistaken.parabolic.ui.theme.dynamicColorSupported
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefs: Prefs,
    contentPadding: PaddingValues,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by YtdlpEngine.updateProgress.collectAsStateWithLifecycle()
    val shownUpdate = rememberShownUpdate(update)
    var requested by remember { mutableStateOf(false) }
    val updating = requested || update != null
    var ytdlpVersion by remember { mutableStateOf<String?>(null) }
    var showLanguages by remember { mutableStateOf(false) }

    // Read again whenever an update (manual or the daily one) finishes.
    LaunchedEffect(update == null) { ytdlpVersion = YtdlpEngine.ytdlpVersion(context) }
    val pickVideoFolder = rememberFolderPicker { prefs.videoFolder = it }
    val pickAudioFolder = rememberFolderPicker { prefs.audioFolder = it }

    // Cards flow into as many columns as the width allows: one on a phone,
    // two or three on a tablet, so a wide screen never shows a lonely strip.
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(340.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 120.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "look") {
            SectionCard("Görünüm", icon = Icons.Filled.Palette) {
                SectionLabel("Tema")
                ConnectedChoiceRow(
                    options = ThemeOption.entries,
                    selected = prefs.theme,
                    onSelect = { prefs.theme = it },
                    label = { it.label },
                    icon = {
                        when (it) {
                            ThemeOption.SYSTEM -> Icons.Filled.PhoneAndroid
                            ThemeOption.LIGHT -> Icons.Filled.LightMode
                            ThemeOption.DARK -> Icons.Filled.DarkMode
                        }
                    },
                )
                SwitchRow(
                    title = "Dinamik renkler",
                    subtitle = if (dynamicColorSupported) "Duvar kağıdından Material You paleti" else "Bu Android sürümünde yok",
                    checked = prefs.dynamicColor && dynamicColorSupported,
                    onCheckedChange = { prefs.dynamicColor = it },
                    enabled = dynamicColorSupported,
                )
            }
        }

        item(key = "defaults") {
            SectionCard("İndirme varsayılanları", icon = Icons.Filled.Download) {
                SectionLabel("Tür")
                ConnectedChoiceRow(
                    options = MediaKind.entries,
                    selected = prefs.defaultKind,
                    onSelect = { prefs.defaultKind = it },
                    label = { if (it == MediaKind.VIDEO) "Video" else "Ses" },
                    icon = { if (it == MediaKind.VIDEO) Icons.Filled.Videocam else Icons.Filled.MusicNote },
                )
                SectionLabel("Video kalitesi")
                ToggleFlow(
                    options = VideoQuality.entries,
                    selected = prefs.defaultQuality,
                    onSelect = { prefs.defaultQuality = it },
                    label = { it.label },
                )
                SectionLabel("Video kabı")
                ConnectedChoiceRow(
                    options = VideoContainer.entries,
                    selected = prefs.defaultContainer,
                    onSelect = { prefs.defaultContainer = it },
                    label = { it.label },
                )
                SectionLabel("Ses biçimi")
                ToggleFlow(
                    options = AudioFormat.entries,
                    selected = prefs.defaultAudioFormat,
                    onSelect = { prefs.defaultAudioFormat = it },
                    label = { it.label },
                )
            }
        }

        item(key = "folders") {
            SectionCard("Kayıt konumu", icon = Icons.Filled.Folder) {
                FolderRow(
                    title = "Videolar",
                    value = prefs.videoFolder,
                    defaultLabel = "Movies/Parabolic",
                    onPick = pickVideoFolder,
                    onReset = { prefs.videoFolder = "" },
                )
                FolderRow(
                    title = "Sesler",
                    value = prefs.audioFolder,
                    defaultLabel = "Music/Parabolic",
                    onPick = pickAudioFolder,
                    onReset = { prefs.audioFolder = "" },
                )
                Text(
                    "Oynatma listeleri seçilen klasörde kendi alt klasörüne kaydedilir. " +
                        "Tek bir indirme için yeni indirme panelinden başka bir klasör seçebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "embed") {
            SectionCard("Gömülü içerik", icon = Icons.Filled.Terminal) {
                SwitchRow("Kapak resmini göm", null, prefs.embedThumbnail, { prefs.embedThumbnail = it })
                SwitchRow("Üstveriyi göm", "Başlık, sanatçı, bölümler", prefs.embedMetadata, { prefs.embedMetadata = it })
                SwitchRow("Altyazıları göm", "Videolara, seçili dillerde", prefs.embedSubtitles, { prefs.embedSubtitles = it })
                LanguageSummaryRow(value = prefs.subtitleLanguages, onClick = { showLanguages = true })
                SwitchRow("SponsorBlock", "Sponsor bölümlerini otomatik kes", prefs.sponsorBlock, { prefs.sponsorBlock = it })
            }
        }

        item(key = "speed") {
            SectionCard("Hız", icon = Icons.Filled.Bolt) {
                SliderSetting(
                    label = "Eşzamanlı indirme",
                    supporting = "Aynı anda çalışan indirme sayısı",
                    value = prefs.concurrentDownloads,
                    range = 1..6,
                    onChange = { prefs.concurrentDownloads = it },
                )
                SliderSetting(
                    label = "Paralel parça",
                    supporting = "Tek videonun kaç parçadan birden çekileceği",
                    value = prefs.concurrentFragments,
                    range = 1..16,
                    onChange = { prefs.concurrentFragments = it },
                )
                SwitchRow("aria2c ile indir", "Bazı kaynaklarda daha hızlı, bazılarında sorunlu", prefs.useAria2c, { prefs.useAria2c = it })
            }
        }

        item(key = "ytdlp") {
            SectionCard("yt-dlp", icon = Icons.Filled.Refresh) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sürüm", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AnimatedContent(
                            targetState = ytdlpVersion ?: "gömülü",
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "version",
                        ) { version ->
                            Text(version, style = MaterialTheme.typography.titleLargeEmphasized)
                        }
                    }
                }
                SectionLabel("Güncelleme kanalı")
                ConnectedChoiceRow(
                    options = UpdateChannelOption.entries,
                    selected = prefs.updateChannel,
                    onSelect = { prefs.updateChannel = it },
                    label = { it.label },
                )
                SwitchRow(
                    title = "Günde bir otomatik güncelle",
                    subtitle = "Yalnızca Wi‑Fi'dayken; mobil veride atlanır",
                    checked = prefs.autoUpdateOnLaunch,
                    onCheckedChange = { prefs.autoUpdateOnLaunch = it },
                )
                // Also shows the daily automatic update when it happens to run now.
                AnimatedVisibility(
                    visible = shownUpdate.visible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    shownUpdate.progress?.let { UpdateProgressSection(it) }
                }
                Button(
                    onClick = {
                        requested = true
                        scope.launch {
                            val result = try {
                                Result.success(YtdlpEngine.updateYtdlp(context, prefs.updateChannel))
                            } catch (cancelled: CancellationException) {
                                // The screen went away; the update itself carries on.
                                throw cancelled
                            } catch (failure: Exception) {
                                Result.failure(failure)
                            } finally {
                                requested = false
                            }
                            ytdlpVersion = YtdlpEngine.ytdlpVersion(context)
                            onMessage(result.getOrElse { "Güncelleme başarısız: ${it.message ?: "bilinmeyen hata"}" })
                        }
                    },
                    enabled = !updating,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (updating) "Güncelleniyor" else "Şimdi güncelle")
                }
            }
        }

        item(key = "about") {
            SectionCard("Hakkında", icon = Icons.Filled.Info) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ParabolicLogo(modifier = Modifier.size(56.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Parabolic", style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(
                            "yt-dlp ile video ve ses indirici",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "NickvisionApps/Parabolic'in iş akışını temel alan bağımsız bir Android istemcisi. " +
                        "İndirmeyi cihaz üzerinde çalışan yt-dlp yapar; YouTube'un JS doğrulamaları QuickJS ile çözülür.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showLanguages) {
        LanguagePickerSheet(
            initial = prefs.subtitleLanguages,
            onDismiss = { showLanguages = false },
            onConfirm = {
                prefs.subtitleLanguages = it
                showLanguages = false
            },
        )
    }
}


/** Stage, percentage, bar and byte count of a yt-dlp update, laid out like a download card's. */
@Composable
private fun UpdateProgressSection(update: YtdlpUpdateProgress) {
    val fraction = update.fraction
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(
                text = update.stage.label,
                leading = if (fraction == null) {
                    { LoadingIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSecondaryContainer) }
                } else {
                    null
                },
            )
            Spacer(Modifier.weight(1f))
            if (fraction != null) ProgressPercent(fraction)
        }
        Spacer(Modifier.height(10.dp))
        DownloadProgressBar(fraction)
        val meta = listOfNotNull(
            update.version,
            when {
                update.totalBytes > 0 -> "${formatBytes(update.downloadedBytes)} / ${formatBytes(update.totalBytes)}"
                update.downloadedBytes > 0 -> formatBytes(update.downloadedBytes)
                else -> null
            },
        )
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
