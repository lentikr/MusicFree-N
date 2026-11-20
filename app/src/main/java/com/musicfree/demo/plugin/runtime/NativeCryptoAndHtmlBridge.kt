package com.musicfree.demo.plugin.runtime

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class HtmlNodeSnapshot(
    val text: String,
    val html: String,
    val attrs: Map<String, String>
)

/**
 * 为 QuickJS 中的 crypto-js 和 cheerio Shim 提供原生加解密与 HTML 解析桥接。
 */
class NativeCryptoAndHtmlBridge {
    private val json = Json { ignoreUnknownKeys = true }

    fun hashHex(algorithm: String, input: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hmacHex(algorithm: String, input: String, key: String): String {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm))
        val bytes = mac.doFinal(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun aesEncryptBase64(plainText: String, keyUtf8: String, ivUtf8: String, mode: String): String {
        val transformation = if (mode.equals("ECB", ignoreCase = true)) {
            "AES/ECB/PKCS5Padding"
        } else {
            "AES/CBC/PKCS5Padding"
        }
        val cipher = Cipher.getInstance(transformation)
        val keySpec = SecretKeySpec(keyUtf8.toByteArray(Charsets.UTF_8), "AES")
        if (mode.equals("ECB", ignoreCase = true)) {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(ivUtf8.toByteArray(Charsets.UTF_8)))
        }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun aesDecryptUtf8(cipherBase64: String, keyUtf8: String, ivUtf8: String, mode: String): String {
        val transformation = if (mode.equals("ECB", ignoreCase = true)) {
            "AES/ECB/PKCS5Padding"
        } else {
            "AES/CBC/PKCS5Padding"
        }
        val cipher = Cipher.getInstance(transformation)
        val keySpec = SecretKeySpec(keyUtf8.toByteArray(Charsets.UTF_8), "AES")
        if (mode.equals("ECB", ignoreCase = true)) {
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
        } else {
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(ivUtf8.toByteArray(Charsets.UTF_8)))
        }
        val raw = Base64.decode(cipherBase64, Base64.DEFAULT)
        return String(cipher.doFinal(raw), Charsets.UTF_8)
    }

    fun base64Encode(input: String): String =
        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun base64Decode(input: String): String =
        String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)

    /**
     * 使用 Jsoup 执行 CSS 选择器查询，并返回序列化节点数组给 cheerio Shim
     */
    fun selectHtml(html: String, selector: String): String {
        val doc = Jsoup.parse(html)
        val elements = doc.select(selector)
        val nodes = elements.map { el ->
            val attrs = mutableMapOf<String, String>()
            el.attributes().forEach { attr -> attrs[attr.key] = attr.value }
            HtmlNodeSnapshot(
                text = el.text(),
                html = el.html(),
                attrs = attrs
            )
        }
        return json.encodeToString(nodes)
    }
}
