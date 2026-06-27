package net.notpumpkins.midnightcoreutils.bridge

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.phys.Vec3
import java.util.UUID

fun CompoundTag.putEnum(key: String, value: Enum<*>) {
    putString(key, value.name)
}

inline fun <reified T : Enum<T>> CompoundTag.getEnum(key: String): T? {
    val name = getString(key) ?: return null
    return try {
        java.lang.Enum.valueOf(T::class.java, name)
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun CompoundTag.putUUID(key: String, uuid: UUID) {
    putLong("${key}_most", uuid.mostSignificantBits)
    putLong("${key}_least", uuid.leastSignificantBits)
}

fun CompoundTag.getUUID(key: String): UUID? {
    if (!contains("${key}_most") || !contains("${key}_least")) return null
    return UUID(getLong("${key}_most"), getLong("${key}_least"))
}

fun CompoundTag.putVec3(key: String, vec: Vec3) {
    val tag = CompoundTag()
    tag.putDouble("x", vec.x)
    tag.putDouble("y", vec.y)
    tag.putDouble("z", vec.z)
    put(key, tag)
}

fun CompoundTag.getVec3(key: String): Vec3? {
    val tag = getCompound(key) ?: return null
    if (!tag.contains("x") || !tag.contains("y") || !tag.contains("z")) return null
    return Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"))
}

fun CompoundTag.putBlockPos(key: String, pos: BlockPos) {
    val tag = CompoundTag()
    tag.putInt("x", pos.x)
    tag.putInt("y", pos.y)
    tag.putInt("z", pos.z)
    put(key, tag)
}

fun CompoundTag.getBlockPos(key: String): BlockPos? {
    val tag = getCompound(key) ?: return null
    if (!tag.contains("x") || !tag.contains("y") || !tag.contains("z")) return null
    return BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"))
}

fun CompoundTag.getSafeString(key: String): String? {
    return if (contains(key, Tag.TAG_STRING.toInt())) getString(key) else null
}

fun CompoundTag.getSafeInt(key: String): Int? {
    return if (contains(key, Tag.TAG_INT.toInt())) getInt(key) else null
}

fun CompoundTag.getSafeLong(key: String): Long? {
    return if (contains(key, Tag.TAG_LONG.toInt())) getLong(key) else null
}

fun CompoundTag.getSafeDouble(key: String): Double? {
    return if (contains(key, Tag.TAG_DOUBLE.toInt())) getDouble(key) else null
}

fun CompoundTag.getSafeBoolean(key: String): Boolean? {
    return if (contains(key, Tag.TAG_BYTE.toInt())) getBoolean(key) else null
}

fun CompoundTag.deepMerge(source: CompoundTag): CompoundTag {
    val result = copy() as CompoundTag
    for (key in source.allKeys) {
        val sourceValue = source.get(key)
        if (sourceValue is CompoundTag && result.get(key) is CompoundTag) {
            val merged = result.getCompound(key).deepMerge(sourceValue)
            result.put(key, merged)
        } else {
            result.put(key, sourceValue!!)
        }
    }
    return result
}

fun CompoundTag.deepMergeInPlace(source: CompoundTag) {
    for (key in source.allKeys) {
        val sourceValue = source.get(key)
        if (sourceValue is CompoundTag && get(key) is CompoundTag) {
            getCompound(key).deepMergeInPlace(sourceValue)
        } else {
            put(key, sourceValue!!)
        }
    }
}

fun CompoundTag.diff(other: CompoundTag): Set<String> {
    val changed = mutableSetOf<String>()
    val allKeys = (allKeys + other.allKeys).toSet()
    for (key in allKeys) {
        val thisHas = contains(key)
        val otherHas = other.contains(key)
        if (thisHas != otherHas) {
            changed.add(key)
        } else if (thisHas) {
            val thisTag = get(key)
            val otherTag = other.get(key)
            if (thisTag is CompoundTag && otherTag is CompoundTag) {
                val subDiffs = thisTag.diff(otherTag)
                subDiffs.forEach { changed.add("$key.$it") }
            } else if (thisTag != otherTag) {
                changed.add(key)
            }
        }
    }
    return changed
}

fun CompoundTag.toReadableString(indent: Int = 0): String {
    val sb = StringBuilder()
    val prefix = "  ".repeat(indent)
    sb.appendLine("{")
    allKeys.sorted().forEach { key ->
        val value = get(key)
        when (value) {
            is CompoundTag -> sb.append("$prefix  \"$key\": ${value.toReadableString(indent + 1)}")
            is ListTag -> {
                sb.appendLine("$prefix  \"$key\": [")
                for (i in 0 until value.size) {
                    val element = value[i]
                    if (element is CompoundTag) {
                        sb.append("$prefix    ${element.toReadableString(indent + 2)}")
                    } else {
                        sb.appendLine("$prefix    $element")
                    }
                }
                sb.appendLine("$prefix  ]")
            }
            else -> sb.appendLine("$prefix  \"$key\": $value")
        }
    }
    sb.append("$prefix}")
    return sb.toString()
}
