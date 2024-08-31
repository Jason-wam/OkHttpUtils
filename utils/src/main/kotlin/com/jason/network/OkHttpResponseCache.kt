package com.jason.network

import com.jakewharton.disklrucache.DiskLruCache
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import okio.buffer
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object OkHttpResponseCache {
    private var cache: DiskLruCache? = null

    const val VALID_FOREVER = -1L

    private const val CACHE_KEY_INFO = 0
    private const val CACHE_KEY_BODY = 1
    private const val CACHE_KEY_TIME = 2

    internal fun init(dir: File) {
        try {
            cache = DiskLruCache.open(dir, 1, 3, 1024 * 1024 * 1024 * 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    internal fun put(request: Request, response: Response): Response {
        try {
            cache ?: return response
            cache?.remove(request.cacheKey) //覆盖数据
            val editor = cache!!.edit(request.cacheKey)
            editor[CACHE_KEY_INFO] = response.info()
            editor[CACHE_KEY_BODY] = response.body?.byteString()?.hex()
            editor[CACHE_KEY_TIME] = System.currentTimeMillis().toString()
            editor.commit()
        } catch (e: Exception) {
            e.printStackTrace()
            return response
        }
        return get(request)!!
    }

    @OptIn(ExperimentalStdlibApi::class)
    internal fun get(request: Request, validDuration: Long = VALID_FOREVER): Response? {
        try {
            cache ?: return null
            val snapshot = cache!!.get(request.cacheKey)
            val info = snapshot?.getString(CACHE_KEY_INFO)?.let { JSONObject(it) }
            val body = snapshot?.getString(CACHE_KEY_BODY)?.decodeHex()?.toByteArray()
            val time = snapshot?.getString(CACHE_KEY_TIME)?.toLongOrNull() ?: 0
            info ?: return null
            if (validDuration == VALID_FOREVER || validDuration > System.currentTimeMillis() - time) {
                val headers = Headers.Builder()
                val array = info.getJSONArray("responseHeaders")
                for (i in 0 until array.length()) {
                    val header = array.getJSONObject(i)
                    headers.add(header.getString("name"), header.getString("value"))
                }

                return Response.Builder().apply {
                    request(request)
                    code(info.getInt("code"))
                    headers(headers.build())
                    message(info.getString("message"))
                    protocol(info.getString("protocol").let {
                        Protocol.valueOf(it)
                    })
                    sentRequestAtMillis(info.getLong("sentRequestAtMillis"))
                    receivedResponseAtMillis(info.getLong("receivedResponseAtMillis"))

                    if (body != null) {
                        body(CacheResponseBody(body, headers["Content-Type"], headers["Content-Length"]))
                    }
                }.build()
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun Response.info(): String {
        return JSONObject().apply {
            put("code", code)
            put("method", request.method)
            put("message", message)
            put("protocol", protocol.name)
            put("sentRequestAtMillis", sentRequestAtMillis)
            put("receivedResponseAtMillis", receivedResponseAtMillis)
            put("responseHeaders", JSONArray().apply {
                for (i in 0 until headers.size) {
                    put(JSONObject().apply {
                        put("name", headers.name(i))
                        put("value", headers.value(i))
                    })
                }
            })
        }.toString()
    }

    private class CacheResponseBody(val byteArray: ByteArray, val contentType: String?, val contentLength: String?) :
        ResponseBody() {
        val source = byteArray.inputStream().source().buffer()
        override fun contentType(): MediaType? {
            return contentType?.toMediaTypeOrNull()
        }

        override fun contentLength(): Long {
            return contentLength?.toLong() ?: byteArray.size.toLong()
        }

        override fun source(): BufferedSource {
            return source
        }

        override fun close() {
            super.close()
            source.close()
        }
    }
}