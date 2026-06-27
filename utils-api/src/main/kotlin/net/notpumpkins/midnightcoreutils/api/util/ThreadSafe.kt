package net.notpumpkins.midnightcoreutils.api.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

inline fun <T> ReentrantReadWriteLock.withReadLock(action: () -> T): T {
    readLock().lock()
    try {
        return action()
    } finally {
        readLock().unlock()
    }
}

inline fun <T> ReentrantReadWriteLock.withWriteLock(action: () -> T): T {
    writeLock().lock()
    try {
        return action()
    } finally {
        writeLock().unlock()
    }
}

fun <K, V> concurrentHashMap(): MutableMap<K, V> = ConcurrentHashMap()
fun <T> concurrentList(): MutableList<T> = CopyOnWriteArrayList()
fun atomicLong(initial: Long = 0L): AtomicLong = AtomicLong(initial)
