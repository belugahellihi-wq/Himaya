package app.himaya

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DnsMessageTest {
    private fun query(host: String = "example.com"): ByteArray {
        val bytes = mutableListOf<Byte>(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0)
        host.split('.').forEach { label -> bytes += label.length.toByte(); bytes += label.toByteArray().toList() }
        bytes += listOf<Byte>(0, 0, 1, 0, 1)
        return bytes.toByteArray()
    }
    private fun answer(): ByteArray {
        val header = query().apply { this[2] = 0x81.toByte(); this[3] = 0x80.toByte(); this[7] = 1 }
        return header + byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 0, 0, 0, 0)
    }
    @Test fun acceptsOrdinaryQueriesAndCaseInsensitiveQuestions() {
        assertEquals("example.com", DnsMessage.question(query("EXAMPLE.COM"))?.host)
        assertTrue(DnsMessage.matches(query("EXAMPLE.COM"), answer()))
    }
    @Test fun rejectsEveryTruncatedQuestion() {
        val q = query()
        for (size in 0 until q.size) assertNull("size=$size", DnsMessage.question(q.copyOf(size)))
    }
    @Test fun rejectsInvalidHeadersNamesAndClasses() {
        assertNull(DnsMessage.question(query().apply { this[5] = 2 }))
        assertNull(DnsMessage.question(query().apply { this[2] = 0x81.toByte() }))
        assertNull(DnsMessage.question(query().apply { this[12] = 64 }))
        assertNull(DnsMessage.question(query().apply { this[12] = 0xC0.toByte() }))
        assertNull(DnsMessage.question(query().apply { this[lastIndex] = 3 }))
        assertNull(DnsMessage.question(query("a".repeat(64) + ".com")))
        assertNull(DnsMessage.question(query(List(5) { "a".repeat(63) }.joinToString("."))))
    }
    @Test fun errorReplyPreservesIdAndQuestionButDropsAdditionalData() {
        val q = query()
        val error = DnsMessage.error(q, 3)!!
        assertEquals(3, error[3].toInt() and 15)
        assertTrue(DnsMessage.matches(q, error))
        assertEquals(0, DnsMessage.u16(error, 6))
        assertEquals(2, DnsMessage.error(q, 2)!![3].toInt() and 15)
    }
    @Test fun rejectsMismatchedAndMalformedResponses() {
        val q = query()
        assertFalse(DnsMessage.matches(q, answer().apply { this[1] = 0 }))
        assertFalse(DnsMessage.matches(q, answer().apply { this[13] = 'z'.code.toByte() }))
        assertFalse(DnsMessage.matches(q, answer().apply { this[7] = 127 }))
        assertFalse(DnsMessage.matches(q, answer().dropLast(1).toByteArray()))
        assertFalse(DnsMessage.matches(q, answer() + byteArrayOf(1)))
        assertFalse(DnsMessage.matches(q, answer().apply { this[q.size + 1] = 0xFF.toByte() }))
    }
    @Test fun recognizesZeroAddressButNotNormalAddress() {
        assertTrue(DnsMessage.isBlockedResponse(answer()))
        assertFalse(DnsMessage.isBlockedResponse(answer().apply { this[lastIndex] = 1 }))
        assertFalse(DnsMessage.isBlockedResponse(DnsMessage.error(query(), 3)!!))
    }
    @Test fun hostileInputsNeverThrow() {
        val random = Random(42)
        repeat(10000) {
            val bytes = random.nextBytes(random.nextInt(0, 1024))
            DnsMessage.question(bytes)
            DnsMessage.isBlockedResponse(bytes)
            DnsMessage.matches(query(), bytes)
        }
        val r = answer()
        for (i in r.indices) {
            for (value in listOf(0, 63, 64, 127, 192, 255)) {
                val mutated = r.copyOf().apply { this[i] = value.toByte() }
                DnsMessage.matches(query(), mutated)
                DnsMessage.isBlockedResponse(mutated)
            }
        }
    }
}
