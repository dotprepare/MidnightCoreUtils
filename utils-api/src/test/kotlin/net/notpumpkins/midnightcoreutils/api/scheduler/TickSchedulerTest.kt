package net.notpumpkins.midnightcoreutils.api.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

class TickSchedulerTest {

    private lateinit var scheduler: TickScheduler

    @BeforeEach
    fun setUp() { scheduler = TickScheduler() }

    @Test
    fun `task executes after specified ticks`() {
        val executed = AtomicInteger(0)
        scheduler.schedule(owner = "test", action = { executed.incrementAndGet() }, delayTicks = 3)
        scheduler.tick(TickPhase.SERVER); scheduler.tick(TickPhase.SERVER)
        assertEquals(0, executed.get())
        scheduler.tick(TickPhase.SERVER)
        assertEquals(1, executed.get())
    }

    @Test
    fun `task with zero delay executes immediately`() {
        val executed = AtomicInteger(0)
        scheduler.schedule(owner = "test", action = { executed.incrementAndGet() }, delayTicks = 0)
        scheduler.tick(TickPhase.SERVER)
        assertEquals(1, executed.get())
    }

    @Test
    fun `repeating task executes multiple times`() {
        val executed = AtomicInteger(0)
        scheduler.scheduleRepeating(owner = "test", action = { executed.incrementAndGet() }, intervalTicks = 2)
        scheduler.tick(TickPhase.SERVER); assertEquals(0, executed.get())
        scheduler.tick(TickPhase.SERVER); assertEquals(1, executed.get())
        scheduler.tick(TickPhase.SERVER); assertEquals(1, executed.get())
        scheduler.tick(TickPhase.SERVER); assertEquals(2, executed.get())
    }

    @Test
    fun `cancelled task does not execute`() {
        val executed = AtomicInteger(0)
        val id = scheduler.schedule(owner = "test", action = { executed.incrementAndGet() }, delayTicks = 1)
        scheduler.cancel(id)
        scheduler.tick(TickPhase.SERVER)
        assertEquals(0, executed.get())
    }

    @Test
    fun `cancelAll removes all tasks for owner`() {
        val executed = AtomicInteger(0)
        scheduler.schedule(owner = "test", action = { executed.incrementAndGet() }, delayTicks = 1)
        scheduler.schedule(owner = "test", action = { executed.incrementAndGet() }, delayTicks = 1)
        scheduler.cancelAll("test")
        scheduler.tick(TickPhase.SERVER)
        assertEquals(0, executed.get())
    }

    @Test
    fun `client and server tasks are separated`() {
        val serverExecuted = AtomicInteger(0)
        val clientExecuted = AtomicInteger(0)
        scheduler.schedule(owner = "test", action = { serverExecuted.incrementAndGet() }, delayTicks = 0, phase = TickPhase.SERVER)
        scheduler.schedule(owner = "test", action = { clientExecuted.incrementAndGet() }, delayTicks = 0, phase = TickPhase.CLIENT)
        scheduler.tick(TickPhase.SERVER)
        assertEquals(1, serverExecuted.get()); assertEquals(0, clientExecuted.get())
        scheduler.tick(TickPhase.CLIENT)
        assertEquals(1, clientExecuted.get())
    }

    @Test
    fun `mod unload cancels all tasks for owner only`() {
        val executed = AtomicInteger(0)
        scheduler.schedule(owner = "modA", action = { executed.incrementAndGet() }, delayTicks = 1)
        scheduler.schedule(owner = "modB", action = { executed.incrementAndGet() }, delayTicks = 1)
        scheduler.onModUnload("modA")
        scheduler.tick(TickPhase.SERVER)
        assertEquals(1, executed.get())
        assertEquals(0, scheduler.getPendingTaskCount())
    }

    @Test
    fun `shutdown prevents new tasks and clears all`() {
        scheduler.schedule(owner = "test", action = { }, delayTicks = 1)
        scheduler.shutdown()
        assertEquals(0, scheduler.getPendingTaskCount())
    }

    @Test
    fun `exception in task does not crash tick loop`() {
        val executed = AtomicInteger(0)
        scheduler.schedule(owner = "test", action = { throw RuntimeException("Boom") }, delayTicks = 0)
        scheduler.schedule(owner = "test", action = { executed.incrementAndGet() }, delayTicks = 0)
        scheduler.tick(TickPhase.SERVER)
        assertEquals(1, executed.get())
    }

    @Test
    fun `getPendingTaskCount returns correct count`() {
        scheduler.schedule(owner = "test", action = { }, delayTicks = 5)
        scheduler.schedule(owner = "test", action = { }, delayTicks = 3)
        assertEquals(2, scheduler.getPendingTaskCount())
    }

    @Test
    fun `stress test 1000 simultaneous scheduled tasks`() {
        val executed = AtomicInteger(0)
        for (i in 0 until 1000) { scheduler.schedule(owner = "stress", action = { executed.incrementAndGet() }, delayTicks = 1) }
        assertEquals(1000, scheduler.getPendingTaskCount())
        scheduler.tick(TickPhase.SERVER); scheduler.tick(TickPhase.SERVER)
        assertEquals(1000, executed.get())
        assertEquals(0, scheduler.getPendingTaskCount())
    }

    @Test
    fun `mod unload mid-schedule does not crash`() {
        val executed = AtomicInteger(0)
        scheduler.schedule(owner = "modA", action = { executed.incrementAndGet() }, delayTicks = 5)
        scheduler.schedule(owner = "modA", action = { executed.incrementAndGet() }, delayTicks = 10)
        scheduler.onModUnload("modA")
        for (i in 0 until 20) { scheduler.tick(TickPhase.SERVER) }
        assertEquals(0, executed.get())
    }

    @Test
    fun `negative delay throws`() {
        assertThrows<IllegalArgumentException> { scheduler.schedule(owner = "test", action = { }, delayTicks = -1) }
    }
}
