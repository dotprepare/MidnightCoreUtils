package net.notpumpkins.midnightcoreutils.api.lifecycle

import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import net.notpumpkins.midnightcoreutils.api.util.withReadLock
import net.notpumpkins.midnightcoreutils.api.util.withWriteLock

class ModLifecycleHooks(
    private val phaseTimeoutMs: Long = 30_000L
) {
    data class LifecycleEntry(
        val modId: String,
        val phase: LifecyclePhase,
        val action: () -> Unit,
        val order: Int = 0
    )

    data class LifecycleLogEntry(
        val modId: String,
        val phase: LifecyclePhase,
        val status: String,
        val timestamp: Long,
        val durationMs: Long
    )

    private val hooks = ConcurrentHashMap<LifecyclePhase, ConcurrentLinkedQueue<LifecycleEntry>>()
    private val shutdownHooks = ConcurrentLinkedQueue<Pair<String, () -> Unit>>()
    private val completed = ConcurrentHashMap<String, MutableSet<LifecyclePhase>>()
    private val lock = ReentrantReadWriteLock()
    private val shutdownInitiated = AtomicBoolean(false)
    private val logs = ConcurrentLinkedQueue<LifecycleLogEntry>()
    private val timedOut = ConcurrentHashMap<String, MutableSet<LifecyclePhase>>()

    fun registerHook(
        modId: String,
        phase: LifecyclePhase,
        order: Int = 0,
        action: () -> Unit
    ) {
        val entry = LifecycleEntry(modId, phase, action, order)
        val phaseHooks = hooks.getOrPut(phase) { ConcurrentLinkedQueue() }
        phaseHooks.add(entry)
    }

    fun registerShutdownHook(modId: String, action: () -> Unit) {
        shutdownHooks.add(modId to action)
    }

    fun executePhase(phase: LifecyclePhase) {
        val phaseHooks = hooks[phase] ?: return
        val sorted = phaseHooks.sortedWith(compareBy({ it.order }, { it.modId }))

        for (entry in sorted) {
            if (shutdownInitiated.get()) break
            if (entry.phase != phase) continue

            val startTime = System.currentTimeMillis()
            val threadError = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
            val thread = Thread {
                try {
                    entry.action.invoke()
                } catch (e: Throwable) {
                    threadError.set(e)
                }
            }
            thread.start()
            thread.join(phaseTimeoutMs)

            val duration = System.currentTimeMillis() - startTime
            val error = threadError.get()
            when {
                thread.isAlive -> {
                    thread.interrupt()
                    timedOut.getOrPut(entry.modId) { mutableSetOf() }.add(phase)
                    logs.add(LifecycleLogEntry(entry.modId, phase, "TIMEOUT", Instant.now().toEpochMilli(), duration))
                }
                error != null -> {
                    logs.add(LifecycleLogEntry(entry.modId, phase, "FAILED: ${error.message}", Instant.now().toEpochMilli(), duration))
                }
                else -> {
                    completed.getOrPut(entry.modId) { mutableSetOf() }.add(phase)
                    logs.add(LifecycleLogEntry(entry.modId, phase, "COMPLETED", Instant.now().toEpochMilli(), duration))
                }
            }
        }
    }

    fun executeAllPhases() {
        LifecyclePhase.entries.sortedBy { it.order }.forEach { phase ->
            executePhase(phase)
        }
    }

    fun executeShutdown() {
        shutdownInitiated.set(true)
        val sorted = shutdownHooks.toList().reversed()
        for ((modId, action) in sorted) {
            try {
                action.invoke()
                logs.add(
                    LifecycleLogEntry(
                        modId, LifecyclePhase.PRE_INIT, "SHUTDOWN_COMPLETED",
                        Instant.now().toEpochMilli(), 0L
                    )
                )
            } catch (e: Exception) {
                logs.add(
                    LifecycleLogEntry(
                        modId, LifecyclePhase.PRE_INIT, "SHUTDOWN_FAILED: ${e.message}",
                        Instant.now().toEpochMilli(), 0L
                    )
                )
            }
        }
    }

    fun hasCompleted(modId: String, phase: LifecyclePhase): Boolean {
        return completed[modId]?.contains(phase) == true
    }

    fun hasTimedOut(modId: String, phase: LifecyclePhase): Boolean {
        return timedOut[modId]?.contains(phase) == true
    }

    fun getLogs(): List<LifecycleLogEntry> = logs.toList()

    fun clearLogs() {
        logs.clear()
    }

    fun getHooksForMod(modId: String): List<LifecycleEntry> {
        val result = mutableListOf<LifecycleEntry>()
        hooks.values.forEach { queue ->
            queue.filter { it.modId == modId }.forEach { result.add(it) }
        }
        return result
    }

    fun removeHooks(modId: String) {
        hooks.values.forEach { queue -> queue.removeIf { it.modId == modId } }
        shutdownHooks.removeIf { it.first == modId }
        completed.remove(modId)
        timedOut.remove(modId)
    }

    fun clear() {
        hooks.clear()
        shutdownHooks.clear()
        completed.clear()
        logs.clear()
        timedOut.clear()
        shutdownInitiated.set(false)
    }
}
