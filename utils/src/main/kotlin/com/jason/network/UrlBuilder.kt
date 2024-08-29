package com.jason.network

import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder

/**
 * @Author: 进阶的面条
 * @Date: 2022-01-10 23:38
 */
class UrlBuilder(private val baseUrl: String) {
    private var builder = StringBuilder()
    private var charset = "utf-8"

    init {
        builder.append(baseUrl)
    }

    fun charset(charset: String): UrlBuilder {
        this.charset = charset
        return this
    }

    fun path(value: String): UrlBuilder {
        if (builder.endsWith("/")) {
            builder.append(value.removePrefix("/"))
        } else {
            builder.append("/").append(value.removePrefix("/"))
        }
        return this
    }

    fun param(key: String, value: Any): UrlBuilder {
        if (builder.contains("?")) {
            builder.append("&").append(key).append("=").append(value.toString().encode(charset))
        } else {
            builder.append("?").append(key).append("=").append(value.toString().encode(charset))
        }
        return this
    }

    fun abs(second: String): UrlBuilder {
        if (second.startsWith("http")) {
            builder.clear()
            builder.append(second)
        } else {
            try {
                val newURL = URL(URL(baseUrl), second)
                builder.clear()
                builder.append(newURL.toString())
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }
        }
        return this
    }

    private fun String.encode(charset: String): String {
        return URLEncoder.encode(this, charset)
    }

    fun build(): String {
        return builder.toString()
    }
}