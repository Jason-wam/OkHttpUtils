package com.jason.network

import com.jason.network.converter.JSONArrayConverter
import com.jason.network.converter.JSONObjectConverter
import com.jason.network.converter.ResponseConverter
import com.jason.network.converter.StringConverter
import com.jason.network.request.BoxedRequest
import com.jason.network.request.DownloadRequest
import okhttp3.*
import okio.use
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

object OkHttpClientUtil {
    private val callManager by lazy { CallManager() }
    private val converters = ArrayList<ResponseConverter<*>>().apply {
        add(StringConverter())
        add(JSONObjectConverter())
        add(JSONArrayConverter())
    }
    private var client: OkHttpClient = OkHttpClient.Builder().apply {
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
            maxRequests = 64
        })
        callManager.bind(this)
    }.build()

    private val downloadClient by lazy {
        client.newBuilder().apply {
            callTimeout(10, TimeUnit.DAYS)
            connectTimeout(10, TimeUnit.DAYS)
            readTimeout(10, TimeUnit.DAYS)
            writeTimeout(10, TimeUnit.DAYS)
        }.build()
    }

    fun setClient(config: (OkHttpClient.Builder.() -> Unit)? = null) {
        if (config != null) {
            client = client.newBuilder().apply(config).build()
        }
    }

    fun setCacheDir(cacheDir: File) {
        OkHttpResponseCache.init(cacheDir)
    }

    fun setLogEnabled(enable: Boolean) {
        OkhttpLogger.enabled = enable
    }

    fun newClient(config: (OkHttpClient.Builder.() -> Unit)? = null): OkHttpClient {
        val builder = client.newBuilder()
        if (config != null) {
            builder.apply(config)
        }
        return builder.build()
    }

    /**
     * 执行同步请求
     * 此函数通过高阶函数参数接收请求配置，允许在执行前动态指定请求的各种参数
     * 主要用于在保持代码灵活和可读性的同时，执行一次性配置的请求
     *
     * @param config 请求的配置块，用于在执行请求前设置请求的各种参数
     * @return 返回请求的执行结果，类型为String
     * @throws Exception 如果请求执行过程中发生异常，则抛出此异常
     */
    @Throws(Exception::class)
    inline fun <reified R> execute(config: BoxedRequest<R>.() -> Unit): R {
        return execute(BoxedRequest<R>().apply(config), R::class)
    }

    fun addConverter(converter: ResponseConverter<*>) {
        converters.add(converter)
    }

    private fun BoxedRequest<*>.findConverter(type: KClass<*>): ResponseConverter<*>? {
        return converter ?: converters.find {
            it.supportType() == type
        }
    }

    /**
     * 执行同步请求
     * @param request BoxedRequest类型的请求对象，包含了所有与请求相关的信息，
     *                如请求头、请求体、请求类型等
     * @return 返回一个字符串类型的响应结果，通常为JSON、XML或其他自定义的格式，
     *         具体格式取决于系统设计和业务需求
     */
    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    fun <R> execute(request: BoxedRequest<R>, type: KClass<*>): R {
        return when (request.cacheMode) {
            CacheMode.ONLY_CACHE -> {
                OkHttpResponseCache.get(request.request, request.cacheValidDuration)?.let {
                    request.onResponse?.invoke(it)
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        throw Exception("Converter not found for $type")
                    }
                    converter.convert(request, it)
                } ?: throw IOException("No cache found!")
            }

            CacheMode.ONLY_NETWORK -> {
                executeResponse(request.request).use {
                    request.onResponse?.invoke(it)
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        throw Exception("Converter not found for $type")
                    }
                    converter.convert(request, it)
                }.also {
                    request.onSuccess?.invoke(it)
                }
            }

            CacheMode.NETWORK_ELSE_CACHE -> {
                val response = try {
                    executeResponse(request.request)
                } catch (e: Exception) {
                    OkHttpResponseCache.get(request.request, request.cacheValidDuration)
                        ?: throw IOException("Request failed: ${e.message}, and no cache found!")
                }
                response.use {
                    request.onResponse?.invoke(it)
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        throw Exception("Converter not found for $type")
                    }
                    converter.convert(request, it)
                }.also {
                    request.onSuccess?.invoke(it)
                }
            }

            CacheMode.CACHE_ELSE_NETWORK -> {
                val response =
                    OkHttpResponseCache.get(request.request, request.cacheValidDuration) ?: executeResponse(request.request)

                response.use {
                    request.onResponse?.invoke(it)
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        throw Exception("Converter not found for $type")
                    }
                    converter.convert(request, it)
                }.also {
                    request.onSuccess?.invoke(it)
                }
            }
        }
    }

    @Throws(IOException::class)
    fun executeResponse(request: Request): Response {
        // 发送请求并获取响应对象
        client.newCall(request).execute().use { response ->
            // 如果响应指示需要重定向
            if (response.isRedirect) {
                // 获取重定向的位置信息，如果不存在则抛出异常
                val location = response.header("Location") ?: throw IOException("No location found")
                // 使用新的URL重新构建请求并执行
                return executeResponse(request.newBuilder().url(location).build())
            }
            // 如果请求成功
            if (response.isSuccessful) {
                // 返回响应体的内容，使用指定的字符集进行解析
                return OkHttpResponseCache.put(request, response)
            } else {
                // 如果请求失败，根据响应码抛出异常
                throw IOException("Request failed with code ${response.code}")
            }
        }
    }

    /**
     * 将打包的请求加入队列
     *
     * 此方法将一个BoxedRequest对象加入处理队列，BoxedRequest包含了执行请求所需的所有信息
     * 包括具体的请求、字符集、错误处理回调和成功处理回调
     *
     * @param request BoxedRequest对象，包含了所有执行请求所需的信息
     */
    inline fun <reified R> enqueue(request: BoxedRequest<R>) {
        enqueue<R>(request, R::class)
    }

    /**
     * 将一个配置化的请求加入队列
     *
     * @param config BoxedRequest的Lambda表达式，用于在调用点内配置请求
     *
     * 此函数的作用是提供一种链式调用的方式，使得请求的配置更加清晰和简洁
     * 它首先创建一个BoxedRequest实例，然后应用传入的配置Lambda来配置这个请求
     * 最后，它将这个配置好的请求以及其成功和失败的回调加入到处理队列中
     *
     * 为什么这么做：
     * 这种设计允许调用者以更加直观和便捷的方式配置和添加请求，避免了繁琐的构造器或者多个参数的方法调用
     *
     */
    inline fun <reified R> enqueue(config: BoxedRequest<R>.() -> Unit) {
        val request = BoxedRequest<R>().apply(config)
        enqueue<R>(request, R::class)
    }

    @Suppress("UNCHECKED_CAST")
    fun <R> enqueue(request: BoxedRequest<R>, type: KClass<*>) {
        when (request.cacheMode) {
            CacheMode.ONLY_CACHE -> {
                OkHttpResponseCache.get(request.request, request.cacheValidDuration)?.also {
                    request.onResponse?.invoke(it)
                }?.let {
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        request.onError?.invoke(IOException("Converter not found for $type"))
                    } else {
                        try {
                            request.onSuccess?.invoke(converter.convert(request, it))
                        } catch (e: Exception) {
                            request.onError?.invoke(e)
                        }
                    }
                } ?: request.onError?.invoke(IOException("Cache not found!"))
            }

            CacheMode.ONLY_NETWORK -> {
                enqueueResponse(request.request, onError = {
                    request.onError?.invoke(it)
                }, onSucceed = { response ->
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        request.onError?.invoke(IOException("Converter not found for $type"))
                    } else {
                        try {
                            request.onSuccess?.invoke(converter.convert(request, response))
                        } catch (e: Exception) {
                            request.onError?.invoke(e)
                        }
                    }
                })
            }

            CacheMode.NETWORK_ELSE_CACHE -> {
                enqueueResponse(request.request, onError = { e ->
                    OkHttpResponseCache.get(request.request, request.cacheValidDuration)?.let {
                        val converter = request.findConverter(type) as? ResponseConverter<R>
                        if (converter == null) {
                            request.onError?.invoke(IOException("Converter not found for $type"))
                        } else {
                            try {
                                request.onSuccess?.invoke(converter.convert(request, it))
                            } catch (e: Exception) {
                                request.onError?.invoke(e)
                            }
                        }
                    } ?: let {
                        request.onError?.invoke(IOException(buildString {
                            append(e.message)
                            append(" and no cache found!")
                        }))
                    }
                }, onSucceed = { response ->
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        request.onError?.invoke(IOException("Converter not found for $type"))
                    } else {
                        try {
                            request.onSuccess?.invoke(converter.convert(request, response))
                        } catch (e: Exception) {
                            request.onError?.invoke(e)
                        }
                    }
                })
            }

            CacheMode.CACHE_ELSE_NETWORK -> {
                OkHttpResponseCache.get(request.request, request.cacheValidDuration)?.also {
                    request.onResponse?.invoke(it)
                }?.let {
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        request.onError?.invoke(IOException("Converter not found for $type"))
                    } else {
                        try {
                            request.onSuccess?.invoke(converter.convert(request, it))
                        } catch (e: Exception) {
                            request.onError?.invoke(e)
                        }
                    }
                } ?: enqueueResponse(request.request, onError = {
                    request.onError?.invoke(it)
                }, onSucceed = { response ->
                    val converter = request.findConverter(type) as? ResponseConverter<R>
                    if (converter == null) {
                        request.onError?.invoke(IOException("Converter not found for $type"))
                    } else {
                        try {
                            request.onSuccess?.invoke(converter.convert(request, response))
                        } catch (e: Exception) {
                            request.onError?.invoke(e)
                        }
                    }
                })
            }
        }
    }

    fun enqueueResponse(
        request: Request, onError: ((e: Exception) -> Unit)? = null, onSucceed: ((response: Response) -> Unit)? = null
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
                            enqueueResponse(request.newBuilder().url(location).build(), onError, onSucceed)
                        } else {
                            onError?.invoke(IOException("Response is redirect but location not found!"))
                        }
                    } else {
                        if (response.isSuccessful) {
                            onSucceed?.invoke(OkHttpResponseCache.put(request, response))
                        } else {
                            onError?.invoke(IOException("Request failed with code ${response.code}"))
                        }
                    }
                }
            }
        })
    }

    @Throws(Exception::class)
    fun download(config: DownloadRequest.() -> Unit): File {
        val request = DownloadRequest().apply(config)
        if (request.saveDirectory == null) throw IOException("SaveDirectory is null!")
        return download(
            request.request,
            request.saveDirectory!!,
            request.filename,
            request.overwrite,
            request.rangeDownload,
            request.md5,
            request.sha1,
            request.sha256,
            request.onVerifyFile,
            request.onProgress
        ).also {
            request.onSuccess?.invoke(it)
        }
    }

    @Throws(Exception::class)
    fun download(request: DownloadRequest): File {
        if (request.saveDirectory == null) throw IOException("SaveDirectory is null!")
        return download(
            request.request,
            request.saveDirectory!!,
            request.filename,
            request.overwrite,
            request.rangeDownload,
            request.md5,
            request.sha1,
            request.sha256,
            request.onVerifyFile,
            request.onProgress
        ).also {
            request.onSuccess?.invoke(it)
        }
    }

    @Throws(Exception::class)
    private fun download(
        request: Request,
        directory: File,
        filename: String? = null,
        overwrite: Boolean = false,
        rangeDownload: Boolean,
        md5: String = "",
        sha1: String = "",
        sha256: String = "",
        onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)? = null,
        onProgress: ((percent: Float, downloadBytes: Long, totalBytes: Long) -> Unit)? = null
    ): File {
        var newRequest = request
        if (rangeDownload && filename != null) {
            val file = File(directory, filename)
            val configFile = File(directory, "$filename.cfg")
            if (overwrite && file.exists()) {
                file.delete()
                configFile.delete()
            }
            if (file.exists()) {
                //"bytes=" + startPos + "-" + endPos
                //未指定endPos ，则下载剩余部分
                newRequest = request.newBuilder().header("Range", "bytes=${file.length()}-").build()
            }
        }

        fun verifyFile(file: File) {
            if (md5.isNotEmpty()) {
                if (!file.verifyMD5(md5, onVerifyFile)) {
                    throw FileVerificationException("File MD5 verification failed!")
                }
            }

            if (sha1.isNotEmpty()) {
                if (!file.verifySHA1(sha1, onVerifyFile)) {
                    throw FileVerificationException("File SHA-1 verification failed!")
                }
            }

            if (sha256.isNotEmpty()) {
                if (file.verifyShA256(sha256, onVerifyFile)) {
                    throw FileVerificationException("File SHA-256 verification failed!")
                }
            }
        }

        //下载文件使用单独的Client
        downloadClient.newCall(newRequest).execute().use { response ->
            if (response.isRedirect) {
                val location = response.header("Location")
                if (location?.isNotEmpty() != true) {
                    throw IOException("Redirect url is empty!")
                } else {
                    println("redirect to $location")
                    return download(
                        newRequest.newBuilder().url(location).build(),
                        directory,
                        filename,
                        overwrite,
                        rangeDownload,
                        md5,
                        sha1,
                        sha256,
                        onVerifyFile,
                        onProgress
                    )
                }
            } else {
                if (!response.isSuccessful && response.code != 416) {
                    throw IOException("Response is not successful: ${response.code}")
                } else {
                    val file = File(directory, filename ?: response.fileName())
                    val contentLength = response.body?.contentLength() ?: -1L
                    val configFile = File(directory, "$${file.name}.cfg")
                    when (response.code) {
                        416 -> { //416一般为请求的文件大小范围超出服务器文件大小范围
                            if (file.exists() && file.length() > 0L) {
                                verifyFile(file)
                                configFile.delete()
                                return file
                            } else {
                                throw IOException("Request content range error, code : ${response.code}")
                            }
                        }

                        206 -> {
                            //Content-Range: bytes 18333696-6114656255/6114656256
                            val rangeInfo = response.headers["Content-Range"]
                            if (rangeInfo == null) {
                                throw IOException("Content-Range is null!")
                            } else {
                                val range = rangeInfo.substringAfter("bytes").substringBefore("/")
                                val startPos = range.substringBefore("-").trim().toLong()

                                println("断点续传: startPos = $startPos")

                                //如果文件已存在，则读取配置文件中的TotalBytes
                                var fullBytes = -1L
                                val localFileBytes = file.length()
                                if (configFile.exists()) {
                                    fullBytes = configFile.reader().use {
                                        it.readText().substringAfter("ContentLength=").toLong()
                                    }
                                }

                                response.body?.source()?.inputStream()?.use { input ->
                                    RandomAccessFile(file, "rwd").use {
                                        it.seek(startPos)
                                        input.copyTo(contentLength, it) { percent, bytesCopied, totalBytes ->
                                            if (fullBytes == -1L) {
                                                onProgress?.invoke(percent, bytesCopied, totalBytes)
                                            } else {
                                                val newBytesCopied = localFileBytes + bytesCopied
                                                val newPercent = newBytesCopied / fullBytes.toFloat() * 100
                                                onProgress?.invoke(newPercent, newBytesCopied, fullBytes)
                                            }
                                        }
                                    }
                                } ?: let {
                                    throw IOException("Response body is null!")
                                }
                            }

                            verifyFile(file)
                            configFile.delete()
                            return file
                        }

                        else -> {
                            if (overwrite && file.exists()) {
                                file.delete()
                                configFile.delete()
                            }

                            if (file.exists() && file.length() == contentLength) {
                                verifyFile(file)
                                configFile.delete()
                                return file
                            } else {
                                configFile.outputStream().writer().use {
                                    it.write("ContentLength=$contentLength")
                                }
                                response.body?.source()?.inputStream()?.use { input ->
                                    file.createNewFile()
                                    file.outputStream().use { out ->
                                        if (onProgress == null) {
                                            input.copyTo(out)
                                        } else {
                                            input.copyTo(contentLength, out, onProgress)
                                        }
                                    }

                                    verifyFile(file)
                                    configFile.delete()
                                    return file
                                } ?: let {
                                    configFile.delete()
                                    throw IOException("Response body is null!")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun downloadAsync(request: DownloadRequest) {
        if (request.saveDirectory == null) {
            request.onError?.invoke(IOException("SaveDirectory is null!"))
        } else {
            downloadAsync(
                request.request,
                request.saveDirectory!!,
                request.filename,
                request.overwrite,
                request.rangeDownload,
                request.md5,
                request.sha1,
                request.sha256,
                request.onVerifyFile,
                request.onError,
                request.onProgress,
                request.onSuccess
            )
        }
    }

    fun downloadAsync(config: DownloadRequest.() -> Unit) {
        val request = DownloadRequest().apply(config)
        if (request.saveDirectory == null) {
            request.onError?.invoke(IOException("SaveDirectory is null!"))
        } else {
            downloadAsync(
                request.request,
                request.saveDirectory!!,
                request.filename,
                request.overwrite,
                request.rangeDownload,
                request.md5,
                request.sha1,
                request.sha256,
                request.onVerifyFile,
                request.onError,
                request.onProgress,
                request.onSuccess
            )
        }
    }

    private fun downloadAsync(
        request: Request,
        directory: File,
        filename: String? = null,
        overwrite: Boolean = false,
        rangeDownload: Boolean = false,
        md5: String = "",
        sha1: String = "",
        sha256: String = "",
        onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)? = null,
        onError: ((e: Exception) -> Unit)? = null,
        onProgress: ((percent: Float, downloadBytes: Long, totalBytes: Long) -> Unit)? = null,
        onSucceed: ((file: File) -> Unit)? = null
    ) {
        var newRequest = request
        if (rangeDownload && filename != null) {
            val file = File(directory, filename)
            val configFile = File(directory, "$filename.cfg")
            if (overwrite && file.exists()) {
                file.delete()
                configFile.delete()
            }
            if (file.exists()) {
                newRequest = request.newBuilder().header("Range", "bytes=${file.length()}-").build()
            }
        }

        fun verifyFile(file: File) {
            try {
                if (md5.isNotEmpty()) {
                    if (file.verifyMD5(md5, onVerifyFile)) {
                        onSucceed?.invoke(file)
                    } else {
                        onError?.invoke(FileVerificationException("File MD5 verification failed!"))
                    }
                    return
                }

                if (sha1.isNotEmpty()) {
                    if (file.verifySHA1(sha1, onVerifyFile)) {
                        onSucceed?.invoke(file)
                    } else {
                        onError?.invoke(FileVerificationException("File SHA-1 verification failed!"))
                    }
                    return
                }

                if (sha256.isNotEmpty()) {
                    if (file.verifyShA256(sha256, onVerifyFile)) {
                        onSucceed?.invoke(file)
                    } else {
                        onError?.invoke(FileVerificationException("File SHA-256 verification failed!"))
                    }
                    return
                }

                onSucceed?.invoke(file)
            } catch (e: Exception) {
                e.printStackTrace()
                onError?.invoke(e)
            }
        }

        downloadClient.newCall(newRequest).enqueue(object : Callback {
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
                                newRequest.newBuilder().url(location).build(),
                                directory,
                                filename,
                                overwrite,
                                rangeDownload,
                                md5,
                                sha1,
                                sha256,
                                onVerifyFile,
                                onError,
                                onProgress,
                                onSucceed
                            )
                        }
                    } else {
                        if (!response.isSuccessful && response.code != 416) {
                            onError?.invoke(IOException("Response is not successful: ${response.code}"))
                        } else {
                            try {
                                val file = File(directory, filename ?: response.fileName())
                                val configFile = File(directory, "${file.name}.cfg")
                                val contentLength = response.body?.contentLength() ?: -1L
                                when (response.code) {
                                    416 -> {//416一般为请求的文件大小范围超出服务器文件大小范围
                                        if (file.exists() && file.length() > 0L) {
                                            verifyFile(file)
                                            configFile.delete()
                                        } else {
                                            onError?.invoke(IOException("Request content range error, code : ${response.code}"))
                                        }
                                    }

                                    206 -> {
                                        //Content-Range: bytes 18333696-6114656255/6114656256
                                        val rangeInfo = response.headers["Content-Range"]
                                        if (rangeInfo == null) {
                                            onError?.invoke(IOException("Content-Range is null!"))
                                        } else {
                                            val range = rangeInfo.substringAfter("bytes").substringBefore("/")
                                            val startPos = range.substringBefore("-").trim().toLong()

                                            println("断点续传: startPos = $startPos")

                                            //如果文件已存在，则读取配置文件中的TotalBytes
                                            var fullBytes = -1L
                                            val localFileBytes = file.length()
                                            if (configFile.exists()) {
                                                try {
                                                    fullBytes = configFile.reader().use {
                                                        it.readText().substringAfter("ContentLength=").toLong()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }

                                            response.body?.source()?.inputStream()?.use { input ->
                                                RandomAccessFile(file, "rwd").use {
                                                    it.seek(startPos)
                                                    input.copyTo(
                                                        contentLength, it
                                                    ) { percent, bytesCopied, totalBytes ->
                                                        if (fullBytes == -1L) {
                                                            onProgress?.invoke(percent, bytesCopied, totalBytes)
                                                        } else {
                                                            val newBytesCopied = localFileBytes + bytesCopied
                                                            val newPercent = newBytesCopied / fullBytes.toFloat() * 100
                                                            onProgress?.invoke(newPercent, newBytesCopied, fullBytes)
                                                        }
                                                    }
                                                }
                                                configFile.delete()

                                                verifyFile(file)
                                            } ?: let {
                                                onError?.invoke(IOException("Response body is null!"))
                                            }
                                        }
                                    }

                                    else -> {
                                        if (overwrite && file.exists()) {
                                            file.delete()
                                            configFile.delete()
                                        }
                                        if (file.exists() && file.length() == contentLength) {
                                            verifyFile(file)
                                        } else {
                                            configFile.outputStream().writer().use {
                                                it.write("ContentLength=$contentLength")
                                            }
                                            response.body?.source()?.inputStream()?.use { input ->
                                                file.createNewFile()
                                                file.outputStream().use { out ->
                                                    if (onProgress == null) {
                                                        input.copyTo(out)
                                                    } else {
                                                        input.copyTo(contentLength, out, onProgress)
                                                    }
                                                }
                                                configFile.delete()

                                                verifyFile(file)
                                            } ?: let {
                                                onError?.invoke(IOException("Response body is null!"))
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                onError?.invoke(e)
                            }
                        }
                    }
                }
            }
        })
    }

    fun cancel(call: Call) {
        callManager.cancel(call)
    }

    fun cancelByTag(tag: Any) {
        callManager.cancelByTag(tag)
    }

    fun cancelAll() {
        callManager.cancelAll()
    }
}