package com.jason.selector

import com.jason.network.CacheMode
import com.jason.network.OkHttpClientUtil
import java.io.File

fun main() {
    OkHttpClientUtil.setCacheDir(File("D:/OKHttpCache"))


    execute()

}

fun execute() {
    val result = OkHttpClientUtil.execute {
        url("https://blog.51cto.com/u_15898516/5901614")
        setCacheMode(CacheMode.ONLY_NETWORK)
        setBaseRequest {
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
            )
        }
    }


    println("请求成功： $result")
}

fun enqueue() {
    OkHttpClientUtil.enqueue(config = {
        url("https://blog.51cto.com/u_15898516/5901614")
        setCacheMode(CacheMode.ONLY_CACHE)
    }, onError = {
        println("请求失败： ${it.message}")
    }, onSucceed = {
        println("请求成功： $it")
    })
}