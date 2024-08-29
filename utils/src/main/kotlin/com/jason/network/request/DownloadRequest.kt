package com.jason.network.request

import okhttp3.Headers
import okhttp3.Request
import java.io.File

class DownloadRequest {
    private var builder: Request.Builder = Request.Builder()
    internal var filename: String? = null
    internal var saveDirectory: File? = null
    internal var overwrite: Boolean = false
    internal var onError: ((e: Exception) -> Unit)? = null
    internal var onSucceed: ((file: File) -> Unit)? = null
    internal var onProgress: ((percent: Float, downloadBytes: Long, totalBytes: Long) -> Unit)? = null
    internal var rangeDownload: Boolean = false

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

    fun onSucceed(onSucceed: ((file: File) -> Unit)): DownloadRequest {
        this.onSucceed = onSucceed
        return this
    }

    fun onProgress(onProgress: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)): DownloadRequest {
        this.onProgress = onProgress
        return this
    }
}