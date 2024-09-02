package com.jason.network

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.CopyOnWriteArrayList

internal object CallManager {
    private val calls by lazy { CopyOnWriteArrayList<Call>() }

    fun cancel(call: Call) {
        call.cancel()
        calls.remove(call)
    }

    fun cancelByTag(tag: Any) {
        val iterator = calls.iterator()
        while (iterator.hasNext()) {
            val currentCall = iterator.next()
            if (currentCall.request().tag() == tag) {
                currentCall.cancel()
                iterator.remove()
            }
        }
    }

    fun cancelAll() {
        calls.forEach { it.cancel() }
        calls.clear()
    }

    fun bind(client: OkHttpClient.Builder) {
        client.eventListener(object : EventListener() {
            override fun callStart(call: Call) {
                super.callStart(call)
                calls.add(call)
                OkhttpLogger.i("OkHttpClient", "callStart: ${call.request()}")
            }

            override fun callFailed(call: Call, ioe: IOException) {
                super.callFailed(call, ioe)
                calls.remove(call)
                OkhttpLogger.e("OkHttpClient", "callFailed: ${call.request()} , reason: $ioe")
            }

            override fun callEnd(call: Call) {
                super.callEnd(call)
                calls.remove(call)
                OkhttpLogger.i("OkHttpClient", "callEnd: ${call.request()}")
            }

            override fun canceled(call: Call) {
                super.canceled(call)
                calls.remove(call)
                OkhttpLogger.i("OkHttpClient", "canceled: ${call.request()}")
            }

            override fun connectFailed(
                call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException
            ) {
                super.connectFailed(call, inetSocketAddress, proxy, protocol, ioe)
                calls.remove(call)
                OkhttpLogger.e("OkHttpClient", "connectFailed: ${call.request()} , reason: $ioe")
            }

            override fun requestFailed(call: Call, ioe: IOException) {
                super.requestFailed(call, ioe)
                calls.remove(call)
                OkhttpLogger.e("OkHttpClient", "requestFailed: ${call.request()} , reason: $ioe")
            }

            override fun responseFailed(call: Call, ioe: IOException) {
                super.responseFailed(call, ioe)
                calls.remove(call)
                OkhttpLogger.e("OkHttpClient", "responseFailed: ${call.request()} , reason: $ioe")
            }
        })
    }
}