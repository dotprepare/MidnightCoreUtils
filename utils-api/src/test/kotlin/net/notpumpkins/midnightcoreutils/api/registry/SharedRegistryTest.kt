package net.notpumpkins.midnightcoreutils.api.registry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SharedRegistryTest {

    private lateinit var registry: SharedRegistry

    @BeforeEach
    fun setUp() { registry = SharedRegistry() }

    @Test
    fun `register and query item`() {
        assertEquals("item_instance", registry.register("modA", "test_item", RegistryType.ITEM) { "item_instance" }.objectReference)
    }

    @Test
    fun `register and query by key`() {
        registry.register("modA", "test_block", RegistryType.BLOCK) { "block_instance" }
        val entry = registry.get<String>(RegistryKey("modA", "test_block", RegistryType.BLOCK))
        assertNotNull(entry)
        assertEquals("block_instance", entry!!.objectReference)
    }

    @Test
    fun `duplicate key throws descriptive error`() {
        registry.register("modA", "dup", RegistryType.ITEM) { "first" }
        val ex = assertThrows<IllegalArgumentException> { registry.register("modA", "dup", RegistryType.ITEM) { "second" } }
        assertTrue(ex.message!!.contains("Duplicate"))
        assertTrue(ex.message!!.contains("modA:dup"))
    }

    @Test
    fun `same key different types allowed`() {
        registry.register("modA", "same_name", RegistryType.ITEM) { "item" }
        registry.register("modA", "same_name", RegistryType.BLOCK) { "block" }
        assertEquals(1, registry.getAllOfType<String>(RegistryType.ITEM).size)
        assertEquals(1, registry.getAllOfType<String>(RegistryType.BLOCK).size)
    }

    @Test
    fun `query by modId and type`() {
        registry.register("modA", "item1", RegistryType.ITEM) { "a1" }
        registry.register("modA", "item2", RegistryType.ITEM) { "a2" }
        registry.register("modB", "item1", RegistryType.ITEM) { "b1" }
        assertEquals(2, registry.query<String>("modA", RegistryType.ITEM).size)
    }

    @Test
    fun `get returns null for non-existent key`() {
        assertNull(registry.get<String>(RegistryKey("modA", "nonexistent", RegistryType.ITEM)))
    }

    @Test
    fun `getCount returns correct total`() {
        registry.register("modA", "i1", RegistryType.ITEM) { "i1" }
        registry.register("modA", "i2", RegistryType.ITEM) { "i2" }
        registry.register("modA", "b1", RegistryType.BLOCK) { "b1" }
        assertEquals(3, registry.getCount())
        assertEquals(2, registry.getCount(RegistryType.ITEM))
    }

    @Test
    fun `removeMod removes all entries for that mod`() {
        registry.register("modA", "item1", RegistryType.ITEM) { "a1" }
        registry.register("modB", "item1", RegistryType.ITEM) { "b1" }
        registry.removeMod("modA")
        assertEquals(0, registry.query<String>("modA", RegistryType.ITEM).size)
        assertEquals(1, registry.query<String>("modB", RegistryType.ITEM).size)
    }

    @Test
    fun `clear removes everything`() {
        registry.register("modA", "item1", RegistryType.ITEM) { "a1" }
        registry.clear(); assertEquals(0, registry.getCount())
    }

    @Test
    fun `stress test 500 registrations with query performance`() {
        for (i in 0 until 500) { registry.register("stress", "item$i", RegistryType.ITEM) { "item$i" } }
        assertEquals(500, registry.getCount())
        val results = registry.getAllOfType<String>(RegistryType.ITEM)
        assertEquals(500, results.size)
    }

    @Test
    fun `deferred actions run after registration`() {
        val executed = java.util.concurrent.atomic.AtomicInteger(0)
        registry.addDeferredAction("modA") { executed.incrementAndGet() }
        registry.addDeferredAction("modA") { executed.incrementAndGet() }
        registry.runDeferredActions("modA")
        assertEquals(2, executed.get())
    }
}
