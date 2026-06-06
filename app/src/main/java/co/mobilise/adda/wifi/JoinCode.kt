package co.mobilise.adda.wifi

/**
 * Packs an IPv4 address into a short, human-typable code and back.
 *
 * 192.168.49.1  <->  e.g. "1HX8M5" (32-bit IP -> base36, max 7 chars).
 * Works on any network (Wi-Fi Direct group-owner or plain hotspot), so the
 * "enter code" join path (Step 6) needs no central directory.
 */
object JoinCode {

    fun encode(ip: String): String {
        val octets = ip.trim().split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return ""
        var v = 0L
        octets.forEach { v = (v shl 8) or it.toLong() }
        return v.toString(36).uppercase().padStart(7, '0')
    }

    fun decode(code: String): String? {
        val clean = code.filter { it.isLetterOrDigit() }.lowercase()
        if (clean.isEmpty()) return null
        val v = clean.toLongOrNull(36) ?: return null
        if (v < 0 || v > 0xFFFFFFFFL) return null
        val a = (v shr 24) and 0xFF
        val b = (v shr 16) and 0xFF
        val c = (v shr 8) and 0xFF
        val d = v and 0xFF
        return "$a.$b.$c.$d"
    }
}
