package dev.eymistaken.parabolic.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KProperty

/**
 * Settings that are both observable from Compose and persisted immediately.
 * Deliberately a thin wrapper over SharedPreferences: there is no migration
 * story to worry about and nothing here is worth a DataStore dependency.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("parabolic_prefs", Context.MODE_PRIVATE)

    private inner class BoolPref(val key: String, default: Boolean) {
        private var state by mutableStateOf(sp.getBoolean(key, default))
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state = value
            sp.edit().putBoolean(key, value).apply()
        }
    }

    private inner class IntPref(val key: String, default: Int) {
        private var state by mutableStateOf(sp.getInt(key, default))
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = state
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state = value
            sp.edit().putInt(key, value).apply()
        }
    }

    private inner class StringPref(val key: String, default: String) {
        private var state by mutableStateOf(sp.getString(key, default) ?: default)
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String = state
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state = value
            sp.edit().putString(key, value).apply()
        }
    }

    // --- appearance -------------------------------------------------------
    var dynamicColor: Boolean by BoolPref("dynamic_color", true)
    var themeRaw: String by StringPref("theme", ThemeOption.SYSTEM.name)
    var theme: ThemeOption
        get() = runCatching { ThemeOption.valueOf(themeRaw) }.getOrDefault(ThemeOption.SYSTEM)
        set(value) { themeRaw = value.name }

    // --- download defaults ------------------------------------------------
    var defaultKindRaw: String by StringPref("default_kind", MediaKind.VIDEO.name)
    var defaultKind: MediaKind
        get() = runCatching { MediaKind.valueOf(defaultKindRaw) }.getOrDefault(MediaKind.VIDEO)
        set(value) { defaultKindRaw = value.name }

    var defaultQualityRaw: String by StringPref("default_quality", VideoQuality.FHD.name)
    var defaultQuality: VideoQuality
        get() = runCatching { VideoQuality.valueOf(defaultQualityRaw) }.getOrDefault(VideoQuality.FHD)
        set(value) { defaultQualityRaw = value.name }

    var defaultContainerRaw: String by StringPref("default_container", VideoContainer.MP4.name)
    var defaultContainer: VideoContainer
        get() = runCatching { VideoContainer.valueOf(defaultContainerRaw) }.getOrDefault(VideoContainer.MP4)
        set(value) { defaultContainerRaw = value.name }

    var defaultAudioRaw: String by StringPref("default_audio", AudioFormat.MP3.name)
    var defaultAudioFormat: AudioFormat
        get() = runCatching { AudioFormat.valueOf(defaultAudioRaw) }.getOrDefault(AudioFormat.MP3)
        set(value) { defaultAudioRaw = value.name }

    // --- where files go ("" = the default Movies/Parabolic, Music/Parabolic) ---
    var videoFolder: String by StringPref("video_folder", "")
    var audioFolder: String by StringPref("audio_folder", "")

    fun folderFor(kind: MediaKind): String? =
        (if (kind == MediaKind.VIDEO) videoFolder else audioFolder).ifBlank { null }

    var embedThumbnail: Boolean by BoolPref("embed_thumbnail", true)
    var embedMetadata: Boolean by BoolPref("embed_metadata", true)
    var embedSubtitles: Boolean by BoolPref("embed_subtitles", false)
    var subtitleLanguages: String by StringPref("subtitle_languages", "tr,en")
    var sponsorBlock: Boolean by BoolPref("sponsorblock", false)

    // --- performance ------------------------------------------------------
    var concurrentDownloads: Int by IntPref("concurrent_downloads", 2)
    var concurrentFragments: Int by IntPref("concurrent_fragments", 8)
    var useAria2c: Boolean by BoolPref("use_aria2c", false)

    // --- yt-dlp -----------------------------------------------------------
    var updateChannelRaw: String by StringPref("update_channel", UpdateChannelOption.NIGHTLY.name)
    var updateChannel: UpdateChannelOption
        get() = runCatching { UpdateChannelOption.valueOf(updateChannelRaw) }
            .getOrDefault(UpdateChannelOption.NIGHTLY)
        set(value) { updateChannelRaw = value.name }

    var autoUpdateOnLaunch: Boolean by BoolPref("auto_update", true)
    var lastAutoUpdateCheck: Long
        get() = sp.getLong("last_auto_update", 0)
        set(value) { sp.edit().putLong("last_auto_update", value).apply() }

    fun specDefaults(url: String, title: String, thumbnail: String?): DownloadSpec = DownloadSpec(
        url = url,
        title = title,
        thumbnail = thumbnail,
        kind = defaultKind,
        quality = defaultQuality,
        container = defaultContainer,
        audioFormat = defaultAudioFormat,
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        subtitleLanguages = subtitleLanguages,
        sponsorBlock = sponsorBlock,
    )
}
