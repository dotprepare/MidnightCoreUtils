package net.notpumpkins.midnightcoreutils.api.event

open class ModEvent {
    private var cancelled = false
    private var propagationStopped = false

    fun isCancelled(): Boolean = cancelled

    fun cancel() {
        cancelled = true
    }

    fun isPropagationStopped(): Boolean = propagationStopped

    fun stopPropagation() {
        propagationStopped = true
    }
}
