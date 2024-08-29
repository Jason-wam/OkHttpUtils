package com.jason.network

/**
 * 用于在返回数据前对数据进行解码，回调解码后的数据
 */
interface DataDecoder {
    fun convert(response: String): String
}