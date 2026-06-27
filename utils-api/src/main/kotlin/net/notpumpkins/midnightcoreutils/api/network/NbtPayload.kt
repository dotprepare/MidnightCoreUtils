package net.notpumpkins.midnightcoreutils.api.network

interface NbtPayload {
    fun hasKey(key: String): Boolean
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String): Int?
    fun putInt(key: String, value: Int)
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun getDouble(key: String): Double?
    fun putDouble(key: String, value: Double)
    fun getBoolean(key: String): Boolean?
    fun putBoolean(key: String, value: Boolean)
    fun getByteArray(key: String): ByteArray?
    fun putByteArray(key: String, value: ByteArray)
    fun getCompound(key: String): NbtPayload?
    fun putCompound(key: String, value: NbtPayload)
    fun getAllKeys(): Set<String>
    fun copy(): NbtPayload
    fun isEmpty(): Boolean
    fun size(): Int
}
