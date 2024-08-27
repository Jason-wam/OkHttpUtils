package com.jason.network

object OkhttpLogger {
    var enabled = true

    fun d(tag: String, msg: String) {
        if (enabled) {
            println("$tag: $msg")
        }
    }

    fun e(tag: String, msg: String) {
        if (enabled) {
            System.err.println("$tag: $msg")
        }
    }

    fun i(tag: String, msg: String) {
        if (enabled) {
            println("$tag: $msg")
        }
    }

    fun w(tag: String, msg: String) {
        if (enabled) {
            println("$tag: $msg")
        }
    }

    fun v(tag: String, msg: String) {
        if (enabled) {
            println("$tag: $msg")
        }
    }
}