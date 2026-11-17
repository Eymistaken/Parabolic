package dev.eymistaken.parabolic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    return codes.joinToString(", ") { code ->
        SubtitleLanguages.firstOrNull { it.code == code }?.name ?: code
    }
}

/** Row that summarizes the chosen languages and opens [LanguagePickerSheet]. */
@Composable
fun LanguageSummaryRow(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialShapes.Cookie6Sided.toShapeSafe())
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Subtitles,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Altyazı dilleri", style = MaterialTheme.typography.bodyLarge)
            Text(
                describeLanguages(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("Değiştir", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The subtitle language drawer: search, pick several, reorder by tapping
 * (the order is the priority yt-dlp tries them in).
 */
@Composable
fun LanguagePickerSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val chosen = remember { mutableStateListOf<String>().apply { addAll(parseLanguageCodes(initial)) } }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) SubtitleLanguages else SubtitleLanguages.filter {
            it.name.lowercase().contains(q) || it.native.lowercase().contains(q) || it.code.lowercase().startsWith(q)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Altyazı dilleri", style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                "Seçme sırası öncelik sırasıdır; ilk bulunan dil indirilir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chosen.isEmpty()) {
                    Text(
                        "Henüz dil seçilmedi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                chosen.forEachIndexed { index, code ->
                    val language = SubtitleLanguages.firstOrNull { it.code == code }
                    InputChip(
                        selected = true,
                        onClick = { chosen.remove(code) },
                        label = { Text("${index + 1}. ${language?.name ?: code}") },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = "Kaldır", modifier = Modifier.size(InputChipDefaults.IconSize))
                        },
                        shape = CircleShape,
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Dil ara") },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.code }) { language ->
                    val selected = language.code in chosen
                    LanguageRow(
                        language = language,
                        selected = selected,
                        order = chosen.indexOf(language.code).takeIf { it >= 0 }?.plus(1),
                        onToggle = {
                            if (selected) chosen.remove(language.code) else chosen.add(language.code)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            Button(
                onClick = { onConfirm(chosen.joinToString(",")) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Tamam", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun LanguageRow(
    language: SubtitleLanguage,
    selected: Boolean,
    order: Int?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "langContainer",
    )
    val badgeScale by animateFloatAsState(
        if (selected) 1f else 0.85f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "badge",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(badgeScale)
                .clip(if (selected) MaterialShapes.Cookie9Sided.toShapeSafe() else CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = order?.toString() ?: language.code.take(2).uppercase(),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(language.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${language.native} · ${language.code}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Seçili", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
