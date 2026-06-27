package net.notpumpkins.midnightcoreutils.api.error

import net.notpumpkins.midnightcoreutils.api.event.ModEventBus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

class ErrorBoundaryTest {

    private lateinit var eventBus: ModEventBus
    private lateinit var errorBoundary: ErrorBoundary
    private val loggedErrors = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        eventBus = ModEventBus(); loggedErrors.clear()
        errorBoundary = ErrorBoundary(eventBus = eventBus, logger = { msg, _ -> loggedErrors.add(msg) })
    }

    @Test
    fun `execute with successful block returns result`() {
        assertEquals(42, errorBoundary.execute("modA", "compute") { 42 })
    }

    @Test
    fun `execute with failing block returns null`() {
        assertNull(errorBoundary.execute("modA", "crash") { throw RuntimeException("Kaboom") })
    }

    @Test
    fun `failing block disables feature`() {
        errorBoundary.execute("modA", "crash") { throw RuntimeException("Kaboom") }
        assertTrue(errorBoundary.isFeatureDisabled("modA", "crash"))
    }

    @Test
    fun `disabled feature returns null without executing`() {
        val executed = AtomicInteger(0)
        errorBoundary.execute("modA", "crash") { throw RuntimeException("Boom") }
        errorBoundary.execute("modA", "crash") { executed.incrementAndGet() }
        assertEquals(0, executed.get())
    }

    @Test
    fun `reenableFeature restores feature`() {
        errorBoundary.execute("modA", "crash") { throw RuntimeException("Boom") }
        errorBoundary.reenableFeature("modA", "crash")
        assertEquals(99, errorBoundary.execute("modA", "crash") { 99 })
    }

    @Test
    fun `ServiceUnavailableEvent posted on failure`() {
        val receivedEvents = mutableListOf<ServiceUnavailableEvent>()
        eventBus.subscribe(ServiceUnavailableEvent::class.java, owner = "test") { receivedEvents.add(it) }
        errorBoundary.execute("modA", "feature") { throw RuntimeException("fail") }
        assertEquals(1, receivedEvents.size)
        assertEquals("modA", receivedEvents[0].modId)
        assertTrue(receivedEvents[0].reason.contains("fail"))
    }

    @Test
    fun `getErrors returns recorded errors for mod`() {
        errorBoundary.execute("modA", "feature") { throw RuntimeException("fail") }
        val errors = errorBoundary.getErrors("modA")
        assertEquals(1, errors.size)
        assertEquals("modA", errors[0].modId)
        assertTrue(errors[0].disabled)
    }

    @Test
    fun `other systems unaffected when one crashes`() {
        errorBoundary.execute("modA", "crash1") { throw RuntimeException("Boom") }
        assertEquals(42, errorBoundary.execute("modB", "ok") { 42 })
    }

    @Test
    fun `executeOrThrow propagates exception`() {
        assertThrows<RuntimeException> { errorBoundary.executeOrThrow("modA", "crash") { throw RuntimeException("propagated") } }
    }

    @Test
    fun `shutdown prevents execution`() {
        errorBoundary.shutdown()
        assertNull(errorBoundary.execute("modA", "feature") { 42 })
    }

    @Test
    fun `clearMod removes mod state`() {
        errorBoundary.execute("modA", "feature") { throw RuntimeException("fail") }
        errorBoundary.clearMod("modA")
        assertTrue(errorBoundary.getErrors("modA").isEmpty())
        assertFalse(errorBoundary.isFeatureDisabled("modA", "feature"))
    }
}
