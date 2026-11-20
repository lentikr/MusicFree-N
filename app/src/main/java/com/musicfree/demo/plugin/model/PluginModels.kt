package com.musicfree.demo.plugin.model

import kotlinx.serialization.Serializable

/**
 * MusicFree 在线插件订阅清单格式 (plugins.json)
 * 参考: https://github.com/maotoumao/MusicFreePlugins/blob/master/plugins.json
 */
@Serializable
data class PluginSubscriptionManifest(
    val desc: String? = null,
    val plugins: List<PluginSubscriptionEntry> = emptyList()
)

@Serializable
data class PluginSubscriptionEntry(
    val name: String = "",
    val url: String = "",
    val version: String? = null
)

/**
 * 已加载的 MusicFree 插件元数据 (对应 IPluginDefine)
 */
@Serializable
data class PluginMeta(
    val id: String,
    val platform: String,
    val version: String = "0.0.0",
    val author: String = "",
    val srcUrl: String = "",
    val defaultSearchType: String = "music",
    val supportedSearchType: List<String> = listOf("music"),
    val supportedMethods: List<String> = emptyList(),
    val userVariables: List<PluginUserVariable> = emptyList()
)

@Serializable
data class PluginUserVariable(
    val key: String,
    val name: String? = null,
    val hint: String? = null
)

/**
 * 对应 MusicFree IMusicItem，同时保留 rawJson 以便调用 getMediaSource / getLyric 时无损回传插件自定义字段
 */
@Serializable
data class MusicItem(
    val id: String,
    val platform: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
    val duration: Long? = null,
    val rawJson: String = "{}"
)

data class SearchResult(
    val isEnd: Boolean,
    val data: List<MusicItem>,
    val rawJson: String
)

@Serializable
data class MediaSourceResult(
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val quality: String? = null,
    val rawJson: String = "{}"
)

@Serializable
data class LyricResult(
    val rawLrc: String? = null,
    val translation: String? = null,
    val rawJson: String = "{}"
)

@Serializable
data class TopListGroup(
    val title: String = "",
    val data: List<TopListItem> = emptyList()
)

@Serializable
data class TopListItem(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val coverImg: String? = null
)

enum class LogLevel { INFO, HTTP, WARN, ERROR }

data class SandboxLogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)
