package dev.eymistaken.parabolic.legacy.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.eymistaken.parabolic.core.MediaStoreSaver

/** Small colored heading above a group of settings, as in the system Settings app. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
fun SwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** A row that shows the current value and opens a picker when tapped. */
@Composable
fun ValueItem(
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing,
    )
}

/** The classic single-choice preference dialog: a radio list that closes on tap. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(option)
                                    onDismiss()
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}

// ---------------------------------------------------------------------------
// Folders
// ---------------------------------------------------------------------------

/**
 * Opens the system folder picker and keeps a persistent read/write grant on the
 * chosen folder, so downloads can be written there later without a storage permission.
 */
@Composable
fun rememberFolderPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            latest(uri.toString())
        }
    }
    return { runCatching { launcher.launch(null) } }
}

fun folderLabel(value: String?, defaultLabel: String): String =
    value?.takeIf { it.isNotBlank() }?.let { MediaStoreSaver.describeTree(Uri.parse(it)) } ?: defaultLabel

@Composable
fun FolderItem(
    title: String,
    value: String?,
    defaultLabel: String,
    onPick: () -> Unit,
    onReset: (() -> Unit)?,
) {
    val custom = !value.isNullOrBlank()
    ListItem(
        modifier = Modifier.clickable(onClick = onPick),
        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(folderLabel(value, defaultLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (custom && onReset != null) {
            {
                IconButton(onClick = onReset) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Varsayılana dön")
                }
            }
        } else {
            null
        },
    )
}

// ---------------------------------------------------------------------------
// Subtitle languages
// ---------------------------------------------------------------------------

data class SubtitleLanguage(val code: String, val name: String, val native: String)

/** Ordered so the likely picks for a Turkish user come first. */
val SubtitleLanguages = listOf(
    SubtitleLanguage("tr", "Türkçe", "Türkçe"),
    SubtitleLanguage("en", "İngilizce", "English"),
    SubtitleLanguage("de", "Almanca", "Deutsch"),
    SubtitleLanguage("fr", "Fransızca", "Français"),
    SubtitleLanguage("es", "İspanyolca", "Español"),
    SubtitleLanguage("ar", "Arapça", "العربية"),
    SubtitleLanguage("ru", "Rusça", "Русский"),
    SubtitleLanguage("ja", "Japonca", "日本語"),
    SubtitleLanguage("ko", "Korece", "한국어"),
    SubtitleLanguage("zh-Hans", "Çince (Basit)", "简体中文"),
    SubtitleLanguage("zh-Hant", "Çince (Geleneksel)", "繁體中文"),
    SubtitleLanguage("it", "İtalyanca", "Italiano"),
    SubtitleLanguage("pt", "Portekizce", "Português"),
    SubtitleLanguage("pt-BR", "Portekizce (Brezilya)", "Português (Brasil)"),
    SubtitleLanguage("nl", "Felemenkçe", "Nederlands"),
    SubtitleLanguage("pl", "Lehçe", "Polski"),
    SubtitleLanguage("uk", "Ukraynaca", "Українська"),
    SubtitleLanguage("az", "Azerbaycanca", "Azərbaycanca"),
    SubtitleLanguage("ku", "Kürtçe", "Kurdî"),
    SubtitleLanguage("fa", "Farsça", "فارسی"),
    SubtitleLanguage("el", "Yunanca", "Ελληνικά"),
    SubtitleLanguage("bg", "Bulgarca", "Български"),
    SubtitleLanguage("ro", "Rumence", "Română"),
    SubtitleLanguage("hu", "Macarca", "Magyar"),
    SubtitleLanguage("cs", "Çekçe", "Čeština"),
    SubtitleLanguage("sv", "İsveççe", "Svenska"),
    SubtitleLanguage("no", "Norveççe", "Norsk"),
    SubtitleLanguage("da", "Danca", "Dansk"),
    SubtitleLanguage("fi", "Fince", "Suomi"),
    SubtitleLanguage("he", "İbranice", "עברית"),
    SubtitleLanguage("hi", "Hintçe", "हिन्दी"),
    SubtitleLanguage("id", "Endonezce", "Bahasa Indonesia"),
    SubtitleLanguage("ms", "Malayca", "Bahasa Melayu"),
    SubtitleLanguage("th", "Tayca", "ไทย"),
    SubtitleLanguage("vi", "Vietnamca", "Tiếng Việt"),
)

fun parseLanguageCodes(raw: String): List<String> =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

fun describeLanguages(raw: String): String {
    val codes = parseLanguageCodes(raw)
    if (codes.isEmpty()) return "Dil seçilmedi"
    return codes.joinToString(", ") { code -> SubtitleLanguages.firstOrNull { it.code == code }?.name ?: code }
}

/**
 * Checkbox list with a search field. Languages are tried in the order they were
 * ticked, which the summary line under the title spells out.
 */
@Composable
fun LanguageDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val chosen = remember { mutableStateListOf<String>().apply { addAll(parseLanguageCodes(initial)) } }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) SubtitleLanguages else SubtitleLanguages.filter {
            it.name.lowercase().contains(q) || it.native.lowercase().contains(q) || it.code.lowercase().startsWith(q)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Altyazı dilleri") },
        text = {
            Column {
                Text(
                    text = if (chosen.isEmpty()) "Hiçbiri seçili değil" else "Öncelik: " + describeLanguages(chosen.joinToString(",")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Dil ara") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.code }) { language ->
                        val checked = language.code in chosen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(value = checked, role = Role.Checkbox) {
                                    if (it) chosen += language.code else chosen -= language.code
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(language.name, style = MaterialTheme.typography.bodyLarge)
                                if (language.native != language.name) {
                                    Text(
                                        language.native,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen.joinToString(",")) }) { Text("Tamam") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}
