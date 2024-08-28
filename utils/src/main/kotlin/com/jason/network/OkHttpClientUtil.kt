package com.jason.network

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

    fun newClient(config: (OkHttpClient.Builder.() -> Unit)? = null): OkHttpClient {
        val builder = client.newBuilder()
        if (config != null) {
            builder.apply(config)
        }
        return builder.build()
    }

    @Throws(IOException::class)
    fun execute(request: Request, charset: String): String {
        return client.newCall(request).execute().use {
            if (it.isRedirect) {
                val location = it.header("Location") ?: throw IOException("No location found")
                return execute(request.newBuilder().url(location).build(), charset)
            }
            if (it.isSuccessful) {
                it.body?.source()?.readString(Charset.forName(charset)) ?: ""
            } else {
                throw IOException("Request failed with code ${it.code}")
            }
        }
    }

    private fun readCache(request: Request): String? {
        return OkHttpCacheStore.get(request.cacheKey)
    }

    @Throws(IOException::class)
    fun execute(config: BoxedRequest.() -> Unit): String {
        return execute(BoxedRequest().apply(config))
    }

    @Throws(IOException::class)
    fun execute(request: BoxedRequest): String {
        return when (request.cacheMode) {
            CacheMode.ONLY_CACHE -> {
                readCache(request.request)?.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                } ?: throw IOException("No cache found!")
            }

            CacheMode.ONLY_NETWORK -> {
                execute(request.request, request.charset).also {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                }.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }
            }

            CacheMode.NETWORK_ELSE_CACHE -> {
                try {
                    execute(request.request, request.charset).also {
                        OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                    }.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    OkHttpCacheStore.get(request.request.cacheKey)?.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    } ?: throw IOException("Request failed with code ${e.message} and no cache found!")
                }
            }

            CacheMode.CACHE_ELSE_NETWORK -> {
                readCache(request.request)?.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                } ?: execute(request.request, request.charset).also {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                }.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }
            }
        }
    }


    fun enqueue(
        request: Request, charset: String, onError: ((e: Exception) -> Unit)? = null, onSucceed: (body: String) -> Unit
    ) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError?.invoke(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isRedirect) {
                    val location = response.header("Location")
                    if (location?.isNotBlank() == true) {
                        enqueue(request.newBuilder().url(location).build(), charset, onError, onSucceed)
                    } else {
                        onError?.invoke(IOException("Response is redirect but location not found!"))
                    }
                } else {
                    if (response.isSuccessful) {
                        onSucceed.invoke(response.body?.source()?.readString(Charset.forName(charset)) ?: "")
                    } else {
                        onError?.invoke(IOException("Request failed with code ${response.code}"))
                    }
                }
            }
        })
    }

    fun enqueue(
        config: BoxedRequest.() -> Unit, onError: ((e: Exception) -> Unit)? = null, onSucceed: (body: String) -> Unit
    ) {
        val request = BoxedRequest().apply(config)
        enqueue(request, onError, onSucceed)
    }

    fun enqueue(request: BoxedRequest, onError: ((e: Exception) -> Unit)? = null, onSucceed: (body: String) -> Unit) {
        when (request.cacheMode) {
            CacheMode.ONLY_CACHE -> {
                readCache(request.request)?.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }?.let {
                    onSucceed(it)
                } ?: let {
                    onError?.invoke(IOException("No cache found!"))
                }
            }

            CacheMode.ONLY_NETWORK -> {
                enqueue(request.request, request.charset, onError = {
                    onError?.invoke(it)
                }, onSucceed = {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                    onSucceed(it.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    })
                })
            }

            CacheMode.NETWORK_ELSE_CACHE -> {
                enqueue(request.request, request.charset, onError = { e ->
                    readCache(request.request)?.let {
                        onSucceed(it)
                    } ?: let {
                        onError?.invoke(IOException(buildString {
                            append(e.message)
                            append(" and no cache found!")
                        }))
                    }
                }, onSucceed = {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                    onSucceed(it.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    })
                })
            }

            CacheMode.CACHE_ELSE_NETWORK -> {
                readCache(request.request)?.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }?.let {
                    onSucceed(it)
                } ?: enqueue(request.request, request.charset, onError = onError, onSucceed = {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                    onSucceed(it.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    })
                })
            }
        }
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