package app.himaya

import java.net.IDN
import java.net.URI
import java.util.Locale

internal object DomainRules {
    const val LIMIT = 100
    fun normalize(input: String): String? {
        return try {
            val raw = input.trim()
            if (raw.length > 2048 || raw.any { it.isWhitespace() } || raw.contains('@')) return null
            val host = if (raw.contains("://")) {
                val uri = URI(raw)
                if (uri.scheme.lowercase(Locale.ROOT) !in listOf("http", "https") || uri.port != -1 || uri.rawUserInfo != null) return null
                uri.host ?: uri.rawAuthority ?: return null
            } else raw
            val ascii = IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            val parts = ascii.split('.')
            if (ascii.length !in 3..253 || parts.size < 2 || parts.all { it.all(Char::isDigit) } ||
                parts.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') || !it.all { c -> c in 'a'..'z' || c in '0'..'9' || c == '-' } }) null else ascii
        } catch (_: Exception) { null }
    }
    fun matches(host: String, domains: Set<String>): Boolean = domains.any { host == it || host.endsWith(".$it") }
}
