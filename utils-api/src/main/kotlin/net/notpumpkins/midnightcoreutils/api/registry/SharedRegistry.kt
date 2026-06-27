package net.notpumpkins.midnightcoreutils.api.registry

import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import net.notpumpkins.midnightcoreutils.api.util.withReadLock
import net.notpumpkins.midnightcoreutils.api.util.withWriteLock

class SharedRegistry {
    private val entries = ConcurrentHashMap<RegistryType, ConcurrentHashMap<String, RegistryEntry<*>>>()
    private val byModId = ConcurrentHashMap<String, MutableSet<RegistryKey>>()
    private val lock = ReentrantReadWriteLock()
    private val deferredActions = concurrentHashMap<String, MutableList<() -> Unit>>()

    fun <T : Any> register(
        modId: String,
        name: String,
        type: RegistryType,
        supplier: () -> T
    ): RegistryEntry<T> {
        val key = RegistryKey(modId, name, type)
        lock.withWriteLock {
            val typeMap = entries.getOrPut(type) { ConcurrentHashMap() }
            if (typeMap.containsKey(key.toResourceLocation())) {
                throw IllegalArgumentException(
                    "Duplicate registry key: ${key.toResourceLocation()} of type ${type.registryName}. " +
                        "Key already registered by mod '${key.modId}'."
                )
            }
            val entry = RegistryEntry<T>(key, supplier)
            typeMap[key.toResourceLocation()] = entry
            byModId.getOrPut(modId) { mutableSetOf() }.add(key)
            return entry
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: RegistryKey): RegistryEntry<T>? {
        lock.withReadLock {
            val typeMap = entries[key.type] ?: return null
            return typeMap[key.toResourceLocation()] as? RegistryEntry<T>
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> query(modId: String, type: RegistryType): List<RegistryEntry<T>> {
        lock.withReadLock {
            val typeMap = entries[type] ?: return emptyList()
            return typeMap.entries
                .filter { it.key.startsWith("$modId:") }
                .map { it.value as RegistryEntry<T> }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAllOfType(type: RegistryType): List<RegistryEntry<T>> {
        lock.withReadLock {
            val typeMap = entries[type] ?: return emptyList()
            return typeMap.values.toList() as List<RegistryEntry<T>>
        }
    }

    fun getModIds(): Set<String> {
        lock.withReadLock {
            return byModId.keys.toSet()
        }
    }

    fun getCount(type: RegistryType? = null): Int {
        lock.withReadLock {
            return if (type != null) {
                entries[type]?.size ?: 0
            } else {
                entries.values.sumOf { it.size }
            }
        }
    }

    fun addDeferredAction(modId: String, action: () -> Unit) {
        deferredActions.getOrPut(modId) { mutableListOf() }.add(action)
    }

    fun runDeferredActions(modId: String) {
        val actions = deferredActions.remove(modId) ?: return
        actions.forEach { it.invoke() }
    }

    fun removeMod(modId: String) {
        lock.withWriteLock {
            val keys = byModId.remove(modId) ?: return
            keys.forEach { key ->
                entries[key.type]?.remove(key.toResourceLocation())
            }
            deferredActions.remove(modId)
        }
    }

    fun clear() {
        lock.withWriteLock {
            entries.clear()
            byModId.clear()
            deferredActions.clear()
        }
    }
}
