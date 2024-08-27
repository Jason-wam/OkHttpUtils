package com.jason.network

import okhttp3.Request
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8

inline val Request.cacheKey: String
    get() {
        return buildString {
            append(url.toString())
            append(method)
            append(headers.joinToString(","))
            append(Buffer().apply { body?.writeTo(buffer) }.sha1().hex())
        }.encodeUtf8().sha1().hex()
    }