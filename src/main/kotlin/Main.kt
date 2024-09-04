package com.jason.selector

import com.jason.network.cache.CacheMode
import com.jason.network.OkHttpClientUtil
import com.jason.network.cache.CacheValidDuration
import com.jason.network.extension.isFromCache
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

fun main() {
    OkHttpClientUtil.setCacheDir(File("D:/OKHttpCache"))



    download()
}

fun enqueue() {
    OkHttpClientUtil.enqueue<Response> {
        url("https://i1.hdslb.com/bfs/banner/c5595aaa09c1710ba3e32651aab84104d6171b2b.png")
        setCacheMode(CacheMode.CACHE_ELSE_NETWORK)
        setCacheValidDuration(CacheValidDuration.FOREVER)
        onResponse {
            println()
            println("onResponse > 是否是缓存：${it.isFromCache}, size = ${it.body?.contentLength()}")
        }
        onError {
            println()
            println("onError： ${it.message}")
        }
        onSuccess {
            println()
            println("onSuccess：\n${it.headers}")
        }
    }
}

fun post() {
    OkHttpClientUtil.upload<String> {
        url("http://192.168.0.4:8096/test")
        //这里的Param为添加表单数据，而不是追加url参数
        param("from", "Android")
        param("file", File("D:\\GPT-SoVITS-v2-240821.7z"))
        onResponse {
            println()
            println("onResponse > ${it.body?.contentType()}")
        }
        onError {
            println("请求失败： ${it.stackTraceToString()}")
        }
        onProgress { percent: Float, uploadedBytes: Long, totalBytes: Long ->
            print("\r上传进度： $percent")
        }
        onSuccess {
            println("请求成功： \n${it}")
        }
    }
}

fun cancelDownload() {
    println("开始取消下载...")
    thread {
        Thread.sleep(5000)
        OkHttpClientUtil.cancelByTag("download")
        println("已取消下载...")
    }
}

fun download() {
//    cancelDownload()
    //异步请求
    OkHttpClientUtil.downloadAsync() {
        tag("download")
        //shasum -a 256 = 8762f7e74e4d64d72fceb5f70682e6b069932deedb4949c6975d0f0fe0a91be3
        url("https://releases.ubuntu.com/24.04/ubuntu-24.04.1-desktop-amd64.iso")
        setDownloadDir(File("D:/"))
        setDownloadFileName("ubuntu-24.04.1-desktop-amd64.iso")
        setEnableResumeDownload(true)
//        setOverwrite(true)
        setSHA256("c2e6f4dc37ac944e2ed507f87c6188dd4d3179bf4a3f9e110d3c88d1f3294bdc")

        var test = false
        onVerifyFile { percent, totalCopied, totalSize ->
            if (test != true) {
                test = true
                cancelDownload()
            }
            print("\r校验文件： $percent , $totalCopied/$totalSize")
        }

        //同步请求不会执行此处代码，而是直接抛出异常
        onError {
            println()
            println("下载失败： ${it.message}")
        }

        onSuccess {
            println()
            println("下载成功： $it")
        }

        onProgress { percent, downloadBytes, totalBytes ->
            print("\r下载进度： ${String.format("%.2f", percent)} , $downloadBytes/$totalBytes")
        }
    }
}

fun execute() {
    val result = OkHttpClientUtil.execute<JSONObject> {
        url("https://api.bilibili.com/x/click-interface/click/now")
        setCacheMode(CacheMode.ONLY_NETWORK)
        header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        )

        onResponse {
            println(it.body?.contentType())
        }
    }

    println("请求成功： ${result.toString(2)}")
}

fun execute2() {
    OkHttpClientUtil.execute<String> {
        url("https://api.bilibili.com/x/click-interface/click/now")
        setCacheMode(CacheMode.ONLY_NETWORK)
        header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        )

        //由于是同步请求，所以不会执行此处代码，而是直接抛出异常
        onError {
            println("请求失败： ${it.message}")
        }

        //由于是同步请求，所以在回调此处代码块时也会直接返回数据
        onSuccess {
            println("请求成功： $it")
        }
    }
}


fun enqueue2() {
    OkHttpClientUtil.enqueue<JSONObject> {
        url("https://api.bilibili.com/")
        path("x/click-interface/click/now")
        param("from", "Android")
        setCacheMode(CacheMode.CACHE_ELSE_NETWORK)
        setCacheValidDuration(CacheValidDuration.FOREVER)

        onResponse {
            println("onResponse > 是否是缓存：${it.isFromCache} ${it.body?.contentType()}")
        }
        onError {
            println("请求失败： ${it.stackTraceToString()}")
        }
        onSuccess {
            println("请求成功： \n${it.toString(2)}")
        }
    }
}