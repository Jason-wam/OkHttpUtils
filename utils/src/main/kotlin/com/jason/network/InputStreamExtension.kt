package com.jason.network

import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt


fun InputStream.copyWithProgress(
    outStream: OutputStream, progress: (progress: Int, copied: Long, total: Long) -> Unit
) {
    copyWithProgress(contentLength = -1,outStream, progress)
}

/**
 * 将当前输入流复制到指定的输出流，并提供进度回调。
 *
 * 此函数的主要目的是在将数据从一个流复制到另一个流时，提供进度更新的功能。
 * 它可以用于诸如文件传输或备份的场景，让用户了解操作的进度。
 *
 * @param outStream 要将数据复制到的输出流。
 * @param progress 一个接收进度更新的回调函数，进度以0到100的整数表示。
 * @return 返回已复制的字节数。
 */
fun InputStream.copyWithProgress(
    contentLength: Long = -1, outStream: OutputStream, progress: (progress: Int, copied: Long, total: Long) -> Unit
): Long {
    var bytesCopied: Long = 0
    val totalBytes = if (this is FileInputStream) {
        channel.size()
    } else {
        if (contentLength > 0) contentLength else available().toLong()
    }

    println("size: $totalBytes")
    val buffer = ByteArray(4096)
    var bytes = read(buffer)
    var progressPercent = 0
    while (bytes >= 0) {
        outStream.write(buffer, 0, bytes)
        bytesCopied += bytes
        val newProgressPercent = (bytesCopied / totalBytes.toFloat() * 100).roundToInt()
        if (progressPercent != newProgressPercent) {
            progressPercent = newProgressPercent
            progress((bytesCopied / totalBytes.toFloat() * 100).roundToInt(), bytesCopied, totalBytes)
        }
        bytes = read(buffer)
    }
    return bytesCopied
}