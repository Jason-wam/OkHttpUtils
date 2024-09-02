package com.jason.network.request

import com.jason.network.cache.CacheMode
import com.jason.network.CallManager
import com.jason.network.OkHttpClientUtil
import com.jason.network.UrlBuilder
import com.jason.network.cache.CacheValidDuration
import com.jason.network.converter.ResponseConverter
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

class BoxedRequest<R> {
    var charset: String = "utf-8"
    var cacheValidDuration: Long = CacheValidDuration.FOREVER
    var cacheMode: CacheMode = CacheMode.ONLY_NETWORK
    internal var onError: ((e: Exception) -> Unit)? = null
    internal var onSuccess: ((body: R) -> Unit)? = null
    internal var onResponse: ((response: Response) -> Unit)? = null
    internal var builder: Request.Builder = Request.Builder()
    internal var converter: ResponseConverter<R>? = null
    private val urlBuilder = UrlBuilder()
    internal var client = OkHttpClientUtil.client.newBuilder().apply { CallManager.bind(this) }.build()
    internal var standAloneCacheKay: String? = null

    /**
     * 修改当前Request的OkHttpClient配置, 不会影响全局默认的OkHttpClient
     */
    fun setClient(block: OkHttpClient.Builder.() -> Unit): BoxedRequest<R> {
        client = client.newBuilder().apply(block).apply { CallManager.bind(this) }.build()
        return this
    }

    fun setCache(mode: CacheMode, duration: Long = CacheValidDuration.FOREVER): BoxedRequest<R> {
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

    fun url(url: String): BoxedRequest<R> {
        urlBuilder.host(url)
        builder.url(urlBuilder.build())
        return this
    }

    fun path(path: String): BoxedRequest<R> {
        urlBuilder.path(path)
        builder.url(urlBuilder.build())
        return this
    }

    fun param(key: String, value: Any): BoxedRequest<R> {
        urlBuilder.param(key, value)
        builder.url(urlBuilder.build())
        return this
    }

    fun headers(headers: Headers): BoxedRequest<R> {
        builder.headers(headers)
        return this
    }

    fun header(name: String, value: String): BoxedRequest<R> {
        builder.header(name, value)
        return this
    }

    fun addHeader(name: String, value: String): BoxedRequest<R> {
        builder.addHeader(name, value)
        return this
    }

    fun tag(tag: Any): BoxedRequest<R> {
        builder.tag(tag)
        return this
    }

    fun post(body: RequestBody): BoxedRequest<R> {
        builder.post(body)
        return this
    }

    fun put(body: RequestBody): BoxedRequest<R> {
        builder.put(body)
        return this
    }

    fun delete(body: RequestBody?): BoxedRequest<R> {
        builder.delete(body)
        return this
    }

    fun patch(body: RequestBody): BoxedRequest<R> {
        builder.patch(body)
        return this
    }

    fun get(): BoxedRequest<R> {
        builder.get()
        return this
    }

    fun head(): BoxedRequest<R> {
        builder.head()
        return this
    }


    fun setBaseRequest(config: Request.Builder.() -> Unit): BoxedRequest<R> {
        builder.apply(config)
        return this
    }

    fun setCharset(charset: String): BoxedRequest<R> {
        this.charset = charset
        this.urlBuilder.charset(charset)
        return this
    }

    fun setCacheMode(cacheMode: CacheMode): BoxedRequest<R> {
        this.cacheMode = cacheMode
        return this
    }

    /**
     * 设置缓存Key，默认为 url + method + headers
     *
     * 通常情况下无需设置此参数，但在某些情况下，例如：
     *
     * 1. 如果Method为 POST，则可能根据需要追加使用 body 的 唯一标识
     *
     * 2. 请求的url为动态的，例如：url为：/api/info/{id}，id为动态参数，
     *    此时，默认缓存Key无法满足需求，此时，需要考虑手动设置缓存Key
     *
     */
    fun setCacheKey(key: String): BoxedRequest<R> {
        this.standAloneCacheKay = key
        return this
    }

    /**
     * 设置缓存时长
     * @param duration 单位：毫秒
     */
    fun setCacheValidDuration(duration: Long): BoxedRequest<R> {
        this.cacheValidDuration = duration
        return this
    }

    fun setConverter(converter: ResponseConverter<R>): BoxedRequest<R> {
        this.converter = converter
        return this
    }

    /**
     *  同步请求下载时不会执行，直接抛出异常
     */
    fun onError(onError: ((e: Exception) -> Unit)): BoxedRequest<R> {
        this.onError = onError
        return this
    }

    fun onSuccess(onSuccess: ((body: R) -> Unit)): BoxedRequest<R> {
        this.onSuccess = onSuccess
        return this
    }

    /**
     * 在 [onError] 或 [onSuccess] 前回调 Response
     */
    fun onResponse(onResponse: ((response: Response) -> Unit)): BoxedRequest<R> {
        this.onResponse = onResponse
        return this
    }
}