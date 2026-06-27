package net.notpumpkins.midnightcoreutils.api.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SharedConfigTest {

    private lateinit var config: SharedConfig
    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        config = SharedConfig(JsonConfigSerializer())
        tempDir = Files.createTempDirectory("config-test")
    }

    @Test
    fun `register and get value`() {
        assertEquals("hello", config.registerValue("modA", "greeting", "hello").get())
    }

    @Test
    fun `get with default fallback`() {
        assertEquals("default", config.get<String>("modA", "nonexistent", "default"))
    }

    @Test
    fun `set value and retrieve`() {
        config.registerValue("modA", "count", 42); config.set("modA", "count", 100)
        assertEquals(100, config.get<Int>("modA", "count", -1))
    }

    @Test
    fun `registering same key twice returns existing`() {
        val cv1 = config.registerValue("modA", "key", "first")
        val cv2 = config.registerValue("modA", "key", "second")
        assertEquals("first", cv1.get()); assertEquals(cv1, cv2)
    }

    @Test
    fun `getRegisteredKeys returns correct keys`() {
        config.registerValue("modA", "key1", 1); config.registerValue("modA", "key2", 2.0)
        assertTrue(config.getRegisteredKeys("modA").containsAll(setOf("key1", "key2")))
    }

    @Test
    fun `different namespaces are isolated`() {
        config.registerValue("modA", "key", "valueA"); config.registerValue("modB", "key", "valueB")
        assertEquals("valueA", config.get<String>("modA", "key", ""))
        assertEquals("valueB", config.get<String>("modB", "key", ""))
    }

    @Test
    fun `save and load from file`() {
        config.registerValue("modA", "name", "Alice"); config.registerValue("modA", "age", 30)
        val file = tempDir.resolve("test-config.json")
        config.saveToFile(file); assertTrue(file.toFile().exists())
        val config2 = SharedConfig(JsonConfigSerializer())
        config2.registerValue("modA", "name", "default"); config2.registerValue("modA", "age", 0)
        config2.loadFromFile(file)
        assertEquals("Alice", config2.get<String>("modA", "name", ""))
        assertEquals(30, config2.get<Int>("modA", "age", -1))
    }

    @Test
    fun `load from non-existent file does not throw`() {
        val file = tempDir.resolve("nonexistent.json")
        config.registerValue("modA", "name", "default")
        config.loadFromFile(file)
        assertEquals("default", config.get<String>("modA", "name", ""))
    }

    @Test
    fun `removeNamespace clears namespace`() {
        config.registerValue("modA", "key", "value"); config.removeNamespace("modA")
        assertTrue(config.getRegisteredKeys("modA").isEmpty())
    }

    @Test
    fun `reset value to default`() {
        val cv = config.registerValue("modA", "key", "default")
        cv.set("changed"); cv.reset()
        assertEquals("default", cv.get())
    }

    @Test
    fun `concurrent read write to config`() {
        val threads = 10; val latch = CountDownLatch(threads)
        val errors = java.util.concurrent.atomic.AtomicInteger(0)
        config.registerValue("modA", "counter", 0)
        for (i in 0 until threads) {
            Thread {
                try { config.set("modA", "counter", i); config.get<Int>("modA", "counter", -1) }
                catch (_: Exception) { errors.incrementAndGet() }
                finally { latch.countDown() }
            }.start()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(0, errors.get())
    }

    @Test
    fun `JsonConfigSerializer round trip`() {
        val serializer = JsonConfigSerializer()
        val data = linkedMapOf("modA" to linkedMapOf("name" to "Alice", "age" to 30), "modB" to linkedMapOf("enabled" to true, "ratio" to 1.5))
        val json = serializer.serialize(data)
        val parsed = serializer.deserialize(json)
        assertEquals("Alice", parsed["modA"]?.get("name"))
        assertEquals(30L, parsed["modA"]?.get("age"))
        assertEquals(true, parsed["modB"]?.get("enabled"))
        assertEquals(1.5, parsed["modB"]?.get("ratio"))
    }
}
