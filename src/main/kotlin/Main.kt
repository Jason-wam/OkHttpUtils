package com.jason.selector

import com.jason.network.CacheMode
import com.jason.network.OkHttpClientUtil
import java.io.File

fun main() {
    OkHttpClientUtil.setCacheDir(File("D:/OKHttpCache"))


    download()

}

fun download() {
    OkHttpClientUtil.downloadAsync {
        //shasum -a 256 = 8762f7e74e4d64d72fceb5f70682e6b069932deedb4949c6975d0f0fe0a91be3
        url("https://releases.ubuntu.com/24.04/ubuntu-24.04-live-server-amd64.iso")
        setSaveDirectory(File("D:/"))
        setFileName("ubuntu-24.04-live-server-amd64.iso")
        enableRangeDownload(true)

        //由于是同步请求，所以不会执行此处代码，而是直接抛出异常
        onError {
            println("下载失败： ${it.message}")
        }

        onSucceed {
            println("下载成功： $it")
        }

        onProgress { percent,downloadBytes,totalBytes ->
            print("\r下载进度： $percent , $downloadBytes/$totalBytes")
        }
    }
}

fun execute() {
    val result = OkHttpClientUtil.execute {
        url("https://api.bilibili.com/x/click-interface/click/now")
        setCacheMode(CacheMode.ONLY_NETWORK)
        header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        )
    }

    println("请求成功： $result")
}

fun execute2() {
    OkHttpClientUtil.execute {
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
        onSucceed {
            println("请求成功： $it")
        }
    }
}

fun enqueue() {
    OkHttpClientUtil.enqueue(config = {
        url("https://api.bilibili.com/x/click-interface/click/now")
        setCacheMode(CacheMode.ONLY_NETWORK)
    }, onError = {
        println("请求失败： ${it.message}")
    }, onSucceed = {
        println("请求成功： $it")
    })
}

fun enqueue2() {
    OkHttpClientUtil.enqueue {
        url("https://api.bilibili.com/x/click-interface/click/now")
        setCacheMode(CacheMode.ONLY_NETWORK)
        onError {
            println("请求失败： ${it.message}")
        }
        onSucceed {
            println("请求成功： $it")
        }
    }
}