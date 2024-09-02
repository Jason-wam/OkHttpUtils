package com.jason.selector

import com.jason.network.cache.CacheMode
import com.jason.network.OkHttpClientUtil
import com.jason.network.UrlBuilder
import com.jason.network.cache.CacheValidDuration
import com.jason.network.isFromCache
import okhttp3.Response
import org.json.JSONObject
import java.io.File

fun main() {
    OkHttpClientUtil.setCacheDir(File("D:/OKHttpCache"))

    enqueue()

}

fun enqueue() {
    OkHttpClientUtil.execute<Response> {
        url("http://192.168.0.8:2333/BitComet.7z")
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

fun download() {
    //异步请求
    OkHttpClientUtil.downloadAsync {
        //shasum -a 256 = 8762f7e74e4d64d72fceb5f70682e6b069932deedb4949c6975d0f0fe0a91be3
        url("https://releases.ubuntu.com/24.04/ubuntu-24.04-live-server-amd64.iso")
        setSaveDirectory(File("D:/"))
        setFileName("ubuntu-24.04-live-server-amd64.iso")
        enableRangeDownload(true)
        setSHA256("8762f7e74e4d64d72fceb5f70682e6b069932deedb4949c6975d0f0fe0a91be3")

        onVerifyFile { percent, totalCopied, totalSize ->
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

        onProgress { percent,downloadBytes,totalBytes ->
            print("\r下载进度： $percent , $downloadBytes/$totalBytes")
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
        param("from", "Android")
        path("x/click-interface/click/now")
        setCacheMode(CacheMode.ONLY_NETWORK)
        onResponse {
            println("onResponse > 是否是缓存：${it.isFromCache} ${it.body?.contentType()}")
            //此处不得然使用it.body?.string()，因为此时消费了it.body会导致onSuccess失败
            //println("onResponse > 是否是缓存：${it.isFromCache}, string = ${it.body?.string()}")
        }
        onError {
            println("请求失败： ${it.stackTraceToString()}")
        }
        onSuccess {
            println("请求成功： \n${it.toString(2)}")
        }
    }
}