package com.jason.network.extension

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.File

fun File.verifyMD5(
    md5: String, onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)? = null
): Boolean {
    val result = inputStream().use { it.md5(onVerifyFile) }
    println("MD5: $result")
    return result.lowercase() == md5.lowercase()
}

fun File.verifySHA1(
    sha1: String, onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)? = null
): Boolean {
    val result = inputStream().use { it.sha1(onVerifyFile) }
    println("SHA1: $result")
    return result.lowercase() == sha1.lowercase()
}

fun File.verifyShA256(
    sha256: String, onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)? = null
): Boolean {
    val result = inputStream().use { it.sha256(onVerifyFile) }
    println("SHA256: $result")
    return result.lowercase() == sha256.lowercase()
}

/**
 * 返回文件的MediaType值, 如果不存在返回null
 */
fun File.mediaType(): MediaType? {
    return ClassLoader.getSystemClassLoader().getResourceAsStream("MimeType.json")?.bufferedReader()?.use {
        JSONObject(it.readText())
    }?.let {
        if (it.has(nameWithoutExtension)) {
            it.getString(nameWithoutExtension).toMediaTypeOrNull()
        } else {
            null
        }
    }
}

/**
 * 创建File的RequestBody
 * @param contentType 如果为null则通过判断扩展名来生成MediaType
 */
fun File.toRequestBody(contentType: MediaType? = null): RequestBody {
    val fileMediaType = contentType ?: mediaType()
    return object : RequestBody() {

        override fun contentType(): MediaType? {
            return fileMediaType
        }

        override fun contentLength() = length()

        override fun writeTo(sink: BufferedSink) {
            source().use { source ->
                sink.writeAll(source)
            }
        }
    }
}