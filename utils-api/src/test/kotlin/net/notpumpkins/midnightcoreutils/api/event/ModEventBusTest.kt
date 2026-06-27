package net.notpumpkins.midnightcoreutils.api.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ModEventBusTest {

    class TestEvent : ModEvent()
    class TestEvent2 : ModEvent()

    private lateinit var bus: ModEventBus

    @BeforeEach
    fun setUp() { bus = ModEventBus() }

    @Test
    fun `subscribe and post event`() {
        val received = AtomicInteger(0)
        bus.subscribe(TestEvent::class.java, owner = "test") { received.incrementAndGet() }
        bus.post(TestEvent())
        assertEquals(1, received.get())
    }

    @Test
    fun `multiple listeners receive event`() {
        val received = AtomicInteger(0)
        bus.subscribe(TestEvent::class.java, owner = "modA") { received.incrementAndGet() }
        bus.subscribe(TestEvent::class.java, owner = "modB") { received.incrementAndGet() }
        bus.post(TestEvent())
        assertEquals(2, received.get())
    }

    @Test
    fun `priority ordering is respected`() {
        val order = ConcurrentLinkedQueue<Int>()
        bus.subscribe(TestEvent::class.java, priority = EventPriority.LOWEST, owner = "modA") { order.add(1) }
        bus.subscribe(TestEvent::class.java, priority = EventPriority.NORMAL, owner = "modB") { order.add(2) }
        bus.subscribe(TestEvent::class.java, priority = EventPriority.HIGHEST, owner = "modC") { order.add(3) }
        bus.post(TestEvent())
        assertEquals(listOf(1, 2, 3), order.toList())
    }

    @Test
    fun `duplicate listener registration throws`() {
        val listener: (TestEvent) -> Unit = { }
        bus.subscribe(TestEvent::class.java, owner = "test", listener = listener)
        assertThrows<IllegalArgumentException> { bus.subscribe(TestEvent::class.java, owner = "test", listener = listener) }
    }

    @Test
    fun `unsubscribed listener does not receive event`() {
        val received = AtomicInteger(0)
        val sub = bus.subscribe(TestEvent::class.java, owner = "test") { received.incrementAndGet() }
        sub.unsubscribe()
        bus.post(TestEvent())
        assertEquals(0, received.get())
    }

    @Test
    fun `unsubscribeAll removes all listeners for owner`() {
        val received = AtomicInteger(0)
        bus.subscribe(TestEvent::class.java, owner = "test") { received.incrementAndGet() }
        bus.unsubscribeAll("test")
        bus.post(TestEvent())
        assertEquals(0, received.get())
    }

    @Test
    fun `stopPropagation prevents later listeners`() {
        val received = mutableListOf<String>()
        bus.subscribe(TestEvent::class.java, priority = EventPriority.LOW, owner = "modA") { received.add("A") }
        bus.subscribe(TestEvent::class.java, priority = EventPriority.NORMAL, owner = "modB") {
            received.add("B"); it.stopPropagation()
        }
        bus.subscribe(TestEvent::class.java, priority = EventPriority.HIGH, owner = "modC") { received.add("C") }
        bus.post(TestEvent())
        assertEquals(listOf("A", "B"), received)
    }

    @Test
    fun `event not dispatched to wrong type listeners`() {
        val received = AtomicInteger(0)
        bus.subscribe(TestEvent::class.java, owner = "test") { received.incrementAndGet() }
        bus.post(TestEvent2())
        assertEquals(0, received.get())
    }

    @Test
    fun `getActiveListenerCount returns correct count`() {
        bus.subscribe(TestEvent::class.java, owner = "modA") { }
        bus.subscribe(TestEvent2::class.java, owner = "modA") { }
        bus.subscribe(TestEvent::class.java, owner = "modB") { }
        assertEquals(3, bus.getActiveListenerCount())
    }

    @Test
    fun `recursion depth guard throws on deep nesting`() {
        bus.subscribe(TestEvent::class.java, owner = "loop") { bus.post(TestEvent()) }
        assertThrows<StackOverflowError> { bus.post(TestEvent()) }
    }

    @Test
    fun `100 concurrent listeners no race condition`() {
        val latch = CountDownLatch(100)
        val received = AtomicInteger(0)
        for (i in 0 until 100) {
            bus.subscribe(TestEvent::class.java, owner = "mod$i") { received.incrementAndGet(); latch.countDown() }
        }
        bus.post(TestEvent())
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(100, received.get())
    }

    @Test
    fun `stress test 10000 events throughput`() {
        val received = AtomicInteger(0)
        bus.subscribe(TestEvent::class.java, owner = "stress") { received.incrementAndGet() }
        val start = System.nanoTime()
        for (i in 0 until 10000) { bus.post(TestEvent()) }
        val durationMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(10000, received.get())
        assertTrue(durationMs < 5000, "10k events took ${durationMs}ms (expected <5000ms)")
    }

    @Test
    fun `clear removes all listeners`() {
        bus.subscribe(TestEvent::class.java, owner = "test") { }
        bus.clear()
        assertEquals(0, bus.getActiveListenerCount())
    }
}
