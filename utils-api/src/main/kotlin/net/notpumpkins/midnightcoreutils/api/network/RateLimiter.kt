package net.notpumpkins.midnightcoreutils.api.network

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

class RateLimiter(
    private val maxBurst: Int = 100,
    private val windowMs: Long = 1000L,
    private val maxQueueDepth: Int = 500
) {
    private val timestamps = ConcurrentLinkedDeque<Long>()
    private val queueDepth = AtomicInteger(0)

    fun tryConsume(): Boolean {
        if (queueDepth.get() >= maxQueueDepth) {
            return false
        }
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs

        while (timestamps.peekFirst() != null && timestamps.first < cutoff) {
            timestamps.pollFirst()
        }

        if (timestamps.size >= maxBurst) {
            return false
        }

        timestamps.addLast(now)
        queueDepth.incrementAndGet()
        return true
    }

    fun release() {
        queueDepth.decrementAndGet()
    }

    fun getCurrentLoad(): Int = queueDepth.get()

    fun getWindowUsage(): Int = timestamps.size

    fun reset() {
        timestamps.clear()
        queueDepth.set(0)
    }
}
