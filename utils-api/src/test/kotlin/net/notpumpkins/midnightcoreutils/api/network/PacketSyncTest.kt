package net.notpumpkins.midnightcoreutils.api.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class PacketSyncTest {

    private lateinit var packetSync: PacketSync

    @BeforeEach
    fun setUp() {
        packetSync = PacketSync(maxRetries = 3, maxBurst = 1000, windowMs = 10000, maxQueueDepth = 500)
    }

    @Test
    fun `send to server enqueues packet`() {
        assertTrue(packetSync.sendToServer(SimpleNbtPayload().apply { putString("msg", "hello") }, "targetMod"))
    }

    @Test
    fun `send to client enqueues packet`() {
        assertTrue(packetSync.sendToClient(SimpleNbtPayload().apply { putString("msg", "hello") }, "targetMod", "player1"))
    }

    @Test
    fun `process outgoing calls send handler`() {
        val sentPayloads = mutableListOf<NbtPayload>()
        packetSync.setSendHandler { payload, _, _ -> sentPayloads.add(payload); true }
        packetSync.sendToServer(SimpleNbtPayload().apply { putString("msg", "hello") }, "targetMod")
        packetSync.processOutgoing()
        assertEquals(1, sentPayloads.size)
        assertEquals("hello", sentPayloads[0].getString("msg"))
    }

    @Test
    fun `send handler returning false triggers retry`() {
        val sendAttempts = AtomicInteger(0)
        packetSync.setSendHandler { _, _, _ -> sendAttempts.incrementAndGet(); false }
        packetSync.sendToServer(SimpleNbtPayload().apply { putString("msg", "hello") }, "targetMod")
        packetSync.processOutgoing(); assertEquals(1, sendAttempts.get())
        packetSync.processOutgoing(); assertEquals(2, sendAttempts.get())
    }

    @Test
    fun `receive handler called on incoming packet`() {
        val received = mutableListOf<Pair<NbtPayload, String>>()
        packetSync.setReceiveHandler { payload, source, _ -> received.add(Pair(payload, source)) }
        packetSync.handleIncoming(SimpleNbtPayload().apply { putString("msg", "hello") }, "sourceMod", true)
        assertEquals(1, received.size)
        assertEquals("sourceMod", received[0].second)
    }

    @Test
    fun `rate limiter prevents overflow`() {
        val strictLimiter = RateLimiter(maxBurst = 5, windowMs = 1000, maxQueueDepth = 10)
        for (i in 0 until 5) { assertTrue(strictLimiter.tryConsume(), "Burst $i should succeed") }
        assertFalse(strictLimiter.tryConsume(), "6th packet should be rate limited")
    }

    @Test
    fun `packet queue enqueue limit`() {
        val pktSync = PacketSync(maxRetries = 0, maxBurst = 1000, windowMs = 10000, maxQueueDepth = 3)
        val payload = SimpleNbtPayload().apply { putString("msg", "test") }
        assertTrue(pktSync.sendToServer(payload, "mod"))
        assertTrue(pktSync.sendToServer(payload, "mod"))
        assertTrue(pktSync.sendToServer(payload, "mod"))
        assertFalse(pktSync.sendToServer(payload, "mod"))
    }

    @Test
    fun `disconnected player queue cleared gracefully`() {
        packetSync.sendToClient(SimpleNbtPayload().apply { putString("msg", "test") }, "targetMod", "player1")
        assertEquals(1, packetSync.getTotalQueueDepth())
        packetSync.clearPlayerQueue("targetMod", "player1")
        assertEquals(0, packetSync.getTotalQueueDepth())
    }

    @Test
    fun `getTotalQueueDepth returns sum of all queues`() {
        packetSync.sendToClient(SimpleNbtPayload().apply { putString("a", "1") }, "modA", "p1")
        packetSync.sendToClient(SimpleNbtPayload().apply { putString("a", "2") }, "modA", "p2")
        packetSync.sendToServer(SimpleNbtPayload().apply { putString("a", "3") }, "modB")
        assertEquals(3, packetSync.getTotalQueueDepth())
    }

    @Test
    fun `clearAll removes everything`() {
        packetSync.sendToServer(SimpleNbtPayload(), "modA")
        packetSync.clearAll()
        assertEquals(0, packetSync.getTotalQueueDepth())
        assertEquals(0, packetSync.getTotalFailureCount())
    }

    @Test
    fun `SimpleNbtPayload copy preserves data`() {
        val original = SimpleNbtPayload()
        original.putString("key", "value"); original.putInt("num", 42)
        val copy = original.copy()
        assertEquals("value", copy.getString("key"))
        assertEquals(42, copy.getInt("num"))
    }
}
