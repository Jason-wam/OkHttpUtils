package com.jason.network

import com.jason.network.converter.DataDecoder
import okhttp3.*
import okio.use
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object OkHttpClientUtil {
    private var decoder: DataDecoder? = null
    private val calls = HashMap<String, Call>()
    private val client by lazy {
        OkHttpClient.Builder().apply {
            trustSSLCertificate()
            followRedirects(true)
            followSslRedirects(true)
            hostnameVerifier { _, _ -> true }
            retryOnConnectionFailure(true)
            callTimeout(60, TimeUnit.SECONDS)
            connectTimeout(60, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
            writeTimeout(60, TimeUnit.SECONDS)
            dispatcher(Dispatcher().apply {
                maxRequests = 20
            })

            eventListener(object : EventListener() {
                override fun callStart(call: Call) {
                    super.callStart(call)
                    calls[call.request().url.toString()] = call
                    OkhttpLogger.i("OkHttpClient", "callStart: ${call.request()}")
                }

                override fun callFailed(call: Call, ioe: IOException) {
                    super.callFailed(call, ioe)
                    calls.remove(call.request().url.toString())
                    OkhttpLogger.e("OkHttpClient", "callFailed: ${call.request()} , reason: $ioe")
                }

                override fun callEnd(call: Call) {
                    super.callEnd(call)
                    calls.remove(call.request().url.toString())
                    OkhttpLogger.i("OkHttpClient", "callEnd: ${call.request()}")
                }

                override fun canceled(call: Call) {
                    super.canceled(call)
                    calls.remove(call.request().url.toString())
                    OkhttpLogger.i("OkHttpClient", "canceled: ${call.request()}")
                }

                override fun connectFailed(
                    call: Call,
                    inetSocketAddress: InetSocketAddress,
                    proxy: Proxy,
                    protocol: Protocol?,
                    ioe: IOException
                ) {
                    super.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
                    calls.remove(call.request().url.toString())
                    OkhttpLogger.e("OkHttpClient", "connectFailed: ${call.request()} , reason: $ioe")
                }

                override fun dnsStart(call: Call, domainName: String) {
                    super.dnsStart(call, domainName)
                    OkhttpLogger.i("OkHttpClient", "dnsStart: ${call.request()} , domainName: $domainName")
                }

                override fun dnsEnd(
                    call: Call, domainName: String, inetAddressList: List<InetAddress>
                ) {
                    super.dnsEnd(call, domainName, inetAddressList)
                    OkhttpLogger.i(
                        "OkHttpClient",
                        "dnsEnd: ${call.request()} , domainName: $domainName , inetAddressList: ${inetAddressList.size}"
                    )
                }

                override fun requestFailed(call: Call, ioe: IOException) {
                    super.requestFailed(call, ioe)
                    calls.remove(call.request().url.toString())
                    OkhttpLogger.e("OkHttpClient", "requestFailed: ${call.request()} , reason: $ioe")
                }

                override fun responseFailed(call: Call, ioe: IOException) {
                    super.responseFailed(call, ioe)
                    calls.remove(call.request().url.toString())
                    OkhttpLogger.e("OkHttpClient", "responseFailed: ${call.request()} , reason: $ioe")
                }
            })
        }.build()
    }

    fun setCacheDir(cacheDir: File) {
        OkHttpCacheStore.init(cacheDir)
    }

    fun setDecoder(decoder: DataDecoder) {
        this.decoder = decoder
    }

    fun newClient(config: (OkHttpClient.Builder.() -> Unit)? = null): OkHttpClient {
        val builder = client.newBuilder()
        if (config != null) {
            builder.apply(config)
        }
        return builder.build()
    }

    /**
     * 先读取缓存，如果缓存不存在，则执行请求，并将结果写入缓存
     */
    @Throws(IOException::class)
    fun readElseExecute(
        request: Request, charset: String = "utf-8", cacheValidDuration: Long = OkHttpCacheStore.CACHE_FOREVER
    ): String {
        return (OkHttpCacheStore.get(request.cacheKey) ?: executeThenWrite(
            request, charset, cacheValidDuration
        )).let {
            decoder?.convert(it) ?: it
        }
    }

    /**
     * 先执行请求，并将结果写入缓存
     */
    @Throws(IOException::class)
    fun executeThenWrite(
        request: Request, charset: String = "utf-8", cacheValidDuration: Long = OkHttpCacheStore.CACHE_FOREVER
    ): String {
        val call = client.newCall(request)
        call.execute().use { response ->
            if (call.isCanceled()) throw IOException("call is canceled")

            if (response.isRedirect) {
                val location = response.header("Location")
                if (location?.isNotBlank() == true) {
                    return executeThenWrite(request.newBuilder().url(location).build(), charset).let {
                        decoder?.convert(it) ?: it
                    }
                }
            }

            if (response.isSuccessful) {
                return response.body?.source().use { source ->
                    source?.readString(Charset.forName(charset)) ?: ""
                }.also {
                    OkHttpCacheStore.put(request.cacheKey, it, cacheValidDuration)
                }.let {
                    decoder?.convert(it) ?: it
                }
            } else {
                throw IOException("response is not successful: ${response.code}")
            }
        }
    }

    /**
     * 先执行请求，如果请求失败，则读取缓存
     */
    @Throws(IOException::class)
    fun executeElseRead(
        request: Request, charset: String = "utf-8", cacheValidDuration: Long = OkHttpCacheStore.CACHE_FOREVER
    ): String {
        return try {
            executeThenWrite(request, charset, cacheValidDuration)
        } catch (e: Exception) {
            e.printStackTrace()
            OkHttpCacheStore.get(request.cacheKey, true)?.let {
                return decoder?.convert(it) ?: it
            } ?: throw IOException("${e.message} , and cache not found!")
        }
    }

    /**
     * 先读取缓存，如果缓存不存在，则执行请求，并将结果写入缓存
     * 异步模式
     */
    fun readElseEnqueue(
        request: Request,
        charset: String = "utf-8",
        cacheValidDuration: Long = OkHttpCacheStore.CACHE_FOREVER,
        block: (body: String, e: Exception?) -> Unit
    ) {
        val cacheBody = OkHttpCacheStore.get(request.cacheKey)?.let {
            decoder?.convert(it) ?: it
        }
        if (cacheBody != null) {
            block.invoke(cacheBody, null)
        } else {
            enqueueThenWrite(request, charset, cacheValidDuration, block)
        }
    }

    /**
     * 先执行请求，如果请求失败，则读取缓存
     */
    @Throws(IOException::class)
    fun enqueueElseRead(
        request: Request,
        charset: String = "utf-8",
        cacheValidDuration: Long = OkHttpCacheStore.CACHE_FOREVER,
        block: (body: String, th: Throwable?) -> Unit
    ) {
        enqueueThenWrite(request, charset, cacheValidDuration) { s, e ->
            if (e == null) {
                block.invoke(s, null)
            } else {
                OkHttpCacheStore.get(request.cacheKey, true)?.let {
                    decoder?.convert(it) ?: it
                }?.let { cache ->
                    block.invoke(cache, null)
                } ?: let {
                    block.invoke("", e)
                }
            }
        }
    }

    /**
     * 先执行请求，并将结果写入缓存
     * 异步模式
     */
    fun enqueueThenWrite(
        request: Request,
        charset: String = "utf-8",
        cacheValidDuration: Long = OkHttpCacheStore.CACHE_FOREVER,
        block: (body: String, e: Exception?) -> Unit
    ) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    block.invoke("", e)
                } else {
                    OkhttpLogger.e("OkHttpClient", "enqueueThenWrite: ${call.request()} , call canceled!")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) {
                    OkhttpLogger.i("OkHttpClient", "enqueueThenWrite: ${call.request()} , call canceled!")
                    return
                }
                if (response.isRedirect) {
                    val location = response.header("Location")
                    if (location?.isNotBlank() == true) {
                        enqueueThenWrite(
                            request.newBuilder().url(location).build(), charset, cacheValidDuration, block
                        )
                    } else {
                        block.invoke("", IOException("Redirect Location is empty!"))
                    }
                } else {
                    if (!response.isSuccessful || response.body == null) {
                        block.invoke(
                            "", IOException("response is not successful: ${response.code} , and cache not found!")
                        )
                    } else {
                        response.body?.source().use { source ->
                            source?.readString(Charset.forName(charset)) ?: ""
                        }.also {
                            OkHttpCacheStore.put(request.cacheKey, it, cacheValidDuration)
                        }.let {
                            block.invoke(decoder?.convert(it) ?: it, null)
                        }
                    }
                }
            }
        })
    }

    @Throws(IOException::class)
    fun download(
        url: String,
        directory: File,
        overwrite: Boolean = false,
        onProgress: ((progress: Int, totalCopied: Long, totalSize: Long) -> Unit)? = null
    ): File {
        val request = Request.Builder().url(url).get().build()
        return download(request, directory, overwrite, onProgress)
    }

    @Throws(IOException::class)
    fun download(
        request: Request,
        directory: File,
        overwrite: Boolean = false,
        onProgress: ((progress: Int, totalCopied: Long, totalSize: Long) -> Unit)? = null
    ): File {
        client.newCall(request).execute().use { response ->
            if (response.isRedirect) {
                val location = response.header("Location")
                if (location?.isNotBlank() == true) {
                    println("redirect to $location")
                    return download(request.newBuilder().url(location).build(), directory, overwrite)
                } else {
                    throw IOException("Redirect url is empty!")
                }
            } else {
                if (!response.isSuccessful) {
                    throw IOException("response is not successful: ${response.code}")
                } else {
                    val file = File(directory, response.fileName())
                    if (overwrite) {
                        if (file.exists()) {
                            file.delete()
                        }
                    }

                    val contentLength = response.body?.contentLength() ?: -1L
                    if (file.exists() && file.length() == contentLength) {
                        return file
                    } else {
                        response.body?.source()?.inputStream()?.use { input ->
                            file.createNewFile()
                            file.outputStream().use { out ->
                                if (onProgress == null) {
                                    input.copyTo(out)
                                } else {
                                    input.copyWithProgress(contentLength, out, onProgress)
                                }
                            }

                            return file
                        } ?: let {
                            throw IOException("response body is null")
                        }
                    }
                }
            }
        }
    }

    fun downloadAsync(
        url: String,
        directory: File,
        overwrite: Boolean = false,
        onError: ((e: Exception) -> Unit)? = null,
        onProgress: ((progress: Int, totalCopied: Long, totalSize: Long) -> Unit)? = null,
        onSucceed: (file: File) -> Unit
    ) {
        val request = Request.Builder().url(url).get().build()
        downloadAsync(request, directory, overwrite, onError, onProgress, onSucceed)
    }

    fun downloadAsync(
        request: Request,
        directory: File,
        overwrite: Boolean = false,
        onError: ((e: Exception) -> Unit)? = null,
        onProgress: ((progress: Int, totalCopied: Long, totalSize: Long) -> Unit)? = null,
        onSucceed: (file: File) -> Unit
    ) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    onError?.invoke(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!call.isCanceled()) {
                    if (response.isRedirect) {
                        val location = response.header("Location")
                        if (location?.isNotBlank() == true) {
                            println("redirect to $location")
                            downloadAsync(
                                request.newBuilder().url(location).build(),
                                directory,
                                overwrite,
                                onError,
                                onSucceed = onSucceed
                            )
                        }
                    } else {
                        if (!response.isSuccessful) {
                            onError?.invoke(IOException("response is not successful: ${response.code}"))
                        } else {
                            val file = File(directory, response.fileName())
                            if (overwrite) {
                                if (file.exists()) {
                                    file.delete()
                                }
                            }

                            val contentLength = response.body?.contentLength() ?: -1L
                            if (file.exists() && file.length() == contentLength) {
                                onSucceed.invoke(file)
                            } else {
                                response.body?.source()?.inputStream()?.use { input ->
                                    try {
                                        file.createNewFile()
                                        file.outputStream().use { out ->
                                            if (onProgress == null) {
                                                input.copyTo(out)
                                            } else {
                                                input.copyWithProgress(contentLength, out, onProgress)
                                            }
                                        }
                                        onSucceed.invoke(file)
                                    } catch (e: Exception) {
                                        onError?.invoke(e)
                                    }
                                } ?: let {
                                    onError?.invoke(IOException("response body is null"))
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    fun cancel(call: Call) {
        call.cancel()
        calls.remove(call.request().cacheKey)
    }

    fun cancelByTag(tag: Any) {
        calls.filter {
            it.value.request().tag() == tag
        }.onEach {
            it.value.cancel()
        }.forEach {
            calls.remove(it.key)
        }
    }

    fun cancelAll() {
        calls.onEach { it.value.cancel() }.clear()
    }
}