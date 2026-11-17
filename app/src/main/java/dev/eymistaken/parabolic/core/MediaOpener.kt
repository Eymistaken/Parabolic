package dev.eymistaken.parabolic.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hands a finished download to whatever player the user has set as default. */
object MediaOpener {

    sealed interface Result {
        data object Opened : Result
        data object Missing : Result
        data object NoPlayer : Result
    }

    fun open(context: Context, uriString: String?, mime: String?): Result {
        val uri = uriString?.let(Uri::parse) ?: return Result.Missing
        val exists = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (!exists) return Result.Missing

        val type = mime ?: context.contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.Opened
        } catch (notFound: ActivityNotFoundException) {
            Result.NoPlayer
        }
    }
}
