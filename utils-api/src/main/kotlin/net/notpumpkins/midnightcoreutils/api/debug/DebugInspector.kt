package net.notpumpkins.midnightcoreutils.api.debug

import net.notpumpkins.midnightcoreutils.api.error.ErrorBoundary
import net.notpumpkins.midnightcoreutils.api.event.ModEventBus
import net.notpumpkins.midnightcoreutils.api.lifecycle.ModLifecycleHooks
import net.notpumpkins.midnightcoreutils.api.network.PacketSync
import net.notpumpkins.midnightcoreutils.api.registry.SharedRegistry
import net.notpumpkins.midnightcoreutils.api.scheduler.TickScheduler
import net.notpumpkins.midnightcoreutils.api.service.ServiceLocator
import net.notpumpkins.midnightcoreutils.api.config.SharedConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DebugInspector(
    private val enabled: Boolean = false,
    private val maxSamples: Int = 1000
) {
    data class PerfSample(
        val system: String,
        val operation: String,
        val durationNanos: Long,
        val timestamp: Long
    )

    data class SystemSnapshot(
        val name: String,
        val metrics: Map<String, Any>
    )

    private val perfSamples = ConcurrentHashMap<String, MutableList<PerfSample>>()
    private val timerEnabled = AtomicLong(if (enabled) 1 else 0)

    private var eventBus: ModEventBus? = null
    private var serviceLocator: ServiceLocator? = null
    private var tickScheduler: TickScheduler? = null
    private var packetSync: PacketSync? = null
    private var sharedRegistry: SharedRegistry? = null
    private var lifecycleHooks: ModLifecycleHooks? = null
    private var errorBoundary: ErrorBoundary? = null
    private var sharedConfig: SharedConfig? = null

    fun registerSystems(
        eventBus: ModEventBus? = null,
        serviceLocator: ServiceLocator? = null,
        tickScheduler: TickScheduler? = null,
        packetSync: PacketSync? = null,
        sharedRegistry: SharedRegistry? = null,
        lifecycleHooks: ModLifecycleHooks? = null,
        errorBoundary: ErrorBoundary? = null,
        sharedConfig: SharedConfig? = null
    ) {
        this.eventBus = eventBus
        this.serviceLocator = serviceLocator
        this.tickScheduler = tickScheduler
        this.packetSync = packetSync
        this.sharedRegistry = sharedRegistry
        this.lifecycleHooks = lifecycleHooks
        this.errorBoundary = errorBoundary
        this.sharedConfig = sharedConfig
    }

    fun <T> time(system: String, operation: String, block: () -> T): T {
        if (!isEnabled()) return block()

        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val duration = System.nanoTime() - start
            recordSample(system, operation, duration)
        }
    }

    private fun recordSample(system: String, operation: String, durationNanos: Long) {
        val samples = perfSamples.getOrPut(system) { mutableListOf() }
        synchronized(samples) {
            samples.add(PerfSample(system, operation, durationNanos, System.currentTimeMillis()))
            if (samples.size > maxSamples) {
                samples.removeAt(0)
            }
        }
    }

    fun getAverageMs(system: String): Double {
        val samples = perfSamples[system] ?: return 0.0
        synchronized(samples) {
            if (samples.isEmpty()) return 0.0
            return samples.map { it.durationNanos }.average() / 1_000_000.0
        }
    }

    fun getSampleCount(system: String): Int {
        return perfSamples[system]?.size ?: 0
    }

    fun dumpSnapshot(): String {
        val sb = StringBuilder()
        sb.appendLine("=== DebugInspector Snapshot ===")

        eventBus?.let { eb ->
            sb.appendLine("[ModEventBus] Active listeners: ${eb.getActiveListenerCount()}")
        }

        serviceLocator?.let { sl ->
            sb.appendLine("[ServiceLocator] Registered mods: ${sl.getRegisteredModIds()}")
        }

        tickScheduler?.let { ts ->
            sb.appendLine("[TickScheduler] Pending tasks: ${ts.getPendingTaskCount()}")
        }

        packetSync?.let { ps ->
            sb.appendLine("[PacketSync] Total queue depth: ${ps.getTotalQueueDepth()}")
            sb.appendLine("[PacketSync] Total sent: ${ps.getTotalSendCount()}")
            sb.appendLine("[PacketSync] Total failures: ${ps.getTotalFailureCount()}")
        }

        sharedRegistry?.let { sr ->
            sb.appendLine("[SharedRegistry] Total entries: ${sr.getCount()}")
            val types = RegistryTypeDisplay.values().joinToString(", ") { "${it.display}=${sr.getCount(it.type)}" }
            sb.appendLine("[SharedRegistry] By type: $types")
        }

        lifecycleHooks?.let { lh ->
            sb.appendLine("[ModLifecycleHooks] Log entries: ${lh.getLogs().size}")
        }

        errorBoundary?.let { eb ->
            sb.appendLine("[ErrorBoundary] Total errors: ${eb.getAllErrors().size}")
        }

        sharedConfig?.let { sc ->
            sb.appendLine("[SharedConfig] Registered namespaces: ${sc.getRegisteredModIds()}")
        }

        if (perfSamples.isNotEmpty()) {
            sb.appendLine("--- Performance ---")
            perfSamples.keys.sorted().forEach { system ->
                val avg = getAverageMs(system)
                val count = getSampleCount(system)
                sb.appendLine("  $system: avg ${"%.3f".format(avg)} ms ($count samples)")
            }
        }

        return sb.toString()
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(enabled: Boolean) {
        timerEnabled.set(if (enabled) 1 else 0)
        if (!enabled) {
            perfSamples.clear()
        }
    }

    fun clearSamples() {
        perfSamples.clear()
    }

    private enum class RegistryTypeDisplay(val display: String, val type: net.notpumpkins.midnightcoreutils.api.registry.RegistryType) {
        ITEMS("Items", net.notpumpkins.midnightcoreutils.api.registry.RegistryType.ITEM),
        BLOCKS("Blocks", net.notpumpkins.midnightcoreutils.api.registry.RegistryType.BLOCK),
        ENTITIES("Entities", net.notpumpkins.midnightcoreutils.api.registry.RegistryType.ENTITY);
    }
}
