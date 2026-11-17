package dev.eymistaken.parabolic.core

import kotlinx.serialization.Serializable

enum class MediaKind { VIDEO, AUDIO }

enum class VideoQuality(val label: String, val maxHeight: Int?) {
    BEST("En iyi", null),
    UHD("2160p", 2160),
    QHD("1440p", 1440),
    FHD("1080p", 1080),
    HD("720p", 720),
    SD("480p", 480),
    LOW("360p", 360);

    companion object {
        fun forHeight(height: Int): VideoQuality? = entries.firstOrNull { it.maxHeight == height }
    }
}

enum class VideoContainer(val label: String, val ext: String) {
    MP4("MP4", "mp4"),
    MKV("MKV", "mkv"),
    WEBM("WebM", "webm"),
}

enum class AudioFormat(val label: String, val code: String) {
    MP3("MP3", "mp3"),
    M4A("M4A", "m4a"),
    OPUS("Opus", "opus"),
    FLAC("FLAC", "flac"),
    WAV("WAV", "wav"),
    SOURCE("Kaynak", "best"),
}

enum class UpdateChannelOption(val label: String) {
    STABLE("Kararlı"),
    NIGHTLY("Nightly"),
    MASTER("Master"),
}

enum class ThemeOption(val label: String) {
    SYSTEM("Sistem"),
    LIGHT("Açık"),
    DARK("Koyu"),
}

/** What the probe step managed to learn about a URL before anything is downloaded. */
data class MediaProbe(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSeconds: Int,
    val isPlaylist: Boolean,
    val playlistCount: Int,
    val availableHeights: List<Int>,
    val extractor: String?,
    val hasAudioOnly: Boolean,
)

@Serializable
data class DownloadSpec(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val kind: MediaKind = MediaKind.VIDEO,
    val quality: VideoQuality = VideoQuality.FHD,
    val container: VideoContainer = VideoContainer.MP4,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val playlist: Boolean = false,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val embedSubtitles: Boolean = false,
    val subtitleLanguages: String = "tr,en",
    val sponsorBlock: Boolean = false,
    val cropAudioThumbnail: Boolean = true,
    /** A folder the user picked (SAF tree URI) for this download; null = the default. */
    val targetFolder: String? = null,
)

enum class DownloadState {
    QUEUED,
    RUNNING,
    PROCESSING,
    SAVING,
    DONE,
    FAILED,
    CANCELED;

    val isTerminal: Boolean get() = this == DONE || this == FAILED || this == CANCELED
    val isActive: Boolean get() = !isTerminal
}

/** Short, human phase of a download; the raw yt-dlp output lives in [DownloadTask.log]. */
enum class DownloadStage(val label: String) {
    WAITING("Sırada"),
    PREPARING("Hazırlanıyor"),
    VIDEO("Video indiriliyor"),
    AUDIO("Ses indiriliyor"),
    DOWNLOADING("İndiriliyor"),
    MERGING("Birleştiriliyor"),
    CONVERTING("Dönüştürülüyor"),
    TAGGING("Etiketler yazılıyor"),
    SPONSORBLOCK("Sponsorlar kesiliyor"),
    SAVING("Kaydediliyor"),
    DONE("Tamamlandı"),
}

data class DownloadTask(
    val id: String,
    val spec: DownloadSpec,
    val state: DownloadState = DownloadState.QUEUED,
    val stage: DownloadStage = DownloadStage.WAITING,
    /** 1-based position inside a playlist, and its length; 0 for single videos. */
    val itemIndex: Int = 0,
    val itemCount: Int = 0,
    /** Overall 0f..1f across all parts, or null while indeterminate. */
    val progress: Float? = null,
    val etaSeconds: Long = -1,
    val speed: String? = null,
    val totalSize: String? = null,
    val error: String? = null,
    /** Tail of yt-dlp's output, shown only in the "Ayrıntılar" panel. */
    val log: List<String> = emptyList(),
    val savedNames: List<String> = emptyList(),
    val savedUris: List<String> = emptyList(),
    val savedMimes: List<String> = emptyList(),
    val savedPath: String? = null,
    /** Non-fatal problem worth telling the user about, e.g. subtitles that failed. */
    val notice: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class HistoryEntry(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val kind: MediaKind,
    val savedNames: List<String> = emptyList(),
    val savedUris: List<String> = emptyList(),
    val savedMimes: List<String> = emptyList(),
    val sizeBytes: Long = 0,
    val completedAt: Long = System.currentTimeMillis(),
    val spec: DownloadSpec? = null,
)
