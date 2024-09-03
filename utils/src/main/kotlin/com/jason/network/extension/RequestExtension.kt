package com.jason.network.extension

import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8

inline val Request.cacheKey: String
    get() {
        return cacheKey()
    }

/**
 * 带盐的缓存key
 */
fun Request.cacheKey(salt: String? = null): String {
    return buildString {
        append(url.toString())
        append(method)
        append(headers.joinToString(","))
        if (salt != null) {
            append(salt)
        }
    }.encodeUtf8().md5().hex()
}