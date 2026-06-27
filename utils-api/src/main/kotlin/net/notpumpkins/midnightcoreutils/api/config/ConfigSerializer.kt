package net.notpumpkins.midnightcoreutils.api.config

interface ConfigSerializer {
    fun serialize(data: Map<String, Map<String, Any?>>): String
    fun deserialize(raw: String): Map<String, Map<String, Any?>>
}
