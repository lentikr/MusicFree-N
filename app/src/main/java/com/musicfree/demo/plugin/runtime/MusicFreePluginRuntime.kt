package com.musicfree.demo.plugin.runtime

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.musicfree.demo.plugin.model.LogLevel
import com.musicfree.demo.plugin.model.LyricResult
import com.musicfree.demo.plugin.model.MediaSourceResult
import com.musicfree.demo.plugin.model.MusicItem
import com.musicfree.demo.plugin.model.PluginMeta
import com.musicfree.demo.plugin.model.PluginSubscriptionEntry
import com.musicfree.demo.plugin.model.PluginSubscriptionManifest
import com.musicfree.demo.plugin.model.SandboxLogEntry
import com.musicfree.demo.plugin.model.SearchResult
import com.musicfree.demo.plugin.model.TopListGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class OnlineImportOutcome {
    data class SinglePluginLoaded(val plugin: PluginMeta) : OnlineImportOutcome()
    data class SubscriptionDetected(
        val manifestDesc: String?,
        val entries: List<PluginSubscriptionEntry>
    ) : OnlineImportOutcome()
}

/**
 * MusicFree 插件运行时宿主：
 * 负责维护 QuickJS 实例、注入原生桥接、管理本地持久化插件与调度 IPluginDefine 接口。
 */
class MusicFreePluginRuntime(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val pluginsDir: File = File(context.filesDir, "mf_plugins").apply { mkdirs() }
    private val engineMutex = Mutex()

    private val _logs = MutableStateFlow<List<SandboxLogEntry>>(emptyList())
    val logs: StateFlow<List<SandboxLogEntry>> = _logs.asStateFlow()

    val httpBridge = NativeHttpBridge(
        onLog = { msg -> appendLog(LogLevel.HTTP, "OkHttp", msg) }
    )
    private val cryptoAndHtmlBridge = NativeCryptoAndHtmlBridge()

    private var quickJs: QuickJs? = null

    private fun appendLog(level: LogLevel, tag: String, message: String) {
        val entry = SandboxLogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        _logs.update { (listOf(entry) + it).take(200) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private suspend fun ensureEngine(): QuickJs {
        quickJs?.let { return it }
        val engine = QuickJs.create(Dispatchers.IO)
        engine.evaluationTimeoutMillis = 20_000L

        engine.define("nativeConsole") {
            function("log") { args ->
                appendLog(LogLevel.INFO, "JS", args.joinToString(" "))
            }
            function("warn") { args ->
                appendLog(LogLevel.WARN, "JS", args.joinToString(" "))
            }
            function("error") { args ->
                appendLog(LogLevel.ERROR, "JS", args.joinToString(" "))
            }
        }

        engine.define("nativeHttp") {
            asyncFunction("request") { args ->
                val reqJson = args.firstOrNull()?.toString() ?: "{}"
                httpBridge.executeFromJs(reqJson)
            }
        }

        engine.define("nativeCrypto") {
            function("hashHex") { args ->
                cryptoAndHtmlBridge.hashHex(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty()
                )
            }
            function("hmacHex") { args ->
                cryptoAndHtmlBridge.hmacHex(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty(),
                    args.getOrNull(2)?.toString().orEmpty()
                )
            }
            function("aesEncryptBase64") { args ->
                cryptoAndHtmlBridge.aesEncryptBase64(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty(),
                    args.getOrNull(2)?.toString().orEmpty(),
                    args.getOrNull(3)?.toString() ?: "CBC"
                )
            }
            function("aesDecryptUtf8") { args ->
                cryptoAndHtmlBridge.aesDecryptUtf8(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty(),
                    args.getOrNull(2)?.toString().orEmpty(),
                    args.getOrNull(3)?.toString() ?: "CBC"
                )
            }
            function("base64Encode") { args ->
                cryptoAndHtmlBridge.base64Encode(args.getOrNull(0)?.toString().orEmpty())
            }
            function("base64Decode") { args ->
                cryptoAndHtmlBridge.base64Decode(args.getOrNull(0)?.toString().orEmpty())
            }
        }

        engine.define("nativeHtml") {
            function("select") { args ->
                cryptoAndHtmlBridge.selectHtml(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty()
                )
            }
        }

        engine.define("nativeCookie") {
            function("get") { args ->
                val map = httpBridge.cookieJar.getCookiesMap(args.getOrNull(0)?.toString().orEmpty())
                json.encodeToString(map)
            }
            function("set") { args ->
                httpBridge.cookieJar.setCookie(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty(),
                    args.getOrNull(2)?.toString().orEmpty()
                )
            }
            function("clearAll") {
                httpBridge.cookieJar.clearAll()
            }
        }

        val status = engine.evaluate<String>(MusicFreeSandboxBootstrap.SCRIPT)
        appendLog(LogLevel.INFO, "Runtime", "QuickJS 沙箱初始化完成 ($status)")
        quickJs = engine
        return engine
    }

    /**
     * 启动时恢复已持久化的插件
     */
    suspend fun restorePersistedPlugins(): List<PluginMeta> = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<PluginMeta>()
        pluginsDir.listFiles { f -> f.extension == "js" }?.sortedBy { it.name }?.forEach { file ->
            runCatching {
                val id = file.nameWithoutExtension
                loadPluginCode(id, file.readText(), fallbackUrl = "local://${file.name}", persist = false)
            }.onSuccess { meta ->
                loaded.add(meta)
            }.onFailure { err ->
                appendLog(LogLevel.WARN, "Restore", "恢复插件 ${file.name} 失败: ${err.message}")
            }
        }
        loaded
    }

    /**
     * 在线导入：自动识别 MusicFree 订阅清单 (plugins.json) 或单个插件 (.js)
     */
    suspend fun importFromOnlineUrl(url: String): OnlineImportOutcome {
        val trimmed = url.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "请输入以 http:// 或 https:// 开头的有效地址"
        }
        val rawText = httpBridge.fetchRawText(trimmed)
        val trimmedBody = rawText.trimStart()

        // 优先尝试解析 plugins.json 订阅清单
        if (trimmed.endsWith(".json", ignoreCase = true) || trimmedBody.startsWith("{") || trimmedBody.startsWith("[")) {
            val manifest = runCatching {
                if (trimmedBody.startsWith("[")) {
                    val entries = json.decodeFromString<List<PluginSubscriptionEntry>>(trimmedBody)
                    PluginSubscriptionManifest(plugins = entries)
                } else {
                    json.decodeFromString<PluginSubscriptionManifest>(trimmedBody)
                }
            }.getOrNull()

            if (manifest != null && manifest.plugins.isNotEmpty()) {
                appendLog(LogLevel.INFO, "Import", "识别到插件订阅源，共 ${manifest.plugins.size} 个插件")
                return OnlineImportOutcome.SubscriptionDetected(manifest.desc, manifest.plugins)
            }
        }

        // 否则按单个 CommonJS 插件源码加载
        val pluginId = "p_" + UUID.randomUUID().toString().take(8)
        val meta = loadPluginCode(pluginId, rawText, fallbackUrl = trimmed, persist = true)
        return OnlineImportOutcome.SinglePluginLoaded(meta)
    }

    /**
     * 载入内置的 iTunes 公开接口验证插件
     */
    suspend fun importBuiltInDemoPlugin(): PluginMeta = withContext(Dispatchers.IO) {
        val code = context.assets.open("demo_itunes_plugin.js").bufferedReader().use { it.readText() }
        loadPluginCode("builtin_itunes_demo", code, fallbackUrl = "asset://demo_itunes_plugin.js", persist = true)
    }

    suspend fun loadPluginCode(
        pluginId: String,
        sourceCode: String,
        fallbackUrl: String,
        persist: Boolean
    ): PluginMeta = engineMutex.withLock {
        val engine = ensureEngine()
        val idJson = json.encodeToString(pluginId)
        val codeJson = json.encodeToString(sourceCode)
        val urlJson = json.encodeToString(fallbackUrl)
        val metaJson = engine.evaluate<String>(
            "globalThis.__mf_loadPlugin($idJson, $codeJson, $urlJson)"
        )
        val meta = json.decodeFromString<PluginMeta>(metaJson)
        if (persist) {
            withContext(Dispatchers.IO) {
                File(pluginsDir, "$pluginId.js").writeText(sourceCode)
            }
        }
        appendLog(
            LogLevel.INFO,
            "Plugin",
            "已加载插件「${meta.platform}」v${meta.version}，支持接口: ${meta.supportedMethods.joinToString()}"
        )
        meta
    }

    suspend fun removePlugin(pluginId: String) = engineMutex.withLock {
        quickJs?.evaluate<String>("globalThis.__mf_unloadPlugin(${json.encodeToString(pluginId)})")
        withContext(Dispatchers.IO) {
            File(pluginsDir, "$pluginId.js").delete()
        }
        appendLog(LogLevel.INFO, "Plugin", "已移除插件 $pluginId")
    }

    suspend fun search(
        plugin: PluginMeta,
        query: String,
        page: Int = 1,
        type: String = "music"
    ): SearchResult = engineMutex.withLock {
        val engine = ensureEngine()
        appendLog(LogLevel.INFO, "API", "调用 ${plugin.platform}.search('$query', $page, '$type')")
        val raw = engine.evaluate<String>(
            "globalThis.__mf_search(${json.encodeToString(plugin.id)}, ${json.encodeToString(query)}, $page, ${json.encodeToString(type)})"
        )
        val root = json.parseToJsonElement(raw).jsonObject
        val isEnd = root["isEnd"]?.jsonPrimitive?.booleanOrNull ?: true
        val dataArray = root["data"] as? JsonArray ?: JsonArray(emptyList())
        val items = dataArray.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            MusicItem(
                id = obj["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
                platform = obj["platform"]?.jsonPrimitive?.contentOrNull ?: plugin.platform,
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "(无标题)",
                artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: "",
                album = obj["album"]?.jsonPrimitive?.contentOrNull ?: "",
                artwork = obj["artwork"]?.jsonPrimitive?.contentOrNull,
                duration = obj["duration"]?.jsonPrimitive?.longOrNull,
                rawJson = obj.toString()
            )
        }
        appendLog(LogLevel.INFO, "API", "search() 返回 ${items.size} 条结果 (isEnd=$isEnd)")
        SearchResult(isEnd = isEnd, data = items, rawJson = raw)
    }

    suspend fun getMediaSource(
        plugin: PluginMeta,
        item: MusicItem,
        quality: String = "standard"
    ): MediaSourceResult = engineMutex.withLock {
        val engine = ensureEngine()
        appendLog(LogLevel.INFO, "API", "调用 ${plugin.platform}.getMediaSource('${item.title}', '$quality')")
        val raw = engine.evaluate<String>(
            "globalThis.__mf_getMediaSource(${json.encodeToString(plugin.id)}, ${json.encodeToString(item.rawJson)}, ${json.encodeToString(quality)})"
        )
        val obj = json.parseToJsonElement(raw).jsonObject
        val headersMap = (obj["headers"] as? JsonObject)?.mapValues { (_, v) ->
            (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
        } ?: emptyMap()

        val result = MediaSourceResult(
            url = obj["url"]?.jsonPrimitive?.contentOrNull,
            headers = headersMap,
            userAgent = obj["userAgent"]?.jsonPrimitive?.contentOrNull,
            quality = obj["quality"]?.jsonPrimitive?.contentOrNull ?: quality,
            rawJson = raw
        )
        appendLog(LogLevel.INFO, "API", "getMediaSource() -> ${result.url ?: "null"}")
        result
    }

    suspend fun getLyric(
        plugin: PluginMeta,
        item: MusicItem
    ): LyricResult = engineMutex.withLock {
        val engine = ensureEngine()
        appendLog(LogLevel.INFO, "API", "调用 ${plugin.platform}.getLyric('${item.title}')")
        val raw = engine.evaluate<String>(
            "globalThis.__mf_getLyric(${json.encodeToString(plugin.id)}, ${json.encodeToString(item.rawJson)})"
        )
        val obj = json.parseToJsonElement(raw).jsonObject
        val result = LyricResult(
            rawLrc = obj["rawLrc"]?.jsonPrimitive?.contentOrNull,
            translation = obj["translation"]?.jsonPrimitive?.contentOrNull,
            rawJson = raw
        )
        appendLog(LogLevel.INFO, "API", "getLyric() 返回 ${result.rawLrc?.length ?: 0} 字符")
        result
    }

    suspend fun getTopLists(plugin: PluginMeta): List<TopListGroup> = engineMutex.withLock {
        val engine = ensureEngine()
        appendLog(LogLevel.INFO, "API", "调用 ${plugin.platform}.getTopLists()")
        val raw = engine.evaluate<String>(
            "globalThis.__mf_getTopLists(${json.encodeToString(plugin.id)})"
        )
        val groups = json.decodeFromString<List<TopListGroup>>(raw)
        appendLog(LogLevel.INFO, "API", "getTopLists() 返回 ${groups.size} 个榜单分组")
        groups
    }

    fun close() {
        quickJs?.close()
        quickJs = null
    }
}
