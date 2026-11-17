package dev.eymistaken.parabolic.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.eymistaken.parabolic.core.DownloadRepository
import dev.eymistaken.parabolic.core.DownloadSpec
import dev.eymistaken.parabolic.core.DownloadState
import dev.eymistaken.parabolic.core.MediaOpener
import dev.eymistaken.parabolic.core.Prefs
import dev.eymistaken.parabolic.ui.components.ExpressiveNavigationRail
import dev.eymistaken.parabolic.ui.components.MagneticNavBar
import dev.eymistaken.parabolic.ui.components.NavDestination
import dev.eymistaken.parabolic.ui.screens.DownloadsScreen
import dev.eymistaken.parabolic.ui.screens.HistoryScreen
import dev.eymistaken.parabolic.ui.screens.SettingsScreen
import dev.eymistaken.parabolic.ui.theme.FlexWide

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Downloads("İndirmeler", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    History("Geçmiş", Icons.Outlined.History, Icons.Filled.History),
    Settings("Ayarlar", Icons.Outlined.Settings, Icons.Filled.Settings),
}

private val NavDestinations = Tab.entries.map { NavDestination(it.label, it.icon, it.selectedIcon) }

/** Window width buckets, following the Material window size classes. */
private enum class WidthClass { Compact, Medium, Expanded }

@Composable
fun ParabolicRoot(
    prefs: Prefs,
    repo: DownloadRepository,
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Downloads) }
    var sheetUrl by remember { mutableStateOf<String?>(null) }
    val panelState = remember { AddDownloadState("", prefs) }
    val tasks by repo.tasks.collectAsState()
    val history by repo.history.collectAsState()
    val message by repo.toast.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            repo.consumeMessage()
        }
    }

    fun handleOpen(result: MediaOpener.Result) {
        when (result) {
            MediaOpener.Result.Opened -> Unit
            MediaOpener.Result.Missing -> repo.showMessage("Dosya bulunamadı; silinmiş ya da taşınmış olabilir")
            MediaOpener.Result.NoPlayer -> repo.showMessage("Bu dosyayı açabilecek bir uygulama yok")
        }
    }

    fun enqueue(spec: DownloadSpec) {
        repo.enqueue(spec)
        repo.showMessage("İndirme sıraya alındı")
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val widthClass = when {
            maxWidth >= 840.dp -> WidthClass.Expanded
            maxWidth >= 600.dp -> WidthClass.Medium
            else -> WidthClass.Compact
        }
        val usesPanel = widthClass == WidthClass.Expanded

        fun startAdd(url: String) {
            if (usesPanel) {
                tab = Tab.Downloads
                panelState.url = url
                if (url.isNotBlank()) panelState.startProbe(context, scope)
            } else {
                sheetUrl = url
            }
        }

        LaunchedEffect(sharedUrl) {
            sharedUrl?.let {
                tab = Tab.Downloads
                startAdd(it)
                onSharedUrlConsumed()
            }
        }

        val activeCount = tasks.count { it.state.isActive }
        val subtitle: String? = when (tab) {
            Tab.Downloads -> when {
                activeCount > 0 -> "$activeCount indirme sürüyor"
                tasks.isNotEmpty() -> "${tasks.count { it.state == DownloadState.DONE }} tamamlandı"
                else -> null
            }
            Tab.History -> "${history.size} kayıt"
            Tab.Settings -> "Parabolic"
        }

        val content: @Composable (PaddingValues) -> Unit = { padding ->
            val enterFx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val enterMove = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
            val exitFx = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(enterFx) + slideInVertically(enterMove) { it / 14 }) togetherWith fadeOut(exitFx)
                },
                label = "tab",
            ) { current ->
                when (current) {
                    Tab.Downloads -> {
                        val list: @Composable (Modifier) -> Unit = { mod ->
                            DownloadsScreen(
                                tasks = tasks,
                                contentPadding = padding,
                                onCancel = repo::cancel,
                                onRetry = repo::retry,
                                onDismiss = repo::dismiss,
                                onOpen = { task ->
                                    handleOpen(MediaOpener.open(context, task.savedUris.firstOrNull(), task.savedMimes.firstOrNull()))
                                },
                                onClearFinished = repo::clearFinished,
                                onAdd = { startAdd("") },
                                onPaste = { startAdd(readClipboard(context).orEmpty()) },
                                modifier = mod,
                            )
                        }
                        if (usesPanel) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                list(Modifier.weight(1.1f))
                                AddDownloadPanel(
                                    state = panelState,
                                    onEnqueue = { spec ->
                                        enqueue(spec)
                                        panelState.reset()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(
                                            top = padding.calculateTopPadding() + 8.dp,
                                            end = 16.dp,
                                            bottom = 16.dp,
                                        ),
                                )
                            }
                        } else {
                            list(Modifier)
                        }
                    }

                    Tab.History -> HistoryScreen(
                        entries = history,
                        contentPadding = padding,
                        onOpen = { entry ->
                            handleOpen(MediaOpener.open(context, entry.savedUris.firstOrNull(), entry.savedMimes.firstOrNull()))
                        },
                        onRedownload = { entry ->
                            repo.enqueue(entry.spec ?: prefs.specDefaults(entry.url, entry.title, entry.thumbnail))
                            repo.showMessage("Yeniden sıraya alındı")
                            tab = Tab.Downloads
                        },
                        onDelete = repo::deleteHistory,
                        onClearAll = repo::clearHistory,
                    )

                    Tab.Settings -> SettingsScreen(
                        prefs = prefs,
                        contentPadding = padding,
                        onMessage = repo::showMessage,
                    )
                }
            }
        }

        if (widthClass == WidthClass.Compact) {
            CompactLayout(
                tab = tab,
                subtitle = subtitle,
                onTab = { tab = it },
                onAdd = { startAdd("") },
                snackbar = snackbar,
                content = content,
            )
        } else {
            RailLayout(
                tab = tab,
                subtitle = subtitle,
                expandedByDefault = widthClass == WidthClass.Expanded,
                onTab = { tab = it },
                onAdd = { startAdd("") },
                snackbar = snackbar,
                content = content,
            )
        }
    }

    sheetUrl?.let { initial ->
        AddDownloadSheet(
            initialUrl = initial,
            prefs = prefs,
            onDismiss = { sheetUrl = null },
            onEnqueue = { spec ->
                enqueue(spec)
                sheetUrl = null
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Phones: big flexible header + floating toolbar with the FAB beside it
// ---------------------------------------------------------------------------

@Composable
private fun CompactLayout(
    tab: Tab,
    subtitle: String?,
    onTab: (Tab) -> Unit,
    onAdd: () -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    val topScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom,
    )
    Scaffold(
        modifier = Modifier
            .nestedScroll(topScroll.nestedScrollConnection)
            .nestedScroll(toolbarScroll),
        topBar = { ExpressiveTopBar(tab, subtitle, topScroll) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp),
            )

            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, contentDescription = "Bağlantı ekle")
                    }
                },
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                scrollBehavior = toolbarScroll,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                MagneticNavBar(
                    destinations = NavDestinations,
                    selectedIndex = tab.ordinal,
                    onSelect = { onTab(Tab.entries[it]) },
                )
            }
        }
    }
}

@Composable
private fun ExpressiveTopBar(
    tab: Tab,
    subtitle: String?,
    scroll: TopAppBarScrollBehavior,
    scrolledColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    LargeFlexibleTopAppBar(
        title = {
            val enterFx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val enterMove = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
            val exitFx = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = tab.label,
                transitionSpec = {
                    (fadeIn(enterFx) + slideInVertically(enterMove) { it / 2 }) togetherWith fadeOut(exitFx)
                },
                label = "title",
            ) { title ->
                Text(title, fontFamily = FlexWide, fontWeight = FontWeight.ExtraBold)
            }
        },
        subtitle = subtitle?.let { text ->
            {
                AnimatedContent(targetState = text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "subtitle") {
                    Text(it)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = scrolledColor,
        ),
        scrollBehavior = scroll,
    )
}

// ---------------------------------------------------------------------------
// Tablets: navigation rail (wide when there is room) + adaptive content
// ---------------------------------------------------------------------------

@Composable
private fun RailLayout(
    tab: Tab,
    subtitle: String?,
    expandedByDefault: Boolean,
    onTab: (Tab) -> Unit,
    onAdd: () -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit,
) {
    // Wide windows open with the rail expanded, medium ones collapsed; the menu
    // button lets the user flip it either way until the window class changes.
    var railExpanded by rememberSaveable(expandedByDefault) { mutableStateOf(expandedByDefault) }
    val topScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ExpressiveNavigationRail(
            destinations = NavDestinations,
            selectedIndex = tab.ordinal,
            onSelect = { onTab(Tab.entries[it]) },
            expanded = railExpanded,
            onExpandedChange = { railExpanded = it },
            actionLabel = "Bağlantı ekle",
            actionIcon = Icons.Filled.Add,
            onAction = onAdd,
        )

        // Content sits on its own rounded pane, lifted off the rail's surface.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
                .padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                .clip(ContentPaneShape)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(topScroll.nestedScrollConnection),
                topBar = {
                    ExpressiveTopBar(
                        tab = tab,
                        subtitle = subtitle,
                        scroll = topScroll,
                        scrolledColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                },
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = MaterialTheme.colorScheme.surface,
            ) { padding ->
                content(PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()))
            }
        }
    }
}

private val ContentPaneShape = RoundedCornerShape(28.dp)

private fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
    val text = manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    return extractUrl(text)
}
