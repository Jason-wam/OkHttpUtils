package com.jason.network

import com.jakewharton.disklrucache.DiskLruCache
import java.io.File

object OkHttpCacheStore {
    private var cache: DiskLruCache? = null

    const val CACHE_NEVER = 0L
    const val CACHE_FOREVER = -1L

    internal fun init(dir: File) {
        try {
            cache = DiskLruCache.open(dir, 1, 3, 1024 * 1024 * 1024 * 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 添加缓存数据
     */
    fun put(key: String, body: String, validDuration: Long = CACHE_FOREVER) {
        if (validDuration > CACHE_NEVER || validDuration == CACHE_FOREVER) {
            cache ?: return
            try {
                cache?.remove(key) //覆盖数据
                val editor = cache!!.edit(key)
                editor[0] = body
                editor[1] = validDuration.toString()
                editor[2] = System.currentTimeMillis().toString()
                editor.commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 获取缓存数据
     */
    fun get(key: String, skipValidDuration: Boolean = false): String? {
        try {
            return cache?.get(key).use { snapshot ->
                val body = snapshot?.getString(0)
                val validDuration = snapshot?.getString(1)?.toLongOrNull() ?: 0
                if (validDuration == CACHE_FOREVER) return body

                val timestamp = snapshot?.getString(2)?.toLongOrNull() ?: 0
                if (System.currentTimeMillis() - timestamp <= validDuration || skipValidDuration) {
                    body
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 删除缓存
     */
    fun delete(key: String) {
        try {
            cache ?: return
            cache!!.remove(key)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            cache?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clear() {
        try {
            cache?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}