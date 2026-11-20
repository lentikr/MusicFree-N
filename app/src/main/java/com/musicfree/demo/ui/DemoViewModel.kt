package com.musicfree.demo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicfree.demo.player.DemoAudioPlayer
import com.musicfree.demo.player.PlaybackState
import com.musicfree.demo.plugin.model.LyricResult
import com.musicfree.demo.plugin.model.MediaSourceResult
import com.musicfree.demo.plugin.model.MusicItem
import com.musicfree.demo.plugin.model.PluginMeta
import com.musicfree.demo.plugin.model.PluginSubscriptionEntry
import com.musicfree.demo.plugin.model.SandboxLogEntry
import com.musicfree.demo.plugin.model.TopListGroup
import com.musicfree.demo.plugin.runtime.MusicFreePluginRuntime
import com.musicfree.demo.plugin.runtime.OnlineImportOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DemoUiState(
    val plugins: List<PluginMeta> = emptyList(),
    val selectedPluginId: String? = null,
    val importUrl: String = "",
    val isImporting: Boolean = false,
    val pendingSubscription: List<PluginSubscriptionEntry> = emptyList(),
    val searchQuery: String = "周杰伦",
    val searchType: String = "music",
    val isSearching: Boolean = false,
    val searchResults: List<MusicItem> = emptyList(),
    val isEnd: Boolean = true,
    val verifyingItemId: String? = null,
    val lastMediaSource: Pair<MusicItem, MediaSourceResult>? = null,
    val lastLyric: Pair<MusicItem, LyricResult>? = null,
    val lastTopLists: List<TopListGroup>? = null,
    val statusBanner: String? = null,
    val errorMessage: String? = null
) {
    val selectedPlugin: PluginMeta?
        get() = plugins.firstOrNull { it.id == selectedPluginId } ?: plugins.firstOrNull()
}

class DemoViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = MusicFreePluginRuntime(application)
    private val audioPlayer = DemoAudioPlayer(application, runtime.httpBridge.okHttpClient)

    private val _uiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    val logs: StateFlow<List<SandboxLogEntry>> = runtime.logs
    val playbackState: StateFlow<PlaybackState> = audioPlayer.state

    init {
        viewModelScope.launch {
            val restored = runtime.restorePersistedPlugins()
            _uiState.update {
                it.copy(
                    plugins = restored,
                    selectedPluginId = restored.firstOrNull()?.id
                )
            }
        }
    }

    fun updateImportUrl(url: String) {
        _uiState.update { it.copy(importUrl = url, errorMessage = null) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchType(type: String) {
        _uiState.update { it.copy(searchType = type) }
    }

    fun selectPlugin(pluginId: String) {
        _uiState.update { state ->
            val plugin = state.plugins.firstOrNull { it.id == pluginId }
            state.copy(
                selectedPluginId = pluginId,
                searchType = plugin?.defaultSearchType ?: "music",
                searchResults = emptyList(),
                lastMediaSource = null,
                lastLyric = null,
                lastTopLists = null
            )
        }
    }

    fun importOnlineUrl() {
        val url = _uiState.value.importUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先粘贴 .js 插件链接或 plugins.json 订阅源链接") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null, statusBanner = null) }
            runCatching { runtime.importFromOnlineUrl(url) }
                .onSuccess { outcome ->
                    when (outcome) {
                        is OnlineImportOutcome.SinglePluginLoaded -> {
                            addOrReplacePlugin(outcome.plugin)
                            _uiState.update {
                                it.copy(
                                    isImporting = false,
                                    importUrl = "",
                                    statusBanner = "已导入插件：${outcome.plugin.platform} v${outcome.plugin.version}"
                                )
                            }
                        }
                        is OnlineImportOutcome.SubscriptionDetected -> {
                            _uiState.update {
                                it.copy(
                                    isImporting = false,
                                    pendingSubscription = outcome.entries,
                                    statusBanner = "检测到订阅清单，共 ${outcome.entries.size} 个插件，请选择导入"
                                )
                            }
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "在线导入失败：${err.message}"
                        )
                    }
                }
        }
    }

    fun importSubscriptionEntry(entry: PluginSubscriptionEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            runCatching { runtime.importFromOnlineUrl(entry.url) }
                .onSuccess { outcome ->
                    if (outcome is OnlineImportOutcome.SinglePluginLoaded) {
                        addOrReplacePlugin(outcome.plugin)
                        _uiState.update {
                            it.copy(
                                isImporting = false,
                                statusBanner = "已从订阅导入：${outcome.plugin.platform}"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isImporting = false) }
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "导入「${entry.name}」失败：${err.message}"
                        )
                    }
                }
        }
    }

    fun dismissSubscriptionDialog() {
        _uiState.update { it.copy(pendingSubscription = emptyList()) }
    }

    fun importBuiltInDemo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            runCatching { runtime.importBuiltInDemoPlugin() }
                .onSuccess { meta ->
                    addOrReplacePlugin(meta)
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            statusBanner = "已载入内置验证插件：${meta.platform}"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isImporting = false, errorMessage = "载入内置插件失败：${err.message}")
                    }
                }
        }
    }

    fun removePlugin(pluginId: String) {
        viewModelScope.launch {
            runtime.removePlugin(pluginId)
            _uiState.update { state ->
                val remaining = state.plugins.filterNot { it.id == pluginId }
                state.copy(
                    plugins = remaining,
                    selectedPluginId = if (state.selectedPluginId == pluginId) remaining.firstOrNull()?.id else state.selectedPluginId
                )
            }
        }
    }

    fun runSearch() {
        val plugin = _uiState.value.selectedPlugin ?: run {
            _uiState.update { it.copy(errorMessage = "请先在「插件」页导入至少一个插件") }
            return
        }
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSearching = true, errorMessage = null, lastTopLists = null)
            }
            runCatching { runtime.search(plugin, query, 1, _uiState.value.searchType) }
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = res.data,
                            isEnd = res.isEnd,
                            statusBanner = "search() 成功，返回 ${res.data.size} 条结果"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isSearching = false, errorMessage = "search() 调用失败：${err.message}")
                    }
                }
        }
    }

    fun verifyMediaSource(item: MusicItem) {
        val plugin = _uiState.value.selectedPlugin ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(verifyingItemId = item.id, errorMessage = null) }
            runCatching { runtime.getMediaSource(plugin, item, "standard") }
                .onSuccess { source ->
                    _uiState.update {
                        it.copy(
                            verifyingItemId = null,
                            lastMediaSource = item to source,
                            statusBanner = if (!source.url.isNullOrBlank()) {
                                "getMediaSource() 成功解析出音频流地址"
                            } else {
                                "getMediaSource() 返回空 URL（可能受限于该音源版权或需登录）"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(verifyingItemId = null, errorMessage = "getMediaSource() 失败：${err.message}")
                    }
                }
        }
    }

    fun verifyLyric(item: MusicItem) {
        val plugin = _uiState.value.selectedPlugin ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(verifyingItemId = item.id, errorMessage = null) }
            runCatching { runtime.getLyric(plugin, item) }
                .onSuccess { lyric ->
                    _uiState.update {
                        it.copy(
                            verifyingItemId = null,
                            lastLyric = item to lyric,
                            statusBanner = "getLyric() 调用成功"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(verifyingItemId = null, errorMessage = "getLyric() 失败：${err.message}")
                    }
                }
        }
    }

    fun verifyTopLists() {
        val plugin = _uiState.value.selectedPlugin ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            runCatching { runtime.getTopLists(plugin) }
                .onSuccess { lists ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            lastTopLists = lists,
                            statusBanner = "getTopLists() 返回 ${lists.size} 个榜单分组"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isSearching = false, errorMessage = "getTopLists() 失败：${err.message}")
                    }
                }
        }
    }

    fun playMediaSource(item: MusicItem, source: MediaSourceResult) {
        val url = source.url ?: return
        audioPlayer.play(item.title, url, source.headers, source.userAgent)
    }

    fun togglePlayPause() {
        audioPlayer.togglePlayPause()
    }

    fun clearLogs() = runtime.clearLogs()

    private fun addOrReplacePlugin(meta: PluginMeta) {
        _uiState.update { state ->
            val updated = state.plugins.filterNot { it.platform == meta.platform } + meta
            state.copy(
                plugins = updated,
                selectedPluginId = meta.id,
                searchType = meta.defaultSearchType
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
        runtime.close()
    }
}
