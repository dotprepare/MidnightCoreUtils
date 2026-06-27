package net.notpumpkins.midnightcoreutils.bridge

import net.notpumpkins.midnightcoreutils.api.network.NbtPayload
import net.notpumpkins.midnightcoreutils.api.network.PacketSync
import net.minecraft.nbt.CompoundTag

class ForgeNetworkBridge {
    private val packetSync = PacketSync()

    fun getPacketSync(): PacketSync = packetSync

    fun nbtToCompound(payload: NbtPayload): CompoundTag {
        val tag = CompoundTag()
        for (key in payload.getAllKeys()) {
            when {
                payload.getString(key) != null -> tag.putString(key, payload.getString(key)!!)
                payload.getInt(key) != null -> tag.putInt(key, payload.getInt(key)!!)
                payload.getLong(key) != null -> tag.putLong(key, payload.getLong(key)!!)
                payload.getDouble(key) != null -> tag.putDouble(key, payload.getDouble(key)!!)
                payload.getBoolean(key) != null -> tag.putBoolean(key, payload.getBoolean(key)!!)
                payload.getByteArray(key) != null -> tag.putByteArray(key, payload.getByteArray(key)!!)
                payload.getCompound(key) != null -> tag.put(key, nbtToCompound(payload.getCompound(key)!!))
            }
        }
        return tag
    }

    fun compoundToNbt(tag: CompoundTag): NbtPayload {
        val payload = net.notpumpkins.midnightcoreutils.api.network.SimpleNbtPayload()
        for (key in tag.allKeys) {
            val value = tag.get(key)
            when (value) {
                is net.minecraft.nbt.StringTag -> payload.putString(key, value.asString)
                is net.minecraft.nbt.IntTag -> payload.putInt(key, value.asInt)
                is net.minecraft.nbt.LongTag -> payload.putLong(key, value.asLong)
                is net.minecraft.nbt.DoubleTag -> payload.putDouble(key, value.asDouble)
                is net.minecraft.nbt.ByteTag -> payload.putBoolean(key, value.asByte != 0.toByte())
                is net.minecraft.nbt.ByteArrayTag -> payload.putByteArray(key, value.asByteArray)
                is CompoundTag -> payload.putCompound(key, compoundToNbt(value))
            }
        }
        return payload
    }
}
