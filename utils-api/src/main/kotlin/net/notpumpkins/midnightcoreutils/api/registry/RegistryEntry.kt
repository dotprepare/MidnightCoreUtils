package net.notpumpkins.midnightcoreutils.api.registry

data class RegistryEntry<T : Any>(
    val key: RegistryKey,
    val supplier: () -> T
) {
    val objectReference: T? by lazy(supplier)
}
