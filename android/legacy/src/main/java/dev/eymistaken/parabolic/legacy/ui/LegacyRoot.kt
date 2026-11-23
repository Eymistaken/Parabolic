package dev.eymistaken.parabolic.legacy.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import dev.eymistaken.parabolic.core.DownloadRepository
import dev.eymistaken.parabolic.core.DownloadSpec
import dev.eymistaken.parabolic.core.MediaOpener
import dev.eymistaken.parabolic.core.Prefs

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Downloads("İndirmeler", Icons.Outlined.Download, Icons.Filled.Download),
    History("Geçmiş", Icons.Outlined.History, Icons.Filled.History),
    Settings("Ayarlar", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun LegacyRoot(
    prefs: Prefs,
    repo: DownloadRepository,
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Downloads) }
    // Non-null while the "new download" screen is open; holds the link it starts with.
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    val tasks by repo.tasks.collectAsState()
    val history by repo.history.collectAsState()
    val message by repo.toast.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            repo.consumeMessage()
        }
    }
    LaunchedEffect(sharedUrl) {
        sharedUrl?.let {
            tab = Tab.Downloads
            adding = it
            onSharedUrlConsumed()
        }
    }

    fun open(uri: String?, mime: String?) {
        when (MediaOpener.open(context, uri, mime)) {
            MediaOpener.Result.Opened -> Unit
            MediaOpener.Result.Missing -> repo.showMessage("Dosya bulunamadı; silinmiş ya da taşınmış olabilir")
            MediaOpener.Result.NoPlayer -> repo.showMessage("Bu dosyayı açabilecek bir uygulama yok")
        }
    }

    fun enqueue(spec: DownloadSpec) {
        repo.enqueue(spec)
        repo.showMessage("İndirme sıraya alındı")
    }

    AnimatedContent(
        targetState = adding,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it != null },
        label = "screen",
    ) { initialUrl ->
        if (initialUrl != null) {
            BackHandler { adding = null }
            // A link shared while the form is already open starts a fresh form.
            key(initialUrl) {
                AddDownloadScreen(
                    initialUrl = initialUrl,
                    prefs = prefs,
                    onClose = { adding = null },
                    onEnqueue = { spec ->
                        enqueue(spec)
                        tab = Tab.Downloads
                        adding = null
                    },
                )
            }
            return@AnimatedContent
        }

        if (tab != Tab.Downloads) BackHandler { tab = Tab.Downloads }

        val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()
        val downloadsList = rememberLazyListState()
        val fabExpanded by remember { derivedStateOf { downloadsList.firstVisibleItemIndex == 0 } }
        var menuOpen by remember { mutableStateOf(false) }
        var confirmClearHistory by remember { mutableStateOf(false) }

        Scaffold(
            modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text(tab.label) },
                    scrollBehavior = topBarScroll,
                    actions = {
                        when (tab) {
                            Tab.Downloads -> {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Diğer seçenekler")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Panodaki bağlantıyı ekle") },
                                        leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            adding = readClipboardUrl(context).orEmpty()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Bitenleri listeden kaldır") },
                                        leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                                        enabled = tasks.any { it.state.isTerminal },
                                        onClick = {
                                            menuOpen = false
                                            repo.clearFinished()
                                        },
                                    )
                                }
                            }
                            Tab.History -> if (history.isNotEmpty()) {
                                IconButton(onClick = { confirmClearHistory = true }) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Geçmişi temizle")
                                }
                            }
                            Tab.Settings -> Unit
                        }
                    },
                )
            },
            bottomBar = {
                val active = tasks.count { it.state.isActive }
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (item == Tab.Downloads && active > 0) Badge { Text(active.toString()) }
                                    },
                                ) {
                                    Icon(if (tab == item) item.selectedIcon else item.icon, contentDescription = null)
                                }
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == Tab.Downloads) {
                    ExtendedFloatingActionButton(
                        text = { Text("Yeni indirme") },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { adding = "" },
                        expanded = fabExpanded,
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box {
                when (tab) {
                    Tab.Downloads -> DownloadsTab(
                        tasks = tasks,
                        listState = downloadsList,
                        contentPadding = padding,
                        onCancel = repo::cancel,
                        onRetry = repo::retry,
                        onDismiss = repo::dismiss,
                        onOpen = { open(it.savedUris.firstOrNull(), it.savedMimes.firstOrNull()) },
                        onAdd = { adding = "" },
                        onPaste = { adding = readClipboardUrl(context).orEmpty() },
                    )

                    Tab.History -> HistoryTab(
                        entries = history,
                        contentPadding = padding,
                        onOpen = { open(it.savedUris.firstOrNull(), it.savedMimes.firstOrNull()) },
                        onRedownload = { entry ->
                            repo.enqueue(entry.spec ?: prefs.specDefaults(entry.url, entry.title, entry.thumbnail))
                            repo.showMessage("Yeniden sıraya alındı")
                            tab = Tab.Downloads
                        },
                        onDelete = repo::deleteHistory,
                    )

                    Tab.Settings -> SettingsTab(
                        prefs = prefs,
                        contentPadding = padding,
                        onMessage = repo::showMessage,
                    )
                }
            }
        }

        if (confirmClearHistory) {
            AlertDialog(
                onDismissRequest = { confirmClearHistory = false },
                title = { Text("Geçmiş temizlensin mi?") },
                text = { Text("Liste boşalır. İndirilen dosyalar telefonda kalır.") },
                confirmButton = {
                    TextButton(onClick = {
                        repo.clearHistory()
                        confirmClearHistory = false
                    }) { Text("Temizle") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearHistory = false }) { Text("Vazgeç") }
                },
            )
        }
    }
}
