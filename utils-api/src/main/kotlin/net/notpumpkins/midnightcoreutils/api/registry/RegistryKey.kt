package net.notpumpkins.midnightcoreutils.api.registry

data class RegistryKey(
    val modId: String,
    val name: String,
    val type: RegistryType
) {
    fun toResourceLocation(): String = "$modId:$name"

    companion object {
        fun parse(value: String, type: RegistryType): RegistryKey {
            val parts = value.split(":", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid registry key: '$value'. Expected format 'modId:name'")
            }
            return RegistryKey(parts[0], parts[1], type)
        }
    }
}
