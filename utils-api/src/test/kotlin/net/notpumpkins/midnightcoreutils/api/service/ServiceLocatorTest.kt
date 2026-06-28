package net.notpumpkins.midnightcoreutils.api.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ServiceLocatorTest {

    interface GreetingService { fun greet(): String }
    class EnglishGreetingService : GreetingService { override fun greet() = "Hello" }
    class FrenchGreetingService : GreetingService { override fun greet() = "Bonjour" }
    interface MathService { fun add(a: Int, b: Int): Int }
    class SimpleMathService : MathService { override fun add(a: Int, b: Int) = a + b }

    private lateinit var locator: ServiceLocator

    @BeforeEach
    fun setUp() { locator = ServiceLocator() }

    @Test
    fun `register and retrieve service`() {
        locator.registerService("modA", GreetingService::class.java, EnglishGreetingService())
        assertEquals("Hello", locator.getService("modA", GreetingService::class.java).greet())
    }

    @Test
    fun `register multiple services from different mods`() {
        locator.registerService("modA", GreetingService::class.java, EnglishGreetingService())
        locator.registerService("modB", GreetingService::class.java, FrenchGreetingService())
        assertEquals("Hello", locator.getService("modA", GreetingService::class.java).greet())
        assertEquals("Bonjour", locator.getService("modB", GreetingService::class.java).greet())
    }

    @Test
    fun `duplicate service registration throws`() {
        locator.registerService("modA", GreetingService::class.java, EnglishGreetingService())
        assertThrows<IllegalArgumentException> { locator.registerService("modA", GreetingService::class.java, FrenchGreetingService()) }
    }

    @Test
    fun `missing service throws with descriptive message`() {
        val ex = assertThrows<IllegalStateException> { locator.getService("nonexistent", GreetingService::class.java) }
        assertTrue(ex.message!!.contains("not found"))
        assertTrue(ex.message!!.contains("nonexistent"))
    }

    @Test
    fun `lazy service registration`() {
        val initCount = AtomicInteger(0)
        locator.registerLazyService("modA", GreetingService::class.java) { initCount.incrementAndGet(); EnglishGreetingService() }
        assertFalse(locator.hasService("modA", GreetingService::class.java))
        val service = locator.getService("modA", GreetingService::class.java)
        assertNotNull(service); assertEquals(1, initCount.get()); assertEquals("Hello", service.greet())
    }

    @Test
    fun `lazy service initialized only once`() {
        val initCount = AtomicInteger(0)
        locator.registerLazyService("modA", GreetingService::class.java) { initCount.incrementAndGet(); EnglishGreetingService() }
        locator.getService("modA", GreetingService::class.java)
        locator.getService("modA", GreetingService::class.java)
        assertEquals(1, initCount.get())
    }

    @Test
    fun `hasService returns correct status`() {
        assertFalse(locator.hasService("modA", GreetingService::class.java))
        locator.registerService("modA", GreetingService::class.java, EnglishGreetingService())
        assertTrue(locator.hasService("modA", GreetingService::class.java))
    }

    @Test
    fun `remove services for a mod`() {
        locator.registerService("modA", GreetingService::class.java, EnglishGreetingService())
        locator.removeServices("modA")
        assertFalse(locator.hasService("modA", GreetingService::class.java))
    }

    @Test
    fun `getRegisteredModIds returns all mods`() {
        locator.registerService("modA", GreetingService::class.java, EnglishGreetingService())
        locator.registerService("modB", MathService::class.java, SimpleMathService())
        assertTrue(locator.getRegisteredModIds().containsAll(setOf("modA", "modB")))
    }

    @Test
    fun `clear removes all`() {
        locator.registerService("modA", GreetingService::class.java, EnglishGreetingService())
        locator.clear()
        assertTrue(locator.getRegisteredModIds().isEmpty())
    }

    @Test
    fun `circular dependency detection`() {
        locator.registerLazyService("modA", GreetingService::class.java) { locator.getService("modA", MathService::class.java); EnglishGreetingService() }
        locator.registerLazyService("modA", MathService::class.java) { locator.getService("modA", GreetingService::class.java); SimpleMathService() }
        val ex = assertThrows<CircularDependencyException> { locator.getService("modA", GreetingService::class.java) }
        assertTrue(ex.message!!.contains("Circular"))
    }

    @Test
    fun `concurrent access to service locator`() {
        val threadCount = 10; val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)
        locator.registerService("modA", GreetingService::class.java, EnglishGreetingService())
        for (i in 0 until threadCount) {
            Thread {
                try { assertEquals("Hello", locator.getService("modA", GreetingService::class.java).greet()) }
                catch (_: Exception) { errors.incrementAndGet() }
                finally { latch.countDown() }
            }.apply { isDaemon = true }.start()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(0, errors.get())
    }
}
