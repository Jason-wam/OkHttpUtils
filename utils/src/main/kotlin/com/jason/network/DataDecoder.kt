package com.jason.network

interface DataDecoder {
    fun convert(response: String): String
}