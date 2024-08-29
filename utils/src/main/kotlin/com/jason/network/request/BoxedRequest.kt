package com.jason.network.request

import com.jason.network.CacheMode
import com.jason.network.DataDecoder
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody

class BoxedRequest {
    internal var decoder: DataDecoder? = null
    internal var charset: String = "utf-8"
    internal var cacheValidDuration: Long = CacheDuration.FOREVER
    internal var cacheMode: CacheMode = CacheMode.ONLY_NETWORK
    internal var onError: ((e: Exception) -> Unit)? = null
    internal var onSucceed: ((body: String) -> Unit)? = null
    private var builder: Request.Builder = Request.Builder()

    /**
     * 缓存时长
     * NEVER 禁用缓存，无论什么缓存模式都不会写入缓存
     * FOREVER 永久缓存，不会失效
     */
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
            return builder.build()
        }

    val url: String
        get() {
            return request.url.toString()
        }

    fun url(url: String): BoxedRequest {
        builder.url(url)
        return this
    }

    fun headers(headers: Headers): BoxedRequest {
        builder.headers(headers)
        return this
    }

    fun header(name: String, value: String): BoxedRequest {
        builder.header(name, value)
        return this
    }

    fun addHeader(name: String, value: String): BoxedRequest {
        builder.addHeader(name, value)
        return this
    }

    fun tag(tag: Any): BoxedRequest {
        builder.tag(tag)
        return this
    }

    fun post(body: RequestBody): BoxedRequest {
        builder.post(body)
        return this
    }

    fun put(body: RequestBody): BoxedRequest {
        builder.put(body)
        return this
    }

    fun delete(body: RequestBody?): BoxedRequest {
        builder.delete(body)
        return this
    }

    fun patch(body: RequestBody): BoxedRequest {
        builder.patch(body)
        return this
    }

    fun get(): BoxedRequest {
        builder.get()
        return this
    }

    fun head(): BoxedRequest {
        builder.head()
        return this
    }


    fun setBaseRequest(config: Request.Builder.() -> Unit): BoxedRequest {
        builder.apply(config)
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

    /**
     * 设置缓存时长
     * @param duration 单位：毫秒
     */
    fun setCacheValidDuration(duration: Long): BoxedRequest {
        this.cacheValidDuration = duration
        return this
    }

    fun setCacheMode(cacheMode: CacheMode): BoxedRequest {
        this.cacheMode = cacheMode
        return this
    }

    /**
     *  同步请求下载时不会执行，直接抛出异常
     */
    fun onError(onError: ((e: Exception) -> Unit)): BoxedRequest {
        this.onError = onError
        return this
    }

    fun onSucceed(onSucceed: ((body: String) -> Unit)): BoxedRequest {
        this.onSucceed = onSucceed
        return this
    }
}