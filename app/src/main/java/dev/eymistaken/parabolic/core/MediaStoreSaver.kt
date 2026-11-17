package dev.eymistaken.parabolic.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.io.File

/**
 * Moves a finished file out of the app's private directory into the shared
 * media collections, so it shows up in the gallery / music player with no
 * storage permission on API 29+.
 */
object MediaStoreSaver {

    private const val FOLDER = "Parabolic"

    data class Saved(val displayName: String, val uri: Uri?, val path: String?, val size: Long, val mime: String)

    /**
     * @param subfolder optional folder under "Parabolic", e.g. a playlist's name,
     * so a 50-track album lands in its own folder instead of a flat pile.
     */
    fun save(context: Context, file: File, kind: MediaKind, subfolder: String? = null): Saved {
        val mime = mimeOf(file.extension)
        val size = file.length()
        val folder = subfolder
            ?.split('/')
            ?.map { it.replace(Regex("""[\\:*?"<>|]"""), "_").trim().trim('.') }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("/")
            ?.takeIf { it.isNotEmpty() }
            ?.let { "$FOLDER/$it" }
            ?: FOLDER
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, file, mime, kind, size, folder)
        } else {
            saveLegacy(context, file, mime, kind, size, folder)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        file: File,
        mime: String,
        kind: MediaKind,
        size: Long,
        folder: String,
    ): Saved {
        val isVideo = mime.startsWith("video/")
        val isAudio = mime.startsWith("audio/")
        val collection = when {
            isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relative = when {
            isVideo -> "${Environment.DIRECTORY_MOVIES}/$folder"
            isAudio -> "${Environment.DIRECTORY_MUSIC}/$folder"
            else -> "${Environment.DIRECTORY_DOWNLOADS}/$folder"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: return saveLegacy(context, file, mime, kind, size, folder)

        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
        } ?: run {
            resolver.delete(uri, null, null)
            return saveLegacy(context, file, mime, kind, size, folder)
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        file.delete()
        return Saved(file.name, uri, "$relative/${file.name}", size, mime)
    }

    private fun saveLegacy(
        context: Context,
        file: File,
        mime: String,
        kind: MediaKind,
        size: Long,
        folder: String,
    ): Saved {
        val base = when {
            mime.startsWith("video/") -> Environment.DIRECTORY_MOVIES
            mime.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(base), folder)
        dir.mkdirs()
        var target = File(dir, file.name)
        var counter = 1
        while (target.exists()) {
            target = File(dir, "${file.nameWithoutExtension} ($counter).${file.extension}")
            counter++
        }
        val ok = runCatching {
            file.inputStream().use { input ->
                target.outputStream().use { out -> input.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
            }
            true
        }.getOrDefault(false)

        if (!ok) {
            // Last resort: leave it in app-specific external storage so it is not lost.
            val fallbackDir = File(context.getExternalFilesDir(null), folder).apply { mkdirs() }
            val fallback = File(fallbackDir, file.name)
            file.copyTo(fallback, overwrite = true)
            file.delete()
            return Saved(fallback.name, providerUri(context, fallback), fallback.absolutePath, size, mime)
        }

        file.delete()
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        }
        return Saved(target.name, providerUri(context, target), target.absolutePath, size, mime)
    }

    private fun providerUri(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull()

    /**
     * Saves into a folder the user picked with the system folder picker. The
     * app holds a persisted grant on that tree, so no storage permission is
     * involved; files under shared storage are indexed by the media scanner.
     */
    fun saveToTree(context: Context, file: File, treeUri: Uri, subfolder: String? = null): Saved {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Klasör açılamadı")
        if (!root.exists() || !root.canWrite()) throw IOException("Klasöre yazılamıyor")
        var dir = root
        subfolder?.split('/')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
            val existing = dir.findFile(name)
            dir = existing?.takeIf { it.isDirectory }
                ?: dir.createDirectory(name)
                ?: throw IOException("Alt klasör oluşturulamadı")
        }
        // octet-stream keeps the provider from appending an extension of its own
        // (it would guess .ogg for .opus, for instance); the scanner uses the name.
        val target = dir.createFile("application/octet-stream", file.name)
            ?: throw IOException("Dosya oluşturulamadı")
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
        } ?: run {
            target.delete()
            throw IOException("Dosyaya yazılamadı")
        }
        val size = file.length()
        file.delete()
        val shownFolder = listOfNotNull(describeTree(treeUri), subfolder).joinToString("/")
        return Saved(target.name ?: file.name, target.uri, "$shownFolder/${target.name ?: file.name}", size, mimeOf(file.extension))
    }

    /** "primary:Movies/YouTube" -> "Movies/YouTube"; SD cards get a prefix. */
    fun describeTree(treeUri: Uri): String = runCatching {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        when {
            volume == "primary" -> path.ifEmpty { "Dahili depolama" }
            path.isEmpty() -> "SD kart"
            else -> "SD kart/$path"
        }
    }.getOrDefault(treeUri.toString())

    fun mimeOf(extension: String): String = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts" -> "video/mp2t"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/opus"
        "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/x-wav"
        "aac" -> "audio/aac"
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
