package dev.eymistaken.parabolic.core

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/** History lives in one small JSON file; a database would be overkill here. */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<HistoryEntry>>(file.readText())
        }.onFailure { Log.w("HistoryStore", "history unreadable, starting over", it) }
            .getOrDefault(emptyList())
    }

    fun save(entries: List<HistoryEntry>) {
        runCatching {
            file.writeText(json.encodeToString(entries.take(500)))
        }.onFailure { Log.w("HistoryStore", "could not persist history", it) }
    }
}
