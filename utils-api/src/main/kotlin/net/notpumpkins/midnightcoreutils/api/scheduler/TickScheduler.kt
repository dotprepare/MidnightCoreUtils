package net.notpumpkins.midnightcoreutils.api.scheduler

import net.notpumpkins.midnightcoreutils.api.util.atomicLong
import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class TickScheduler {
    private data class ScheduledTask(
        val id: Long,
        val owner: String,
        val phase: TickPhase,
        var remainingTicks: Int,
        val interval: Int,
        val action: () -> Unit,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
        val repeating: Boolean
    )

    private val clientTasks = ConcurrentLinkedQueue<ScheduledTask>()
    private val serverTasks = ConcurrentLinkedQueue<ScheduledTask>()
    private val taskCounter = atomicLong(0)
    private val tasksById = concurrentHashMap<Long, ScheduledTask>()
    private val ownerTasks = concurrentHashMap<String, MutableSet<Long>>()
    private var shutdown = AtomicBoolean(false)

    fun schedule(
        owner: String,
        action: () -> Unit,
        delayTicks: Int,
        phase: TickPhase = TickPhase.SERVER
    ): Long {
        require(delayTicks >= 0) { "delayTicks must be >= 0" }
        val id = taskCounter.incrementAndGet()
        val task = ScheduledTask(
            id = id,
            owner = owner,
            phase = phase,
            remainingTicks = delayTicks,
            interval = -1,
            action = action,
            repeating = false
        )
        enqueueTask(task)
        return id
    }

    fun scheduleRepeating(
        owner: String,
        action: () -> Unit,
        intervalTicks: Int,
        delayTicks: Int = intervalTicks,
        phase: TickPhase = TickPhase.SERVER
    ): Long {
        require(intervalTicks > 0) { "intervalTicks must be > 0" }
        require(delayTicks >= 0) { "delayTicks must be >= 0" }
        val id = taskCounter.incrementAndGet()
        val task = ScheduledTask(
            id = id,
            owner = owner,
            phase = phase,
            remainingTicks = delayTicks,
            interval = intervalTicks,
            action = action,
            repeating = true
        )
        enqueueTask(task)
        return id
    }

    private fun enqueueTask(task: ScheduledTask) {
        if (shutdown.get()) return
        tasksById[task.id] = task
        ownerTasks.getOrPut(task.owner) { mutableSetOf() }.add(task.id)
        when (task.phase) {
            TickPhase.CLIENT -> clientTasks.add(task)
            TickPhase.SERVER -> serverTasks.add(task)
        }
    }

    fun cancel(taskId: Long): Boolean {
        val task = tasksById[taskId] ?: return false
        task.cancelled.set(true)
        return true
    }

    fun cancelAll(owner: String) {
        val ids = ownerTasks.remove(owner) ?: return
        ids.forEach { id ->
            tasksById[id]?.cancelled?.set(true)
        }
    }

    fun tick(phase: TickPhase) {
        if (shutdown.get()) return
        val queue = when (phase) {
            TickPhase.CLIENT -> clientTasks
            TickPhase.SERVER -> serverTasks
        }

        val snapshot = queue.toTypedArray()

        for (task in snapshot) {
            if (task.cancelled.get()) {
                queue.remove(task)
                continue
            }

            task.remainingTicks--

            if (task.remainingTicks <= 0) {
                if (!task.cancelled.get()) {
                    try {
                        task.action.invoke()
                    } catch (e: Exception) {
                        // Logged by caller, never crash tick loop
                    }
                }

                if (task.repeating && !task.cancelled.get()) {
                    task.remainingTicks = task.interval
                } else {
                    queue.remove(task)
                    tasksById.remove(task.id)
                    ownerTasks[task.owner]?.remove(task.id)
                }
            }
        }
    }

    fun onModUnload(owner: String) {
        cancelAll(owner)
    }

    fun shutdown() {
        shutdown.set(true)
        clientTasks.clear()
        serverTasks.clear()
        tasksById.clear()
        ownerTasks.clear()
    }

    fun getPendingTaskCount(): Int = tasksById.size

    fun getTaskCountForOwner(owner: String): Int =
        ownerTasks[owner]?.size ?: 0
}
