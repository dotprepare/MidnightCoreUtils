package net.notpumpkins.midnightcoreutils.api.event

import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap
import net.notpumpkins.midnightcoreutils.api.util.concurrentList
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private val EMPTY_EVENT_ARRAY = arrayOfNulls<ModEvent>(0)

class ModEventBus {
    private val listeners = concurrentHashMap<Class<*>, CopyOnWriteArrayList<EventSubscription<*>>>()
    private val recursionDepth = ThreadLocal.withInitial { AtomicInteger(0) }
    private val registeredOwners = concurrentHashMap<String, MutableSet<String>>()

    companion object {
        private const val MAX_RECURSION_DEPTH = 64
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ModEvent> subscribe(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        owner: String,
        listener: (T) -> Unit
    ): EventSubscription<T> {
        val sub = EventSubscription(listener, priority, owner, eventClass)

        val subs = listeners.getOrPut(eventClass) { CopyOnWriteArrayList() }

        val duplicate = subs.any { it == sub }
        if (duplicate) {
            throw IllegalArgumentException(
                "Duplicate listener registration for $owner on ${eventClass.simpleName}"
            )
        }

        subs.add(sub)
        subs.sortWith(compareBy { it.priority.value })

        registeredOwners.getOrPut(owner) { mutableSetOf() }.add(eventClass.name)

        return sub
    }

    fun <T : ModEvent> post(event: T): T {
        val depth = recursionDepth.get().incrementAndGet()
        try {
            if (depth > MAX_RECURSION_DEPTH) {
                throw StackOverflowError(
                    "Event recursion depth exceeded $MAX_RECURSION_DEPTH: " +
                        "possible circular event posting detected for ${event::class.java.simpleName}"
                )
            }

            val subs = listeners[event::class.java] ?: return event
            val array = subs.toTypedArray()

            for (sub in array) {
                if (!sub.isActive()) continue
                if (event.isPropagationStopped()) break

                @Suppress("UNCHECKED_CAST")
                (sub as EventSubscription<T>).listener.invoke(event)
            }

            return event
        } finally {
            recursionDepth.get().decrementAndGet()
        }
    }

    fun unsubscribeAll(owner: String) {
        registeredOwners.remove(owner)
        listeners.values.forEach { list ->
            list.removeAll { it.owner == owner }
        }
    }

    fun getActiveListenerCount(): Int {
        return listeners.values.sumOf { list ->
            list.count { it.isActive() }
        }
    }

    fun getListenersForOwner(owner: String): List<String> {
        return registeredOwners[owner]?.toList() ?: emptyList()
    }

    fun clear() {
        listeners.clear()
        registeredOwners.clear()
    }
}
