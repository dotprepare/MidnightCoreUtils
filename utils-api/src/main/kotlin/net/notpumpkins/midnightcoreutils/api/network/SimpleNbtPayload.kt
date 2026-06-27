package net.notpumpkins.midnightcoreutils.api.network

import net.notpumpkins.midnightcoreutils.api.util.concurrentHashMap

class SimpleNbtPayload(
    private val maxByteArraySize: Int = MAX_BYTE_ARRAY_SIZE
) : NbtPayload {
    companion object {
        const val MAX_BYTE_ARRAY_SIZE = 512_000
    }

    private val data = concurrentHashMap<String, Any?>()

    override fun hasKey(key: String): Boolean = data.containsKey(key)

    override fun getString(key: String): String? = data[key] as? String
    override fun putString(key: String, value: String) { data[key] = value }

    override fun getInt(key: String): Int? = data[key] as? Int
    override fun putInt(key: String, value: Int) { data[key] = value }

    override fun getLong(key: String): Long? = data[key] as? Long
    override fun putLong(key: String, value: Long) { data[key] = value }

    override fun getDouble(key: String): Double? = data[key] as? Double
    override fun putDouble(key: String, value: Double) { data[key] = value }

    override fun getBoolean(key: String): Boolean? = data[key] as? Boolean
    override fun putBoolean(key: String, value: Boolean) { data[key] = value }

    override fun getByteArray(key: String): ByteArray? = data[key] as? ByteArray
    override fun putByteArray(key: String, value: ByteArray) {
        require(value.size <= maxByteArraySize) {
            "ByteArray too large: ${value.size} > $maxByteArraySize"
        }
        data[key] = value
    }

    override fun getCompound(key: String): NbtPayload? = data[key] as? NbtPayload
    override fun putCompound(key: String, value: NbtPayload) { data[key] = value }

    override fun getAllKeys(): Set<String> = data.keys.toSet()
    override fun copy(): NbtPayload {
        val copy = SimpleNbtPayload()
        data.forEach { (k, v) ->
            when (v) {
                is NbtPayload -> copy.putCompound(k, v.copy())
                is ByteArray -> copy.putByteArray(k, v.copyOf())
                else -> copy.data[k] = v
            }
        }
        return copy
    }

    override fun isEmpty(): Boolean = data.isEmpty()
    override fun size(): Int = data.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SimpleNbtPayload) return false
        return data == other.data
    }

    override fun hashCode(): Int = data.hashCode()
}
