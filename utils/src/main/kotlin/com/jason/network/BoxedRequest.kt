package com.jason.network

import com.jason.network.converter.DataDecoder
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody

class BoxedRequest {
    internal var decoder: DataDecoder? = null
    internal var charset: String = "utf-8"
    internal var cacheValidDuration: Long = CacheDuration.FOREVER
    internal var cacheMode: CacheMode = CacheMode.ONLY_NETWORK
    private var requestBuilder: Request.Builder = Request.Builder()

    object CacheDuration {
        const val NEVER = 0L
        const val FOREVER = -1L
    }

    fun setCache(mode: CacheMode, duration: Long = CacheDuration.FOREVER): BoxedRequest {
        this.cacheMode = mode
        this.cacheValidDuration = duration
        return this
    }

    val request: Request
        get() {
            return requestBuilder.build()
        }

    val url: String
        get() {
            return request.url.toString()
        }

    fun url(url: String): BoxedRequest {
        requestBuilder.url(url)
        return this
    }

    fun headers(headers: Headers): BoxedRequest {
        requestBuilder.headers(headers)
        return this
    }

    fun header(name: String, value: String): BoxedRequest {
        requestBuilder.header(name, value)
        return this
    }

    fun addHeader(name: String, value: String): BoxedRequest {
        requestBuilder.addHeader(name, value)
        return this
    }

    fun tag(tag: Any): BoxedRequest {
        requestBuilder.tag(tag)
        return this
    }

    fun post(body: RequestBody): BoxedRequest {
        requestBuilder.post(body)
        return this
    }

    fun put(body: RequestBody): BoxedRequest {
        requestBuilder.put(body)
        return this
    }

    fun delete(body: RequestBody?): BoxedRequest {
        requestBuilder.delete(body)
        return this
    }

    fun patch(body: RequestBody): BoxedRequest {
        requestBuilder.patch(body)
        return this
    }

    fun get(): BoxedRequest {
        requestBuilder.get()
        return this
    }

    fun head(): BoxedRequest {
        requestBuilder.head()
        return this
    }


    fun setBaseRequest(config: Request.Builder.() -> Unit): BoxedRequest {
        requestBuilder.apply(config)
        return this
    }

    fun setDecoder(decoder: DataDecoder): BoxedRequest {
        this.decoder = decoder
        return this
    }

    fun setCharset(charset: String): BoxedRequest {
        this.charset = charset
        return this
    }

    fun setCacheValidDuration(duration: Long): BoxedRequest {
        this.cacheValidDuration = duration
        return this
    }

    fun setCacheMode(cacheMode: CacheMode): BoxedRequest {
        this.cacheMode = cacheMode
        return this
    }
}