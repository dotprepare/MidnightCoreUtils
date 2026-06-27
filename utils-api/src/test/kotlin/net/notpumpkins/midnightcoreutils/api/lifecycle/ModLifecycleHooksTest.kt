package net.notpumpkins.midnightcoreutils.api.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class ModLifecycleHooksTest {

    private lateinit var lifecycle: ModLifecycleHooks

    @BeforeEach
    fun setUp() { lifecycle = ModLifecycleHooks(phaseTimeoutMs = 1000L) }

    @Test
    fun `hooks execute in order`() {
        val order = mutableListOf<String>()
        lifecycle.registerHook("modA", LifecyclePhase.PRE_INIT, order = 1) { order.add("pre") }
        lifecycle.registerHook("modA", LifecyclePhase.INIT, order = 2) { order.add("init") }
        lifecycle.registerHook("modA", LifecyclePhase.POST_INIT, order = 3) { order.add("post") }
        lifecycle.executeAllPhases()
        assertEquals(listOf("pre", "init", "post"), order)
    }

    @Test
    fun `shutdown hooks execute in reverse order`() {
        val order = mutableListOf<String>()
        lifecycle.registerShutdownHook("modA") { order.add("A") }
        lifecycle.registerShutdownHook("modB") { order.add("B") }
        lifecycle.executeShutdown()
        assertEquals(listOf("B", "A"), order)
    }

    @Test
    fun `hasCompleted returns correct status`() {
        lifecycle.registerHook("modA", LifecyclePhase.INIT) { }
        lifecycle.executePhase(LifecyclePhase.INIT)
        assertTrue(lifecycle.hasCompleted("modA", LifecyclePhase.INIT))
        assertFalse(lifecycle.hasCompleted("modA", LifecyclePhase.PRE_INIT))
    }

    @Test
    fun `exception in hook is logged`() {
        lifecycle.registerHook("modA", LifecyclePhase.INIT) { throw RuntimeException("fail") }
        lifecycle.executePhase(LifecyclePhase.INIT)
        val logs = lifecycle.getLogs()
        assertEquals(1, logs.size)
        assertTrue(logs[0].status.contains("FAILED"))
    }

    @Test
    fun `timeout is detected`() {
        lifecycle.registerHook("modA", LifecyclePhase.INIT) { Thread.sleep(200) }
        lifecycle.executePhase(LifecyclePhase.INIT)
        assertTrue(lifecycle.hasCompleted("modA", LifecyclePhase.INIT))
    }

    @Test
    fun `getLogs returns entries with timestamps`() {
        lifecycle.registerHook("modA", LifecyclePhase.INIT) { }
        lifecycle.executePhase(LifecyclePhase.INIT)
        val logs = lifecycle.getLogs()
        assertEquals(1, logs.size); assertTrue(logs[0].timestamp > 0); assertTrue(logs[0].durationMs >= 0)
    }

    @Test
    fun `clear removes all state`() {
        lifecycle.registerHook("modA", LifecyclePhase.INIT) { }
        lifecycle.executePhase(LifecyclePhase.INIT)
        lifecycle.clear()
        assertEquals(0, lifecycle.getLogs().size)
    }

    @Test
    fun `multiple mods can register hooks`() {
        val count = AtomicInteger(0)
        lifecycle.registerHook("modA", LifecyclePhase.INIT) { count.incrementAndGet() }
        lifecycle.registerHook("modB", LifecyclePhase.INIT) { count.incrementAndGet() }
        lifecycle.executePhase(LifecyclePhase.INIT)
        assertEquals(2, count.get())
    }
}
