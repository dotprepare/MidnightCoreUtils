package net.notpumpkins.midnightcoreutils.api.config

class ConfigValue<T>(
    val key: String,
    private var value: T,
    private val defaultValue: T
) {
    fun get(): T = value
    fun set(newValue: T) { value = newValue }
    fun reset() { value = defaultValue }
    fun isDefault(): Boolean = value == defaultValue
}
