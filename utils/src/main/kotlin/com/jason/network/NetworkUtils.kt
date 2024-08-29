package com.jason.network

import java.net.NetworkInterface
import java.util.regex.Pattern

object NetworkUtils {

    /**
     * 获取本机局域网IP
     * 排除环回地址
     */
    fun getLocalIPAddress(): String? {
        try {
            val enumeration = NetworkInterface.getNetworkInterfaces()
            if (enumeration != null) {
                while (enumeration.hasMoreElements()) {
                    val element = enumeration.nextElement()
                    val addresses = element.inetAddresses
                    if (addresses != null) {
                        while (addresses.hasMoreElements()) {
                            val address = addresses.nextElement()
                            if (!address.isLoopbackAddress && isIPv4Address(address.hostAddress)) {
                                return address.hostAddress
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 获取本机局域网IP
     * 包含环回地址
     */
    fun getLocalIPAddresses(): List<String> {
        return ArrayList<String>().apply {
            try {
                val enumeration = NetworkInterface.getNetworkInterfaces()
                if (enumeration != null) {
                    while (enumeration.hasMoreElements()) {
                        val element = enumeration.nextElement()
                        val addresses = element.inetAddresses
                        if (addresses != null) {
                            while (addresses.hasMoreElements()) {
                                val address = addresses.nextElement()
                                if (!address.isLoopbackAddress && isIPv4Address(address.hostAddress)) {
                                    address.hostAddress?.let { add(it) }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Check if valid IPV4 address.
     *
     * @param input the address string to check for validity.
     * @return True if the input parameter is a valid IPv4 address.
     */
    private fun isIPv4Address(input: String?): Boolean {
        return IPV4_PATTERN.matcher(input.orEmpty()).matches()
    }

    /**
     * Ipv4 address check.
     */
    private val IPV4_PATTERN = Pattern.compile(
        "^(" + "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" + "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
    )
}