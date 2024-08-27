package com.jason.network.converter

interface DataDecoder {
    fun convert(response: String): String
}