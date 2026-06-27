package net.notpumpkins.midnightcoreutils.api.config

import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import net.notpumpkins.midnightcoreutils.api.util.withReadLock
import net.notpumpkins.midnightcoreutils.api.util.withWriteLock

class SharedConfig(
    private val serializer: ConfigSerializer = JsonConfigSerializer()
) {
    private val namespaces = ConcurrentHashMap<String, ConcurrentHashMap<String, ConfigValue<*>>>()
    private val lock = ReentrantReadWriteLock()
    private var configDir: Path? = null
    private val watchService: WatchService? = null
    private val hotReloadEnabled = AtomicBoolean(false)

    fun setConfigDirectory(path: Path) {
        configDir = path
        path.toFile().mkdirs()
    }

    fun <T : Any> registerValue(
        modId: String,
        key: String,
        defaultValue: T
    ): ConfigValue<T> {
        lock.withWriteLock {
            val namespace = namespaces.getOrPut(modId) { ConcurrentHashMap() }
            @Suppress("UNCHECKED_CAST")
            val existing = namespace[key] as? ConfigValue<T>
            if (existing != null) {
                return existing
            }
            val value = ConfigValue(key, defaultValue, defaultValue)
            namespace[key] = value
            return value
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getValue(modId: String, key: String): ConfigValue<T>? {
        lock.withReadLock {
            val namespace = namespaces[modId] ?: return null
            return namespace[key] as? ConfigValue<T>
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(modId: String, key: String, default: T): T {
        lock.withReadLock {
            val namespace = namespaces[modId] ?: return default
            val configValue = namespace[key] as? ConfigValue<T> ?: return default
            return configValue.get()
        }
    }

    fun <T : Any> set(modId: String, key: String, value: T): Boolean {
        lock.withWriteLock {
            val namespace = namespaces[modId] ?: return false
            @Suppress("UNCHECKED_CAST")
            val configValue = namespace[key] as? ConfigValue<T> ?: return false
            configValue.set(value)
            return true
        }
    }

    fun loadFromFile(path: Path) {
        if (!path.toFile().exists()) return
        try {
            val raw = path.toFile().readText()
            val data = serializer.deserialize(raw)
            lock.withWriteLock {
                data.forEach { (modId, values) ->
                    val namespace = namespaces.getOrPut(modId) { ConcurrentHashMap() }
                    values.forEach { (key, value) ->
                        @Suppress("UNCHECKED_CAST")
                        val cv = namespace[key] as? ConfigValue<Any>
                        if (cv != null && value != null) {
                            try {
                                val converted = convertValue(value, cv.get()::class.java)
                                if (converted != null) {
                                    cv.set(converted as Any)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to load config from ${path.fileName}: ${e.message}", e)
        }
    }

    fun saveToFile(path: Path) {
        try {
            path.parent.toFile().mkdirs()
            val data = linkedMapOf<String, Map<String, Any?>>()
            lock.withReadLock {
                namespaces.forEach { (modId, values) ->
                    val sorted = linkedMapOf<String, Any?>()
                    values.entries.sortedBy { it.key }.forEach { (key, cv) ->
                        sorted[key] = cv.get()
                    }
                    data[modId] = sorted
                }
            }
            val json = serializer.serialize(data)
            val tempFile = path.resolveSibling("${path.fileName}.tmp")
            tempFile.toFile().writeText(json)
            tempFile.toFile().renameTo(path.toFile())
        } catch (e: Exception) {
            throw RuntimeException("Failed to save config to ${path.fileName}: ${e.message}", e)
        }
    }

    fun enableHotReload(dir: Path) {
        configDir = dir
        hotReloadEnabled.set(true)
    }

    fun disableHotReload() {
        hotReloadEnabled.set(false)
    }

    fun removeNamespace(modId: String) {
        lock.withWriteLock {
            namespaces.remove(modId)
        }
    }

    fun clear() {
        lock.withWriteLock {
            namespaces.clear()
        }
    }

    fun getRegisteredKeys(modId: String): Set<String> {
        lock.withReadLock {
            return namespaces[modId]?.keys?.toSet() ?: emptySet()
        }
    }

    fun getRegisteredModIds(): Set<String> {
        lock.withReadLock {
            return namespaces.keys.toSet()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertValue(value: Any?, targetClass: Class<*>): Any? {
        if (value == null) return null
        val tc = when {
            targetClass == Int::class.java || targetClass == Integer::class.java -> Int::class.java
            targetClass == Long::class.java || targetClass == java.lang.Long::class.java -> Long::class.java
            targetClass == Double::class.java || targetClass == java.lang.Double::class.java -> Double::class.java
            targetClass == Float::class.java || targetClass == java.lang.Float::class.java -> Float::class.java
            targetClass == Boolean::class.java || targetClass == java.lang.Boolean::class.java -> Boolean::class.java
            else -> targetClass
        }
        return when {
            tc.isInstance(value) -> value
            value is Number && tc == Int::class.java -> value.toInt()
            value is Number && tc == Long::class.java -> value.toLong()
            value is Number && tc == Double::class.java -> value.toDouble()
            value is Number && tc == Float::class.java -> value.toFloat()
            value is Number && tc == String::class.java -> value.toString()
            value is String && tc == Int::class.java -> value.toIntOrNull()
            value is String && tc == Long::class.java -> value.toLongOrNull()
            value is String && tc == Double::class.java -> value.toDoubleOrNull()
            value is String && tc == Boolean::class.java -> value.toBooleanStrictOrNull()
            else -> null
        }
    }
}
