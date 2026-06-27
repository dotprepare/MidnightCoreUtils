package net.notpumpkins.midnightcoreutils.api.network

import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap

class PacketSync(
    private val maxRetries: Int = 3,
    private val maxBurst: Int = 100,
    private val windowMs: Long = 1000L,
    private val maxQueueDepth: Int = 500
) {
    val rateLimiter = RateLimiter(maxBurst, windowMs, maxQueueDepth)
    private val queues = concurrentHashMap<String, PacketQueue>()
    private var sendHandler: ((NbtPayload, String, Boolean) -> Boolean)? = null
    private var receiveHandler: ((NbtPayload, String, Boolean) -> Unit)? = null

    fun setSendHandler(handler: (NbtPayload, String, Boolean) -> Boolean) {
        sendHandler = handler
    }

    fun setReceiveHandler(handler: (NbtPayload, String, Boolean) -> Unit) {
        receiveHandler = handler
    }

    fun sendToServer(payload: NbtPayload, targetModId: String): Boolean {
        val queue = queues.getOrPut(targetModId) {
            PacketQueue(maxRetries, rateLimiter)
        }
        return queue.enqueue(payload, targetModId, isServerBound = true)
    }

    fun sendToClient(payload: NbtPayload, targetModId: String, playerId: String): Boolean {
        val queueKey = "$targetModId:$playerId"
        val queue = queues.getOrPut(queueKey) {
            PacketQueue(maxRetries, rateLimiter)
        }
        return queue.enqueue(payload, "$targetModId:$playerId", isServerBound = false)
    }

    fun processOutgoing(): Int {
        var sent = 0
        val iter = queues.entries.iterator()
        while (iter.hasNext()) {
            val (key, queue) = iter.next()
            while (true) {
                val packet = queue.dequeue() ?: break
                val sendResult = sendHandler?.invoke(packet.payload, packet.target, packet.isServerBound)

                if (sendResult == true) {
                    queue.markSent()
                    sent++
                } else {
                    queue.retry(packet)
                    break
                }
            }
        }
        return sent
    }

    fun handleIncoming(payload: NbtPayload, sourceModId: String, isServerBound: Boolean) {
        receiveHandler?.invoke(payload, sourceModId, isServerBound)
    }

    fun getQueueDepth(targetModId: String): Int {
        return queues[targetModId]?.getQueueDepth() ?: 0
    }

    fun getTotalQueueDepth(): Int {
        return queues.values.sumOf { it.getQueueDepth() }
    }

    fun getTotalFailureCount(): Int {
        return queues.values.sumOf { it.getFailureCount() }
    }

    fun getTotalSendCount(): Int {
        return queues.values.sumOf { it.getSendCount() }
    }

    fun clearAll() {
        queues.values.forEach { it.clear() }
        queues.clear()
        rateLimiter.reset()
    }

    fun clearQueue(targetModId: String) {
        queues.remove(targetModId)
    }

    fun clearPlayerQueue(targetModId: String, playerId: String) {
        queues.remove("$targetModId:$playerId")
    }
}
