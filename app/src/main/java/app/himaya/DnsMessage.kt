package app.himaya

import java.util.Locale

/** Bounded DNS parsing. Unsupported/malformed packets are never forwarded. */
internal object DnsMessage {
    const val MAX_QUERY_BYTES = 4096
    data class Question(val host: String, val end: Int)
    fun u16(b: ByteArray, i: Int): Int = ((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)

    fun question(q: ByteArray, response: Boolean = false): Question? {
        if (q.size < 17 || q.size > 32739 || u16(q, 4) != 1) return null
        if ((q[2].toInt() and 0xF8) != if (response) 0x80 else 0) return null
        if (!response && (u16(q, 6) != 0 || u16(q, 8) != 0)) return null
        var i = 12
        val labels = mutableListOf<String>()
        while (i < q.size) {
            val size = q[i++].toInt() and 255
            if (size == 0) {
                if (i + 4 > q.size || i - 12 > 255 || u16(q, i + 2) != 1) return null
                return Question(labels.joinToString(".").lowercase(Locale.ROOT), i + 4)
            }
            if (size > 63 || i + size > q.size || i + size - 12 > 254) return null
            // Hostname-oriented filter; reject ambiguous bytes rather than rewriting them.
            if ((i until i + size).any { (q[it].toInt() and 255) !in 33..126 || q[it] == '.'.code.toByte() }) return null
            labels += String(q, i, size, Charsets.US_ASCII)
            i += size
        }
        return null
    }

    fun error(q: ByteArray, code: Int): ByteArray? {
        val question = question(q) ?: return null
        return q.copyOf(question.end).apply {
            this[2] = (0x80 or (q[2].toInt() and 1)).toByte()
            this[3] = (0x80 or code).toByte()
            for (i in 6..11) this[i] = 0
        }
    }

    /** Accept a normal question with at most one EDNS OPT. Keep DO and the UDP
     * size, but don't forward client options such as subnet, cookies or padding. */
    fun upstreamQuery(q: ByteArray, question: Question): ByteArray? {
        if (q.size > MAX_QUERY_BYTES) return null
        val offset = question.end
        val additional = u16(q, 10)
        if (additional == 0) return q.takeIf { it.size == offset }
        if (additional != 1 || q.size - offset < 11 || q[offset] != 0.toByte() ||
            u16(q, offset + 1) != 41 || u16(q, offset + 5) != 0
        ) return null

        val options = offset + 11
        if (u16(q, offset + 9) != q.size - options) return null
        var cursor = options
        while (cursor < q.size) {
            if (q.size - cursor < 4) return null
            val length = u16(q, cursor + 2)
            cursor += 4
            if (length > q.size - cursor) return null
            cursor += length
        }
        return q.copyOf(options).apply {
            this[offset + 7] = (q[offset + 7].toInt() and 0x80).toByte()
            this[offset + 8] = 0
            this[offset + 9] = 0
            this[offset + 10] = 0
        }
    }

    fun matches(q: ByteArray, r: ByteArray): Boolean {
        val a = question(q) ?: return false
        val b = question(r, true) ?: return false
        return q[0] == r[0] && q[1] == r[1] && a.host == b.host &&
            u16(q, a.end - 4) == u16(r, b.end - 4) && validRecords(r, b.end)
    }

    private fun skipName(b: ByteArray, start: Int): Int? {
        var i = start
        while (i < b.size && i - start < 255) {
            val n = b[i++].toInt() and 255
            if (n == 0) return i
            if (n and 0xC0 == 0xC0) {
                if (i >= b.size) return null
                val pointer = ((n and 63) shl 8) or (b[i].toInt() and 255)
                return if (pointer in 12 until (i - 1)) i + 1 else null
            }
            if (n > 63 || i + n > b.size) return null
            i += n
        }
        return null
    }

    private fun validRecords(r: ByteArray, start: Int): Boolean {
        var i = start
        val records = u16(r, 6) + u16(r, 8) + u16(r, 10)
        if (records > (r.size - start) / 11) return false
        repeat(records) {
            i = skipName(r, i) ?: return false
            if (i + 10 > r.size) return false
            val length = u16(r, i + 8)
            i += 10
            if (length > r.size - i) return false
            i += length
        }
        return i == r.size
    }

    fun isBlockedResponse(r: ByteArray): Boolean {
        val question = question(r, true) ?: return false
        if (r[3].toInt() and 15 != 0 || !validRecords(r, question.end)) return false
        var i = question.end
        repeat(u16(r, 6)) {
            i = skipName(r, i) ?: return false
            val type = u16(r, i)
            val klass = u16(r, i + 2)
            val length = u16(r, i + 8)
            val data = i + 10
            if (klass == 1 && ((type == 1 && length == 4) || (type == 28 && length == 16)) &&
                (data until data + length).all { r[it] == 0.toByte() }) return true
            i = data + length
        }
        return false
    }
}
