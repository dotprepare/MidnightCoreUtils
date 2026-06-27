package net.notpumpkins.midnightcoreutils.api.event

interface CancellableModEvent {
    fun isCancelled(): Boolean
    fun cancel()
}
