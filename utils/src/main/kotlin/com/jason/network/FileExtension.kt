package com.jason.network

import java.io.File

fun File.verifyMD5(
    md5: String,
    onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)? = null
): Boolean {
    val result = inputStream().use { it.sumMd5(onVerifyFile) }
    println("MD5: $result")
    return result.lowercase() == md5.lowercase()
}

fun File.verifySHA1(
    sha1: String,
    onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)? = null
): Boolean {
    val result = inputStream().use { it.sumSha1(onVerifyFile) }
    println("SHA1: $result")
    return result.lowercase() == sha1.lowercase()
}

fun File.verifyShA256(
    sha256: String,
    onVerifyFile: ((percent: Float, totalCopied: Long, totalSize: Long) -> Unit)? = null
): Boolean {
    val result = inputStream().use { it.sumSha256(onVerifyFile) }
    println("SHA256: $result")
    return result.lowercase() == sha256.lowercase()
}