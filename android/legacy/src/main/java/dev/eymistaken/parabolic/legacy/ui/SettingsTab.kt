package dev.eymistaken.parabolic.legacy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.eymistaken.parabolic.BuildConfig
import dev.eymistaken.parabolic.R
import dev.eymistaken.parabolic.core.AudioFormat
import dev.eymistaken.parabolic.core.MediaKind
import dev.eymistaken.parabolic.core.Prefs
import dev.eymistaken.parabolic.core.ThemeOption
import dev.eymistaken.parabolic.core.UpdateChannelOption
import dev.eymistaken.parabolic.core.VideoContainer
import dev.eymistaken.parabolic.core.VideoQuality
import dev.eymistaken.parabolic.core.YoutubeAccount
import dev.eymistaken.parabolic.core.YtdlpEngine
import dev.eymistaken.parabolic.core.YtdlpUpdateProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Which choice dialog is open, if any. */
private enum class Picker { Theme, Kind, Quality, Container, Audio, Channel, Languages }

@Composable
fun SettingsTab(
    prefs: Prefs,
    contentPadding: PaddingValues,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<Picker?>(null) }
    var showSignIn by remember { mutableStateOf(false) }
    val youtubeSignedIn by YoutubeAccount.signedIn.collectAsStateWithLifecycle()
    val update by YtdlpEngine.updateProgress.collectAsStateWithLifecycle()
    val shownUpdate = rememberShownUpdate(update)
    var requested by remember { mutableStateOf(false) }
    val updating = requested || update != null
    var ytdlpVersion by remember { mutableStateOf<String?>(null) }
    // Read again whenever an update (manual or the daily one) finishes.
    LaunchedEffect(update == null) { ytdlpVersion = YtdlpEngine.ytdlpVersion(context) }
    val pickVideoFolder = rememberFolderPicker { prefs.videoFolder = it }
    val pickAudioFolder = rememberFolderPicker { prefs.audioFolder = it }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item { SectionHeader("Görünüm") }
        item { ValueItem("Tema", prefs.theme.label, onClick = { picker = Picker.Theme }) }
        item {
            SwitchItem(
                title = "Dinamik renkler",
                subtitle = if (dynamicColorSupported) "Renkleri duvar kağıdından al" else "Android 12 ve üstünde kullanılabilir",
                checked = prefs.dynamicColor && dynamicColorSupported,
                onCheckedChange = { prefs.dynamicColor = it },
                enabled = dynamicColorSupported,
            )
        }

        item { SectionHeader("İndirme varsayılanları") }
        item { ValueItem("Tür", kindLabel(prefs.defaultKind), onClick = { picker = Picker.Kind }) }
        item { ValueItem("Video kalitesi", prefs.defaultQuality.label, onClick = { picker = Picker.Quality }) }
        item { ValueItem("Video biçimi", prefs.defaultContainer.label, onClick = { picker = Picker.Container }) }
        item { ValueItem("Ses biçimi", prefs.defaultAudioFormat.label, onClick = { picker = Picker.Audio }) }

        item { SectionHeader("Kayıt konumu") }
        item {
            FolderItem("Videolar", prefs.videoFolder, "Movies/Parabolic", pickVideoFolder) { prefs.videoFolder = "" }
        }
        item {
            FolderItem("Sesler", prefs.audioFolder, "Music/Parabolic", pickAudioFolder) { prefs.audioFolder = "" }
        }

        item { SectionHeader("Gömülü içerik") }
        item { SwitchItem("Kapak resmini göm", prefs.embedThumbnail, { prefs.embedThumbnail = it }) }
        item { SwitchItem("Üstveriyi göm", prefs.embedMetadata, { prefs.embedMetadata = it }, "Başlık, sanatçı, bölümler") }
        item { SwitchItem("Altyazıları göm", prefs.embedSubtitles, { prefs.embedSubtitles = it }, "Yalnızca videolarda") }
        item {
            ValueItem(
                "Altyazı dilleri",
                describeLanguages(prefs.subtitleLanguages),
                onClick = { picker = Picker.Languages },
                enabled = prefs.embedSubtitles,
            )
        }
        item { SwitchItem("SponsorBlock", prefs.sponsorBlock, { prefs.sponsorBlock = it }, "Sponsor bölümlerini otomatik kes") }

        item { SectionHeader("Hız") }
        item {
            SliderItem(
                title = "Eşzamanlı indirme",
                value = prefs.concurrentDownloads,
                range = 1..6,
                onChange = { prefs.concurrentDownloads = it },
            )
        }
        item {
            SliderItem(
                title = "Paralel parça",
                value = prefs.concurrentFragments,
                range = 1..16,
                onChange = { prefs.concurrentFragments = it },
            )
        }
        item { SwitchItem("aria2c ile indir", prefs.useAria2c, { prefs.useAria2c = it }, "Bazı sitelerde daha hızlı") }

        item { SectionHeader("yt-dlp") }
        item {
            ListItem(
                modifier = Modifier.clickable(enabled = !updating) {
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
                headlineContent = { Text(if (updating) "Güncelleniyor…" else "Şimdi güncelle") },
                supportingContent = { Text("Yüklü sürüm: ${ytdlpVersion ?: "gömülü"}") },
                trailingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            )
        }
        // Also shows the daily automatic update when it happens to run now.
        item {
            AnimatedVisibility(
                visible = shownUpdate.visible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                shownUpdate.progress?.let { UpdateProgressRow(it) }
            }
        }
        item { ValueItem("Güncelleme kanalı", prefs.updateChannel.label, onClick = { picker = Picker.Channel }) }
        item {
            SwitchItem(
                "Günde bir otomatik güncelle",
                prefs.autoUpdateOnLaunch,
                { prefs.autoUpdateOnLaunch = it },
                "Yalnızca Wi-Fi'dayken",
            )
        }

        item { SectionHeader("YouTube hesabı") }
        item {
            ListItem(
                modifier = Modifier.clickable {
                    if (youtubeSignedIn) {
                        YoutubeAccount.signOut(context)
                        onMessage("YouTube hesabından çıkıldı")
                    } else {
                        showSignIn = true
                    }
                },
                headlineContent = { Text(if (youtubeSignedIn) "Çıkış yap" else "YouTube'a giriş yap") },
                supportingContent = {
                    Text(
                        if (youtubeSignedIn) {
                            "Giriş yapıldı. yt-dlp bu hesabın çerezlerini kullanıyor."
                        } else {
                            "YouTube \"bot olmadığını doğrula\" diyerek indirmeyi durdurursa giriş yapınca " +
                                "genellikle düzelir. Çerezler yalnızca bu cihazda saklanır; yoğun kullanım için " +
                                "ikinci bir hesap önerilir."
                        },
                    )
                },
                trailingContent = {
                    Icon(
                        if (youtubeSignedIn) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                    )
                },
            )
        }

        item { SectionHeader("Hakkında") }
        item {
            ListItem(
                leadingContent = {
                    Image(
                        painter = painterResource(R.drawable.parabolic_logo),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                },
                headlineContent = { Text("Parabolic ${BuildConfig.VERSION_NAME}") },
                supportingContent = {
                    Text("yt-dlp ile video ve ses indirici. Bu sürüm Android 11 ve öncesi için sade arayüzle derlenmiştir.")
                },
            )
        }
    }

    if (showSignIn) {
        YoutubeSignInDialog(
            onDismiss = { showSignIn = false },
            onSignedIn = {
                showSignIn = false
                onMessage("YouTube hesabına giriş yapıldı")
            },
        )
    }

    when (picker) {
        Picker.Theme -> ChoiceDialog(
            "Tema", ThemeOption.entries, prefs.theme, { it.label }, { prefs.theme = it }, { picker = null },
        )
        Picker.Kind -> ChoiceDialog(
            "Tür", MediaKind.entries, prefs.defaultKind, ::kindLabel, { prefs.defaultKind = it }, { picker = null },
        )
        Picker.Quality -> ChoiceDialog(
            "Video kalitesi", VideoQuality.entries, prefs.defaultQuality, { it.label }, { prefs.defaultQuality = it }, { picker = null },
        )
        Picker.Container -> ChoiceDialog(
            "Video biçimi", VideoContainer.entries, prefs.defaultContainer, { it.label }, { prefs.defaultContainer = it }, { picker = null },
        )
        Picker.Audio -> ChoiceDialog(
            "Ses biçimi", AudioFormat.entries, prefs.defaultAudioFormat, { it.label }, { prefs.defaultAudioFormat = it }, { picker = null },
        )
        Picker.Channel -> ChoiceDialog(
            "Güncelleme kanalı", UpdateChannelOption.entries, prefs.updateChannel, { it.label }, { prefs.updateChannel = it }, { picker = null },
        )
        Picker.Languages -> LanguageDialog(
            initial = prefs.subtitleLanguages,
            onDismiss = { picker = null },
            onConfirm = {
                prefs.subtitleLanguages = it
                picker = null
            },
        )
        null -> Unit
    }
}

fun kindLabel(kind: MediaKind): String = if (kind == MediaKind.VIDEO) "Video" else "Ses"

/** Inline slider in the SeekBarPreference style; the value is saved on release. */
@Composable
private fun SliderItem(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                current.roundToInt().toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(current.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

/** Stage, percentage, bar and byte count of a yt-dlp update, laid out like a download row's. */
@Composable
private fun UpdateProgressRow(update: YtdlpUpdateProgress) {
    val fraction = update.fraction
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = update.stage.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (fraction != null) {
                Text(
                    text = "%${(fraction * 100).toInt()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TaskProgressBar(fraction)
        val meta = listOfNotNull(
            update.version,
            when {
                update.totalBytes > 0 -> "${formatBytes(update.downloadedBytes)} / ${formatBytes(update.totalBytes)}"
                update.downloadedBytes > 0 -> formatBytes(update.downloadedBytes)
                else -> null
            },
        )
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
