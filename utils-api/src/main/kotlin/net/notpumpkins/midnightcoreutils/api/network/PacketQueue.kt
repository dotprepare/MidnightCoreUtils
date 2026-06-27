package net.notpumpkins.midnightcoreutils.api.network

import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class PacketQueue(
    private val maxRetries: Int = 3,
    private val rateLimiter: RateLimiter = RateLimiter()
) {
    data class QueuedPacket(
        val payload: NbtPayload,
        val target: String,
        var retriesRemaining: Int,
        val isServerBound: Boolean
    )

    private val queue = ConcurrentLinkedQueue<QueuedPacket>()
    private val failureCount = AtomicInteger(0)
    private val sendCount = AtomicInteger(0)

    fun enqueue(payload: NbtPayload, target: String, isServerBound: Boolean): Boolean {
        if (!rateLimiter.tryConsume()) {
            return false
        }
        val packet = QueuedPacket(
            payload = payload.copy(),
            target = target,
            retriesRemaining = maxRetries,
            isServerBound = isServerBound
        )
        queue.add(packet)
        return true
    }

    fun dequeue(): QueuedPacket? = queue.poll()

    fun retry(packet: QueuedPacket) {
        if (packet.retriesRemaining > 0) {
            packet.retriesRemaining--
            queue.add(packet)
        } else {
            failureCount.incrementAndGet()
        }
    }

    fun markSent() {
        sendCount.incrementAndGet()
        rateLimiter.release()
    }

    fun markFailed() {
        failureCount.incrementAndGet()
        rateLimiter.release()
    }

    fun getQueueDepth(): Int = queue.size
    fun getFailureCount(): Int = failureCount.get()
    fun getSendCount(): Int = sendCount.get()

    fun clear() {
        queue.clear()
        failureCount.set(0)
        sendCount.set(0)
        rateLimiter.reset()
    }
}
