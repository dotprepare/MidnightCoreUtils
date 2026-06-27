package net.notpumpkins.midnightcoreutils.api.event

class EventSubscription<T : ModEvent>(
    val listener: (T) -> Unit,
    val priority: EventPriority,
    val owner: String,
    val eventClass: Class<T>
) {
    private var active = true

    fun isActive(): Boolean = active

    fun unsubscribe() {
        active = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EventSubscription<*>) return false
        return listener === other.listener &&
            owner == other.owner &&
            eventClass == other.eventClass
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(listener)
        result = 31 * result + owner.hashCode()
        result = 31 * result + eventClass.hashCode()
        return result
    }
}
