package app.himaya

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDate
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class DnsVpnService : VpnService() {
    companion object {
        const val STOP = "app.himaya.STOP"
        @Volatile var isRunning = false
        @Volatile var startedAt = 0L
        @Volatile var lastError: String? = null
        private const val TLS_HOST = "family.cloudflare-dns.com"
    }
    private class Session(val fd: ParcelFileDescriptor) {
        val workers = ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(64), ThreadPoolExecutor.DiscardPolicy())
        private val sockets = mutableSetOf<Socket>()
        val pendingBlocks = AtomicInteger(0)
        val notificationPending = AtomicBoolean(false)
        @Volatile var active = true
        @Synchronized fun register(socket: Socket): Boolean {
            if (!active) { socket.close(); return false }
            sockets += socket
            return true
        }
        @Synchronized fun remove(socket: Socket) { sockets -= socket }
        @Synchronized fun close() {
            active = false
            try { fd.close() } catch (_: Exception) { }
            workers.shutdownNow()
            sockets.forEach { try { it.close() } catch (_: Exception) { } }
            sockets.clear()
        }
    }
    @Volatile private var session: Session? = null
    private val handler = Handler(Looper.getMainLooper())
    private val overlay by lazy { BlockOverlay(applicationContext) }
    private var lastOverlay = -4000L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { shutdown(); stopSelf(); return START_NOT_STICKY }
        if (session != null) return START_STICKY
        try {
            val fd = Builder().setSession("Himaya · DNS-фильтр")
                .addAddress("10.111.222.1", 32).addDnsServer("10.111.222.2")
                .addRoute("10.111.222.2", 32).setMtu(32767).setBlocking(true)
                // Non-DNS IPv6 is not tunnelled or filtered, like non-DNS IPv4.
                .allowFamily(android.system.OsConstants.AF_INET6).establish()
                ?: throw IllegalStateException("VPN permission unavailable")
            val current = Session(fd)
            session = current
            foreground()
            lastError = null
            startedAt = SystemClock.elapsedRealtime()
            isRunning = true
            Thread({ loop(current) }, "Himaya-DNS").start()
        } catch (_: Exception) {
            shutdown()
            lastError = "Не удалось включить VPN. Проверь разрешение и настройки VPN."
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("protection", "DNS-фильтр", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, DnsVpnService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "protection")
            .setSmallIcon(R.drawable.ic_shield).setContentTitle("Himaya · фильтр включён")
            .setContentText("Системный DNS фильтруется. Нажми, чтобы открыть настройки.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Выключить", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(1, notification)
    }

    override fun onRevoke() { shutdown(); stopSelf(); super.onRevoke() }
    override fun onDestroy() { shutdown(); super.onDestroy() }
    private fun shutdown() {
        isRunning = false
        startedAt = 0L
        session?.close()
        session = null
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun loop(current: Session) {
        val input = FileInputStream(current.fd.fileDescriptor)
        val output = FileOutputStream(current.fd.fileDescriptor)
        val buffer = ByteArray(32767)
        try {
            while (current.active) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n !in 45..(60 + 8 + DnsMessage.MAX_QUERY_BYTES)) continue
                val packet = buffer.copyOf(n)
                current.workers.execute { handle(current, packet, output) }
            }
        } catch (_: Exception) { }
        finally {
            handler.post {
                if (session === current) {
                    shutdown()
                    lastError = "VPN остановлен. Включи фильтр снова."
                    stopSelf()
                }
            }
        }
    }

    private fun handle(current: Session, p: ByteArray, output: FileOutputStream) {
        try {
            if (!current.active || p.size < 40 || (p[0].toInt() and 0xF0) != 0x40) return
            val ihl = (p[0].toInt() and 15) * 4
            if (ihl < 20 || p.size < ihl + 20 || p[9] != 17.toByte()) return
            if (DnsMessage.u16(p, 6) and 0x3FFF != 0) return // No IP fragments.
            if (DnsMessage.u16(p, 2) != p.size || DnsMessage.u16(p, ihl + 4) != p.size - ihl) return
            if (DnsMessage.u16(p, ihl + 2) != 53 || !p.copyOfRange(16, 20).contentEquals(byteArrayOf(10,111,222.toByte(),2))) return
            val q = p.copyOfRange(ihl + 8, p.size)
            val question = DnsMessage.question(q) ?: return
            val upstream = DnsMessage.upstreamQuery(q, question) ?: return
            val reply = when {
                question.host in Blocklist.silent -> DnsMessage.error(q, 3)
                (Blocklist.isBlocked(question.host) || DomainRules.matches(question.host, getSharedPreferences("himaya", MODE_PRIVATE).getStringSet("custom_domains", emptySet())?.toSet() ?: emptySet())) -> { notifyBlocked(current); DnsMessage.error(q, 3) }
                else -> forward(current, upstream)?.also { if (DnsMessage.isBlockedResponse(it)) notifyBlocked(current) }
                    ?: DnsMessage.error(q, 2)
            } ?: return
            if (!current.active) return
            synchronized(output) { if (current.active) output.write(buildPacket(p, ihl, reply)) }
        } catch (_: Exception) { /* Drop malformed packets without recording domains. */ }
    }

    private fun forward(current: Session, q: ByteArray): ByteArray? {
        for (ip in listOf("1.1.1.3", "1.0.0.3")) {
            if (!current.active) return null
            val raw = Socket()
            try {
                if (!current.register(raw)) return null
                raw.use {
                    if (!protect(raw)) return null
                    raw.soTimeout = 3000
                    raw.connect(InetSocketAddress(ip, 853), 3000)
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    (factory.createSocket(raw, TLS_HOST, 853, true) as SSLSocket).use { tls ->
                        tls.soTimeout = 3000
                        tls.sslParameters = tls.sslParameters.apply {
                            endpointIdentificationAlgorithm = "HTTPS"
                            serverNames = listOf(SNIHostName(TLS_HOST))
                        }
                        tls.startHandshake()
                        DataOutputStream(tls.outputStream).apply { writeShort(q.size); write(q); flush() }
                        val input = DataInputStream(tls.inputStream)
                        val size = input.readUnsignedShort()
                        if (size !in 12..32739) return null
                        val response = ByteArray(size)
                        input.readFully(response)
                        if (DnsMessage.matches(q, response)) {
                            if (session === current) lastError = null
                            return response
                        }
                    }
                }
            } catch (_: Exception) { /* Never fall back to plaintext DNS. */ }
            finally { current.remove(raw); try { raw.close() } catch (_: Exception) { } }
        }
        if (session === current) lastError = "DNS-сервер недоступен. Запросы получают ошибку; проверь интернет."
        return null
    }

    private fun notifyBlocked(current: Session) {
        current.pendingBlocks.updateAndGet { if (it < Int.MAX_VALUE) it + 1 else it }
        if (!current.notificationPending.compareAndSet(false, true)) return
        handler.post {
            current.notificationPending.set(false)
            val increment = current.pendingBlocks.getAndSet(0)
            if (session !== current || !current.active) return@post
            val prefs = getSharedPreferences("himaya", MODE_PRIVATE)
            val count = prefs.getInt("count", 0)
            val today = LocalDate.now()
            val key = "day_$today"
            val edit = prefs.edit().putInt("count", (count.toLong() + increment).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .putInt(key, (prefs.getInt(key, 0).toLong() + increment).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            prefs.all.keys.filter { it.startsWith("day_") && it < "day_${today.minusDays(29)}" }.forEach { edit.remove(it) }
            edit.apply()
            val now = SystemClock.elapsedRealtime()
            if (now - lastOverlay >= 4000 && prefs.getBoolean("reminders", false) && Settings.canDrawOverlays(this)) {
                lastOverlay = now
                overlay.show()
            }
        }
    }

    private fun buildPacket(req: ByteArray, ihl: Int, payload: ByteArray): ByteArray {
        val o = ByteArray(28 + payload.size)
        o[0] = 0x45; o[2] = (o.size shr 8).toByte(); o[3] = o.size.toByte(); o[8] = 64; o[9] = 17
        System.arraycopy(req, 16, o, 12, 4); System.arraycopy(req, 12, o, 16, 4)
        var sum = 0
        for (k in 0 until 20 step 2) sum += DnsMessage.u16(o, k)
        while (sum shr 16 != 0) sum = (sum and 65535) + (sum shr 16)
        val checksum = sum.inv() and 65535
        o[10] = (checksum shr 8).toByte(); o[11] = checksum.toByte()
        System.arraycopy(req, ihl + 2, o, 20, 2); System.arraycopy(req, ihl, o, 22, 2)
        val udpLength = 8 + payload.size
        o[24] = (udpLength shr 8).toByte(); o[25] = udpLength.toByte()
        System.arraycopy(payload, 0, o, 28, payload.size)
        return o
    }
}
