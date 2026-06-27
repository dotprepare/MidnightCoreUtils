package net.notpumpkins.midnightcoreutils.api.lifecycle

enum class LifecyclePhase(val displayName: String, val order: Int) {
    PRE_INIT("PreInit", 0),
    INIT("Init", 1),
    POST_INIT("PostInit", 2)
}
