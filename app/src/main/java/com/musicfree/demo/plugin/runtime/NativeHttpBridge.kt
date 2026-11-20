package com.musicfree.demo.plugin.runtime

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 内存级 CookieJar，既为 OkHttp 自动管理 Cookie，也供 @react-native-cookies/cookies Shim 读写。
 */
class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        synchronized(list) {
            cookies.forEach { incoming ->
                list.removeAll { it.name == incoming.name && it.path == incoming.path }
                if (incoming.expiresAt > System.currentTimeMillis()) {
                    list.add(incoming)
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        store.forEach { (host, cookies) ->
            if (url.host == host || url.host.endsWith(".$host")) {
                synchronized(cookies) {
                    cookies.removeAll { it.expiresAt <= now }
                    result.addAll(cookies.filter { it.matches(url) })
                }
            }
        }
        return result
    }

    fun getCookiesMap(urlString: String): Map<String, String> {
        val httpUrl = urlString.toHttpUrlOrNull() ?: return emptyMap()
        return loadForRequest(httpUrl).associate { it.name to it.value }
    }

    fun setCookie(urlString: String, name: String, value: String) {
        val httpUrl = urlString.toHttpUrlOrNull() ?: return
        val cookie = Cookie.Builder()
            .hostOnlyDomain(httpUrl.host)
            .path("/")
            .name(name)
            .value(value)
            .build()
        saveFromResponse(httpUrl, listOf(cookie))
    }

    fun clearAll() {
        store.clear()
    }
}

@Serializable
data class NativeHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val params: Map<String, JsonElement>? = null,
    val data: JsonElement? = null,
    val responseType: String = "text",
    val timeout: Long = 15000
)

@Serializable
data class NativeHttpResponse(
    val status: Int,
    val statusText: String,
    val headers: Map<String, String>,
    val data: String,
    val isBase64: Boolean = false
)

/**
 * 为 QuickJS 中的 axios Shim 提供原生 OkHttp 网络桥接。
 */
class NativeHttpBridge(
    val cookieJar: InMemoryCookieJar = InMemoryCookieJar(),
    private val onLog: (String) -> Unit = {}
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetchRawText(url: String): String = withContext(Dispatchers.IO) {
        onLog("GET $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MusicFreeNativeDemo/0.1")
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }
            response.body?.string() ?: ""
        }
    }

    suspend fun executeFromJs(requestJson: String): String = withContext(Dispatchers.IO) {
        val req = json.decodeFromString<NativeHttpRequest>(requestJson)
        val parsedUrl = req.url.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("无效 URL: ${req.url}")

        val urlBuilder = parsedUrl.newBuilder()
        req.params?.forEach { (k, v) ->
            val valueStr = if (v is JsonPrimitive) v.contentOrNull else v.toString()
            if (valueStr != null) {
                urlBuilder.addQueryParameter(k, valueStr)
            }
        }
        val finalUrl = urlBuilder.build()
        val method = req.method.uppercase()
        onLog("$method $finalUrl")

        val builder = Request.Builder().url(finalUrl)
        var contentTypeHeader: String? = null
        req.headers.forEach { (k, v) ->
            if (k.equals("Content-Type", ignoreCase = true)) {
                contentTypeHeader = v
            }
            builder.header(k, v)
        }

        if (method in listOf("POST", "PUT", "PATCH", "DELETE")) {
            val bodyString = when (val payload = req.data) {
                null -> ""
                is JsonPrimitive -> payload.contentOrNull ?: ""
                is JsonObject -> {
                    if (contentTypeHeader?.contains("x-www-form-urlencoded", ignoreCase = true) == true) {
                        payload.entries.joinToString("&") { (k, v) ->
                            val raw = if (v is JsonPrimitive) v.contentOrNull.orEmpty() else v.toString()
                            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(raw, "UTF-8")}"
                        }
                    } else {
                        payload.toString()
                    }
                }
                else -> payload.toString()
            }
            val mediaType = (contentTypeHeader ?: "application/json; charset=utf-8").toMediaTypeOrNull()
            builder.method(method, bodyString.toRequestBody(mediaType))
        } else {
            builder.method(method, null)
        }

        val client = if (req.timeout > 0 && req.timeout != 15000L) {
            okHttpClient.newBuilder()
                .callTimeout(req.timeout, TimeUnit.MILLISECONDS)
                .build()
        } else {
            okHttpClient
        }

        client.newCall(builder.build()).execute().use { resp ->
            val respHeaders = mutableMapOf<String, String>()
            for (i in 0 until resp.headers.size) {
                respHeaders[resp.headers.name(i).lowercase()] = resp.headers.value(i)
            }
            val isBinary = req.responseType.equals("arraybuffer", ignoreCase = true)
            val bodyData = if (isBinary) {
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                resp.body?.string() ?: ""
            }
            onLog("← ${resp.code} $finalUrl (${bodyData.length} chars)")
            json.encodeToString(
                NativeHttpResponse(
                    status = resp.code,
                    statusText = resp.message,
                    headers = respHeaders,
                    data = bodyData,
                    isBase64 = isBinary
                )
            )
        }
    }
}
