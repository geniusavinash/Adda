package co.mobilise.adda.server

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Finds the LAN IPv4 address other devices should hit. Prefers the Wi-Fi Direct
 * group-owner / hotspot interface (Step 5 makes this device the group owner;
 * its address is typically 192.168.49.1). Falls back to any private IPv4.
 */
object NetworkUtils {

    fun deviceIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            interfaces
                .filter { it.isUp && !it.isLoopback }
                .sortedByDescending { rank(it.name) }
                .firstNotNullOfOrNull { nif ->
                    nif.inetAddresses.toList().firstOrNull { addr ->
                        addr is Inet4Address &&
                            !addr.isLoopbackAddress &&
                            isPrivate(addr.hostAddress)
                    }?.hostAddress
                } ?: FALLBACK
        } catch (_: Throwable) {
            FALLBACK
        }
    }

    /** Higher rank = more likely the device's own AP/Direct interface. */
    private fun rank(name: String): Int = when {
        name.startsWith("p2p") -> 4      // Wi-Fi Direct
        name.startsWith("ap") -> 3       // SoftAP / hotspot
        name.startsWith("swlan") -> 3
        name.startsWith("wlan") -> 2     // normal Wi-Fi (STA)
        name.startsWith("eth") -> 1
        else -> 0
    }

    private fun isPrivate(ip: String?): Boolean {
        if (ip == null) return false
        return ip.startsWith("192.168.") ||
            ip.startsWith("10.") ||
            ip.startsWith("172.") // good-enough for 172.16–31 private range in demo nets
    }

    private const val FALLBACK = "192.168.49.1" // Wi-Fi Direct group-owner default
}
