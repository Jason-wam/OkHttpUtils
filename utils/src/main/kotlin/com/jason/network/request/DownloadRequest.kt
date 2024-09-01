package com.jason.network.request

import com.jason.network.CallManager
import com.jason.network.OkHttpClientUtil
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File

class DownloadRequest {
    private var builder: Request.Builder = Request.Builder()
    internal var filename: String? = null
    internal var saveDirectory: File? = null
    internal var overwrite: Boolean = false
    internal var onError: ((e: Exception) -> Unit)? = null
    internal var onSuccess: ((file: File) -> Unit)? = null
    internal var onResponse: ((response: Response) -> Unit)? = null

    internal var onProgress: ((percent: Float, downloadBytes: Long, totalBytes: Long) -> Unit)? = null
    internal var rangeDownload: Boolean = false
    internal var md5: String = ""
    internal var sha1: String = ""
    internal var sha256: String = ""
    internal var onVerifyFile: ((percent: Float, downloadBytes: Long, totalBytes: Long) -> Unit)? = null
    internal var client = OkHttpClientUtil.downloadClient

    /**
     * 修改当前Request的OkHttpClient配置, 不会影响全局默认的OkHttpClient
     */
    fun setClient(block: OkHttpClient.Builder.() -> Unit): DownloadRequest {
        client = client.newBuilder().apply(block).apply { CallManager.bind(this) }.build()
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

    fun setBaseRequest(request: Request.Builder.() -> Unit): DownloadRequest {
        builder.apply(request)
        return this
    }

    fun url(url: String): DownloadRequest {
        builder.url(url)
        return this
    }

    fun headers(headers: Headers): DownloadRequest {
        builder.headers(headers)
        return this
    }

    fun header(name: String, value: String): DownloadRequest {
        builder.header(name, value)
        return this
    }

    fun addHeader(name: String, value: String): DownloadRequest {
        builder.addHeader(name, value)
        return this
    }

    fun tag(tag: Any): DownloadRequest {
        builder.tag(tag)
        return this
    }

    fun setFileName(filename: String): DownloadRequest {
        this.filename = filename
        return this
    }

    fun setSaveDirectory(directory: File): DownloadRequest {
        this.saveDirectory = directory
        return this
    }

    fun setOverwrite(overwrite: Boolean): DownloadRequest {
        this.overwrite = overwrite
        return this
    }

    /**
     * 开启断点续传, 默认不开启且必须指定文件名方可生效
     * 优先级低于 [overwrite],如果设置了 [overwrite] 为 true,则不会生效
     */
    fun enableRangeDownload(enableRangeDownload: Boolean): DownloadRequest {
        this.rangeDownload = enableRangeDownload
        return this
    }

    /**
     *  同步请求下载时不会执行，直接抛出异常
     */
    fun onError(onError: ((e: Exception) -> Unit)): DownloadRequest {
        this.onError = onError
        return this
    }

    fun onSuccess(onSuccess: ((file: File) -> Unit)): DownloadRequest {
        this.onSuccess = onSuccess
        return this
    }

    fun onProgress(onProgress: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)): DownloadRequest {
        this.onProgress = onProgress
        return this
    }

    /**
     * 设置校验文件 MD5 值
     *
     * MD5、SHA-1、SHA-256 三种校验方式依次执行，校验顺序为 MD5 -> SHA-1 -> SHA-256
     */
    fun setMD5(md5: String): DownloadRequest {
        this.md5 = md5
        return this
    }

    /**
     * 设置校验文件 SHA-1 值
     *
     * MD5、SHA-1、SHA-256 三种校验方式依次执行，校验顺序为 MD5 -> SHA-1 -> SHA-256
     */
    fun setSHA1(sha1: String): DownloadRequest {
        this.sha1 = sha1
        return this
    }

    /**
     * 设置校验文件 SHA-256 值
     *
     * MD5、SHA-1、SHA-256 三种校验方式依次执行，校验顺序为 MD5 -> SHA-1 -> SHA-256
     */
    fun setSHA256(sha256: String): DownloadRequest {
        this.sha256 = sha256
        return this
    }

    /**
     * 文件校验进度监听
     *
     * 如果文件校验不通过则会抛出异常，异步下载则会回调 [onError]
     */
    fun onVerifyFile(onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)): DownloadRequest {
        this.onVerifyFile = onVerifyFile
        return this
    }

    /**
     * 在 [onError] 或 [onSuccess] 前回调 Response
     */
    fun onResponse(onResponse: ((response: Response) -> Unit)): DownloadRequest {
        this.onResponse = onResponse
        return this
    }
}