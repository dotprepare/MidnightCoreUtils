package net.notpumpkins.midnightcoreutils.bridge

import net.notpumpkins.midnightcoreutils.api.event.ModEventBus
import net.notpumpkins.midnightcoreutils.api.event.EventPriority
import net.notpumpkins.midnightcoreutils.api.event.ModEvent
import net.notpumpkins.midnightcoreutils.api.event.EventSubscription

class ForgeEventBridge {
    private val apiEventBus = ModEventBus()

    fun getApiEventBus(): ModEventBus = apiEventBus

    fun <T : ModEvent> subscribe(
        eventClass: Class<T>,
        priority: EventPriority = EventPriority.NORMAL,
        owner: String,
        listener: (T) -> Unit
    ): EventSubscription<T> {
        return apiEventBus.subscribe(eventClass, priority, owner, listener)
    }

    fun post(event: ModEvent): ModEvent {
        return apiEventBus.post(event)
    }

    fun unsubscribeAll(owner: String) {
        apiEventBus.unsubscribeAll(owner)
    }

    fun clear() {
        apiEventBus.clear()
    }
}
