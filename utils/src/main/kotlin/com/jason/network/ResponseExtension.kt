package com.jason.network

import okhttp3.Response
import java.net.URLDecoder

/**
 * 获取响应的文件名
 */
fun Response.fileName(defaultName: String = ""): String {
    val disposition = header("Content-Disposition")
    if (disposition != null) {
        disposition.substringAfter("filename=", "").takeUnless { it.isBlank() }?.let { return it }
        disposition.substringAfter("filename*=", "").trimStart(*"UTF-8''".toCharArray()).takeUnless { it.isBlank() }
            ?.let { return it }
    }

    var fileName: String = request.url.pathSegments.last().substringBefore("?")
    fileName = if (fileName.isBlank()) {
        defaultName.ifBlank {
            "unknown_${System.currentTimeMillis()}"
        }
    } else {
        try {
            URLDecoder.decode(fileName, "UTF8")
        } catch (e: Exception) {
            fileName
        }
    }
    return fileName
}