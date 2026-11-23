package dev.eymistaken.parabolic.legacy.ui

import android.content.ClipboardManager
import android.content.Context
import java.util.Locale
import kotlin.math.abs

fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "--:--"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatEta(seconds: Long): String? {
    if (seconds <= 0) return null
    return "${formatDuration(seconds.toInt())} kaldı"
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${value.toInt()} ${units[index]}" else String.format(Locale.US, "%.1f %s", value, units[index])
}

fun formatRelativeTime(timestamp: Long): String {
    val minutes = abs(System.currentTimeMillis() - timestamp) / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "şimdi"
        minutes < 60 -> "$minutes dk önce"
        hours < 24 -> "$hours sa önce"
        days < 30 -> "$days gün önce"
        else -> "${days / 30} ay önce"
    }
}

private val URL_REGEX = Regex("""https?://\S+""")

/** Pulls the first URL out of shared text, which often carries a caption too. */
fun extractUrl(text: String?): String? =
    text?.let { URL_REGEX.find(it)?.value?.trimEnd('.', ',', ')', ']') }

fun readClipboardUrl(context: Context): String? {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
    val text = manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    return extractUrl(text) ?: text?.trim()?.takeIf { it.startsWith("http") }
}
