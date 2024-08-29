package com.jason.network

import com.jason.network.request.BoxedRequest
import com.jason.network.request.DownloadRequest
import okhttp3.*
import okio.use
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object OkHttpClientUtil {
    private val callManager by lazy { CallManager() }
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
        OkHttpCacheStore.init(cacheDir)
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

    private fun getCache(request: Request): String? {
        return OkHttpCacheStore.get(request.cacheKey)
    }

    /**
     * 执行网络请求并返回响应结果
     *
     * @param request 请求对象，包含了请求的URL、参数、方法等信息
     * @param charset 字符集编码，用于解析响应体
     * @return 响应体的内容，以字符串形式返回
     * @throws IOException 如果请求失败、重定向出现问题或响应体解析失败时抛出此异常
     */
    @Throws(IOException::class)
    fun execute(request: Request, charset: String): String {
        // 发送请求并获取响应对象
        client.newCall(request).execute().use { response ->
            // 如果响应指示需要重定向
            if (response.isRedirect) {
                // 获取重定向的位置信息，如果不存在则抛出异常
                val location = response.header("Location") ?: throw IOException("No location found")
                // 使用新的URL重新构建请求并执行
                return execute(request.newBuilder().url(location).build(), charset)
            }
            // 如果请求成功
            if (response.isSuccessful) {
                // 返回响应体的内容，使用指定的字符集进行解析
                return response.body?.source()?.readString(Charset.forName(charset)) ?: ""
            } else {
                // 如果请求失败，根据响应码抛出异常
                throw IOException("Request failed with code ${response.code}")
            }
        }
    }

    /**
     * 执行同步请求
     * 此函数通过高阶函数参数接收请求配置，允许在执行前动态指定请求的各种参数
     * 主要用于在保持代码灵活和可读性的同时，执行一次性配置的请求
     *
     * @param config 请求的配置块，用于在执行请求前设置请求的各种参数
     * @return 返回请求的执行结果，类型为String
     * @throws IOException 如果请求执行过程中发生输入输出异常，则抛出此异常
     */
    @Throws(IOException::class)
    fun execute(config: BoxedRequest.() -> Unit): String {
        return execute(BoxedRequest().apply(config))
    }

    /**
     * 执行同步请求
     * @param request BoxedRequest类型的请求对象，包含了所有与请求相关的信息，
     *                如请求头、请求体、请求类型等
     * @return 返回一个字符串类型的响应结果，通常为JSON、XML或其他自定义的格式，
     *         具体格式取决于系统设计和业务需求
     */
    @Throws(IOException::class)
    fun execute(request: BoxedRequest): String {
        return when (request.cacheMode) {
            CacheMode.ONLY_CACHE -> {
                getCache(request.request)?.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }?.also {
                    request.onSucceed?.invoke(it)
                } ?: throw IOException("No cache found!")
            }

            CacheMode.ONLY_NETWORK -> {
                execute(request.request, request.charset).also {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                }.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }.also {
                    request.onSucceed?.invoke(it)
                }
            }

            CacheMode.NETWORK_ELSE_CACHE -> {
                try {
                    execute(request.request, request.charset).also {
                        OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                    }.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    }.also {
                        request.onSucceed?.invoke(it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    OkHttpCacheStore.get(request.request.cacheKey)?.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    }?.also {
                        request.onSucceed?.invoke(it)
                    } ?: throw IOException("Request failed with code ${e.message} and no cache found!")
                }
            }

            CacheMode.CACHE_ELSE_NETWORK -> {
                getCache(request.request)?.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }?.also {
                    request.onSucceed?.invoke(it)
                } ?: execute(request.request, request.charset).also {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                }.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }.also {
                    request.onSucceed?.invoke(it)
                }
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
    fun enqueue(request: BoxedRequest) {
        // 调用enqueue方法，传入BoxedRequest对象的相应属性
        // 这里不直接处理请求，而是将请求的细节封装并传递
        enqueue(request.request, request.charset, request.onError, request.onSucceed)
    }

    /**
     * 将请求加入队列并异步执行
     *
     * @param request 请求对象，包含了具体的请求方法和URL等信息
     * @param charset 请求数据的字符集，用于正确解析响应体
     * @param onError 错误回调，当请求发生异常时被调用默认为空
     * @param onSucceed 成功回调，当请求成功完成时被调用，传入解析后的响应体
     */
    fun enqueue(
        request: Request,
        charset: String,
        onError: ((e: Exception) -> Unit)? = null,
        onSucceed: ((body: String) -> Unit)? = null
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
                            enqueue(request.newBuilder().url(location).build(), charset, onError, onSucceed)
                        } else {
                            onError?.invoke(IOException("Response is redirect but location not found!"))
                        }
                    } else {
                        if (response.isSuccessful) {
                            onSucceed?.invoke(response.body?.source()?.readString(Charset.forName(charset)) ?: "")
                        } else {
                            onError?.invoke(IOException("Request failed with code ${response.code}"))
                        }
                    }
                }
            }
        })
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
     * 重要性：
     * 这个函数是请求处理流程的入口，所有需要发送的请求都通过这个函数进行配置和排队
     */
    fun enqueue(config: BoxedRequest.() -> Unit) {
        BoxedRequest().apply(config).let {
            enqueue(it, it.onError, it.onSucceed)
        }
    }

    /**
     * 将一个任务加入队列并执行
     *
     * 该函数用于接收一个任务配置（通过 BoxedRequest.() -> Unit 闭包定义），执行该任务，并处理其成功或失败的结果
     * 它是enqueue函数家族的一部分，专门用于处理异步任务的队列执行和结果处理
     *
     * @param config 一个 Lambda 表达式，用于配置要执行的任务请求参数它定义了任务的具体执行逻辑
     * @param onError 一个可选的 Lambda 表达式，当任务执行发生异常时被调用用于处理错误情况如果不设置，默认为 null，即不处理错误
     * @param onSucceed 一个 Lambda 表达式，当任务成功完成时被调用接收一个 String 类型的响应体参数，代表任务的成功结果
     */
    fun enqueue(
        config: BoxedRequest.() -> Unit,
        onError: ((e: Exception) -> Unit)? = null,
        onSucceed: ((body: String) -> Unit)? = null
    ) {
        enqueue(BoxedRequest().apply(config), onError, onSucceed)
    }

    /**
     * 将请求加入队列并处理结果
     *
     * 该函数的主要作用是将一个封装好的请求对象加入处理队列，并在请求处理完毕后
     * 调用相应的回调函数处理结果或错误。这个机制常用于异步操作，比如网络请求或者
     * 文件操作等，可以有效地分离请求的发起和处理逻辑，提高程序的可读性和可维护性。
     *
     * @param request 请求对象，包含了所有执行操作所需的信息
     * @param onError 错误回调函数，当请求处理发生异常时被调用。默认为null，表示不处理错误
     * @param onSucceed 成功回调函数，当请求处理成功时被调用，携带处理结果的字符串表示
     */
    fun enqueue(
        request: BoxedRequest, onError: ((e: Exception) -> Unit)? = null, onSucceed: ((body: String) -> Unit)? = null
    ) {
        when (request.cacheMode) {
            CacheMode.ONLY_CACHE -> {
                getCache(request.request)?.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }?.let {
                    onSucceed?.invoke(it)
                } ?: let {
                    onError?.invoke(IOException("No cache found!"))
                }
            }

            CacheMode.ONLY_NETWORK -> {
                enqueue(request.request, request.charset, onError = {
                    onError?.invoke(it)
                }, onSucceed = {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                    onSucceed?.invoke(it.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    })
                })
            }

            CacheMode.NETWORK_ELSE_CACHE -> {
                enqueue(request.request, request.charset, onError = { e ->
                    getCache(request.request)?.let {
                        onSucceed?.invoke(it)
                    } ?: let {
                        onError?.invoke(IOException(buildString {
                            append(e.message)
                            append(" and no cache found!")
                        }))
                    }
                }, onSucceed = {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                    onSucceed?.invoke(it.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    })
                })
            }

            CacheMode.CACHE_ELSE_NETWORK -> {
                getCache(request.request)?.let {
                    if (request.decoder != null) request.decoder!!.convert(it) else it
                }?.let {
                    onSucceed?.invoke(it)
                } ?: enqueue(request.request, request.charset, onError = onError, onSucceed = {
                    OkHttpCacheStore.put(request.request.cacheKey, it, request.cacheValidDuration)
                    onSucceed?.invoke(it.let {
                        if (request.decoder != null) request.decoder!!.convert(it) else it
                    })
                })
            }
        }
    }

    @Throws(IOException::class)
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
            request.onSucceed?.invoke(it)
        }
    }

    @Throws(IOException::class)
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
            request.onSucceed?.invoke(it)
        }
    }

    @Throws(IOException::class)
    fun download(
        url: String,
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
        val request = Request.Builder().url(url).get().build()
        return download(
            request, directory, filename, overwrite, rangeDownload, md5, sha1, sha256, onVerifyFile, onProgress
        )
    }

    @Throws(IOException::class)
    fun download(
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
                if(!file.verifyMD5(md5, onVerifyFile)){
                    throw IOException("File MD5 verification failed!")
                }
            }

            if (sha1.isNotEmpty()) {
                if(!file.verifySHA1(sha1, onVerifyFile)){
                    throw IOException("File SHA-1 verification failed!")
                }
            }

            if (sha256.isNotEmpty()) {
                if(file.verifyShA256(sha256, onVerifyFile)){
                    throw IOException("File SHA-256 verification failed!")
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
                request.onSucceed
            )
        }
    }

    fun downloadAsync(
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
                        onError?.invoke(IOException("File MD5 verification failed!"))
                    }
                    return
                }

                if (sha1.isNotEmpty()) {
                    if (file.verifySHA1(sha1, onVerifyFile)) {
                        onSucceed?.invoke(file)
                    } else {
                        onError?.invoke(IOException("File SHA-1 verification failed!"))
                    }
                    return
                }

                if (sha256.isNotEmpty()) {
                    if (file.verifyShA256(sha256, onVerifyFile)) {
                        onSucceed?.invoke(file)
                    } else {
                        onError?.invoke(IOException("File SHA-256 verification failed!"))
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