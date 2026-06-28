package net.notpumpkins.midnightcoreutils.api.service

import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import net.notpumpkins.midnightcoreutils.api.util.withReadLock
import net.notpumpkins.midnightcoreutils.api.util.withWriteLock

class ServiceLocator {
    private val services = concurrentHashMap<String, MutableMap<Class<*>, Any>>()
    private val lazyFactories = concurrentHashMap<String, MutableMap<Class<*>, () -> Any>>()
    private val resolving = ThreadLocal.withInitial { mutableSetOf<String>() }
    private val lock = ReentrantReadWriteLock()

    fun <T : Any> registerService(
        modId: String,
        serviceClass: Class<T>,
        service: T
    ) {
        lock.withWriteLock {
            val modServices = services.getOrPut(modId) { concurrentHashMap() }
            if (modServices.containsKey(serviceClass)) {
                throw IllegalArgumentException(
                    "Service ${serviceClass.simpleName} already registered for mod '$modId'"
                )
            }
            modServices[serviceClass] = service
        }
    }

    fun <T : Any> registerLazyService(
        modId: String,
        serviceClass: Class<T>,
        factory: () -> T
    ) {
        lock.withWriteLock {
            val modFactories = lazyFactories.getOrPut(modId) { concurrentHashMap() }
            if (modFactories.containsKey(serviceClass)) {
                throw IllegalArgumentException(
                    "Lazy service ${serviceClass.simpleName} already registered for mod '$modId'"
                )
            }
            modFactories[serviceClass] = factory
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getService(modId: String, serviceClass: Class<T>): T {
        // Fast path: check for existing service under read lock
        lock.withReadLock {
            val existing = getExistingService(modId, serviceClass)
            if (existing != null) return existing as T
        }

        // Lazy init or error path: acquire write lock
        lock.withWriteLock {
            // Re-check after lock upgrade
            val existing = getExistingService(modId, serviceClass)
            if (existing != null) return existing as T

            val modFactories = lazyFactories[modId]
            if (modFactories != null) {
                val factory = modFactories[serviceClass]
                if (factory != null) {
                    val path = resolving.get()
                    val depPath = "$modId:${serviceClass.simpleName}"
                    if (depPath in path) {
                        throw CircularDependencyException(
                            "Circular dependency detected when resolving $depPath. Current resolution path: ${path.toList()}"
                        )
                    }

                    path.add(depPath)
                    resolving.set(path)
                    val instance = factory()
                    path.remove(depPath)

                    val modSvcs = services.getOrPut(modId) { concurrentHashMap() }
                    modSvcs[serviceClass] = instance
                    modFactories.remove(serviceClass)
                    return instance as T
                }
            }

            throw IllegalStateException(
                "Service ${serviceClass.simpleName} not found for mod '$modId'. " +
                    "Ensure the providing mod has registered this service."
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getExistingService(modId: String, serviceClass: Class<T>): T? {
        val modServices = services[modId] ?: return null
        val existing = modServices[serviceClass] ?: return null
        return existing as T
    }

    fun hasService(modId: String, serviceClass: Class<*>): Boolean {
        return lock.withReadLock {
            services[modId]?.containsKey(serviceClass) == true
        }
    }

    fun removeServices(modId: String) {
        lock.withWriteLock {
            services.remove(modId)
            lazyFactories.remove(modId)
        }
    }

    fun clear() {
        lock.withWriteLock {
            services.clear()
            lazyFactories.clear()
        }
    }

    fun getRegisteredModIds(): Set<String> {
        return lock.withReadLock {
            (services.keys + lazyFactories.keys).toSet()
        }
    }
}
