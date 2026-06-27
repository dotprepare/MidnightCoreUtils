package net.notpumpkins.midnightcoreutils.api.debug

import net.notpumpkins.midnightcoreutils.api.event.ModEventBus
import net.notpumpkins.midnightcoreutils.api.scheduler.TickScheduler
import net.notpumpkins.midnightcoreutils.api.network.PacketSync
import net.notpumpkins.midnightcoreutils.api.service.ServiceLocator
import net.notpumpkins.midnightcoreutils.api.registry.SharedRegistry
import net.notpumpkins.midnightcoreutils.api.error.ErrorBoundary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DebugInspectorTest {

    private lateinit var inspector: DebugInspector

    @BeforeEach
    fun setUp() { inspector = DebugInspector(enabled = true) }

    @Test
    fun `snapshot is generated without error when no systems registered`() {
        assertTrue(inspector.dumpSnapshot().contains("DebugInspector Snapshot"))
    }

    @Test
    fun `time records samples`() {
        inspector.time("test", "op") { Thread.sleep(1) }
        assertEquals(1, inspector.getSampleCount("test"))
        assertTrue(inspector.getAverageMs("test") > 0)
    }

    @Test
    fun `time returns value from block`() {
        assertEquals(42, inspector.time("test", "identity") { 42 })
    }

    @Test
    fun `snapshot includes registered system info`() {
        inspector.registerSystems(eventBus = ModEventBus(), serviceLocator = ServiceLocator(),
            tickScheduler = TickScheduler(), packetSync = PacketSync(),
            sharedRegistry = SharedRegistry(), errorBoundary = ErrorBoundary())
        val snapshot = inspector.dumpSnapshot()
        assertTrue(snapshot.contains("ModEventBus"))
        assertTrue(snapshot.contains("TickScheduler"))
        assertTrue(snapshot.contains("PacketSync"))
        assertTrue(snapshot.contains("SharedRegistry"))
        assertTrue(snapshot.contains("ErrorBoundary"))
    }

    @Test
    fun `disabled inspector records no samples`() {
        val disabled = DebugInspector(enabled = false)
        disabled.time("test", "op") { Thread.sleep(1) }
        assertEquals(0, disabled.getSampleCount("test"))
    }

    @Test
    fun `setEnabled clears samples when disabled`() {
        inspector.time("test", "op") { }
        assertEquals(1, inspector.getSampleCount("test"))
        inspector.setEnabled(false)
        assertEquals(0, inspector.getSampleCount("test"))
    }

    @Test
    fun `sample count respects maxSamples`() {
        val limited = DebugInspector(enabled = true, maxSamples = 5)
        for (i in 0 until 10) { limited.time("test", "op$i") { } }
        assertEquals(5, limited.getSampleCount("test"))
    }
}
