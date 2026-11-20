package com.musicfree.demo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicfree.demo.player.PlaybackState
import com.musicfree.demo.plugin.model.LogLevel
import com.musicfree.demo.plugin.model.MusicItem
import com.musicfree.demo.plugin.model.PluginMeta
import com.musicfree.demo.plugin.model.SandboxLogEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicFreeDemoApp(viewModel: DemoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MusicFree Native",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "QuickJS 沙箱 · M3 Expressive Demo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(visible = playback.currentTitle != null) {
                    MiniPlayerBar(
                        playback = playback,
                        onToggle = viewModel::togglePlayPause
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Rounded.Extension, contentDescription = "插件") },
                        label = { Text("插件 (${uiState.plugins.size})") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = "接口验证") },
                        label = { Text("接口联调") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Rounded.Terminal, contentDescription = "沙箱日志") },
                        label = { Text("日志 (${logs.size})") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            uiState.errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            uiState.statusBanner?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            when (currentTab) {
                0 -> PluginsScreen(
                    uiState = uiState,
                    onUrlChange = viewModel::updateImportUrl,
                    onImportOnline = viewModel::importOnlineUrl,
                    onImportBuiltIn = viewModel::importBuiltInDemo,
                    onSelectAndVerify = { plugin ->
                        viewModel.selectPlugin(plugin.id)
                        currentTab = 1
                    },
                    onRemove = viewModel::removePlugin
                )
                1 -> VerifierScreen(
                    uiState = uiState,
                    onSelectPlugin = viewModel::selectPlugin,
                    onQueryChange = viewModel::updateSearchQuery,
                    onTypeChange = viewModel::updateSearchType,
                    onSearch = viewModel::runSearch,
                    onVerifyTopLists = viewModel::verifyTopLists,
                    onVerifyMediaSource = viewModel::verifyMediaSource,
                    onVerifyLyric = viewModel::verifyLyric,
                    onPlay = viewModel::playMediaSource
                )
                2 -> ConsoleScreen(
                    logs = logs,
                    onClear = viewModel::clearLogs
                )
            }
        }
    }

    if (uiState.pendingSubscription.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSubscriptionDialog,
            title = { Text("订阅清单中的插件 (${uiState.pendingSubscription.size})") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.pendingSubscription) { entry ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name.ifBlank { "未命名插件" },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "v${entry.version ?: "0.0.0"} · ${entry.url}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { viewModel.importSubscriptionEntry(entry) },
                                    enabled = !uiState.isImporting
                                ) {
                                    Text("导入")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSubscriptionDialog) {
                    Text("关闭")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PluginsScreen(
    uiState: DemoUiState,
    onUrlChange: (String) -> Unit,
    onImportOnline: () -> Unit,
    onImportBuiltIn: () -> Unit,
    onSelectAndVerify: (PluginMeta) -> Unit,
    onRemove: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "在线导入 MusicFree 插件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "支持单个 .js 插件地址或标准 plugins.json 订阅源地址。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.importUrl,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("插件 URL (.js 或 plugins.json)") },
                        placeholder = { Text("https://.../plugins.json 或 index.js") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onImportOnline,
                            enabled = !uiState.isImporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isImporting) {
                                LoadingIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("在线导入")
                        }
                        FilledTonalButton(
                            onClick = onImportBuiltIn,
                            enabled = !uiState.isImporting
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("载入内置验证源")
                        }
                    }
                }
            }
        }

        if (uiState.plugins.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "尚未导入插件。点击「载入内置验证源」即可零配置体验完整链路。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.plugins, key = { it.id }) { plugin ->
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.platform,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "v${plugin.version}" +
                                        if (plugin.author.isNotBlank()) " · ${plugin.author}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRemove(plugin.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "移除插件")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            plugin.supportedMethods.forEach { method ->
                                AssistChip(
                                    onClick = { onSelectAndVerify(plugin) },
                                    label = { Text(method, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            FilledTonalButton(onClick = { onSelectAndVerify(plugin) }) {
                                Text("去验证接口")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VerifierScreen(
    uiState: DemoUiState,
    onSelectPlugin: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVerifyTopLists: () -> Unit,
    onVerifyMediaSource: (MusicItem) -> Unit,
    onVerifyLyric: (MusicItem) -> Unit,
    onPlay: (MusicItem, com.musicfree.demo.plugin.model.MediaSourceResult) -> Unit
) {
    val activePlugin = uiState.selectedPlugin

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.plugins.isEmpty()) {
            item {
                Text(
                    text = "请先在「插件」页导入插件后再进行接口验证。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@LazyColumn
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.plugins.forEach { plugin ->
                    FilterChip(
                        selected = plugin.id == activePlugin?.id,
                        onClick = { onSelectPlugin(plugin.id) },
                        label = { Text(plugin.platform) }
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("search() 关键词") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (activePlugin?.supportedSearchType ?: listOf("music")).forEach { type ->
                            FilterChip(
                                selected = uiState.searchType == type,
                                onClick = { onTypeChange(type) },
                                label = { Text(type) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onSearch,
                            enabled = !uiState.isSearching,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isSearching) {
                                LoadingIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("调用 search()")
                        }

                        if (activePlugin?.supportedMethods?.contains("getTopLists") == true) {
                            OutlinedButton(
                                onClick = onVerifyTopLists,
                                enabled = !uiState.isSearching
                            ) {
                                Text("测 getTopLists()")
                            }
                        }
                    }
                }
            }
        }

        uiState.lastMediaSource?.let { (item, source) ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "getMediaSource() 验证结果 · ${item.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "URL: ${source.url ?: "(空)"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (source.headers.isNotEmpty()) {
                            Text(
                                text = "Headers: ${source.headers}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (!source.url.isNullOrBlank()) {
                            Button(onClick = { onPlay(item, source) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("用 Media3 试听播放")
                            }
                        }
                    }
                }
            }
        }

        uiState.lastLyric?.let { (item, lyric) ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "getLyric() 验证结果 · ${item.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = lyric.rawLrc?.take(400) ?: "(无歌词内容)",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        uiState.lastTopLists?.let { groups ->
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "getTopLists() 返回 ${groups.size} 个分组",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        groups.take(5).forEach { g ->
                            Text(
                                text = "• ${g.title} (${g.data.size} 项)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        items(uiState.searchResults, key = { "${it.platform}_${it.id}" }) { item ->
            ElevatedCard(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(item.artist, item.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { onVerifyMediaSource(item) },
                            enabled = uiState.verifyingItemId == null
                        ) {
                            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("测 getMediaSource")
                        }
                        if (activePlugin?.supportedMethods?.contains("getLyric") == true) {
                            OutlinedButton(
                                onClick = { onVerifyLyric(item) },
                                enabled = uiState.verifyingItemId == null
                            ) {
                                Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("测 getLyric")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleScreen(
    logs: List<SandboxLogEntry>,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QuickJS & OkHttp 实时日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onClear) { Text("清空") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(logs) { log ->
                val color = when (log.level) {
                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                    LogLevel.HTTP -> MaterialTheme.colorScheme.primary
                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                }
                Text(
                    text = "[${log.timestamp}][${log.tag}] ${log.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    playback: PlaybackState,
    onToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playback.currentTitle ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playback.isPlaying) "Media3 正在播放" else "已暂停",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            FilledTonalIconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "播放/暂停"
                )
            }
        }
    }
}
