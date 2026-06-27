package net.notpumpkins.midnightcoreutils.api.error

import net.notpumpkins.midnightcoreutils.api.event.ModEventBus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class ErrorBoundary(
    private val eventBus: ModEventBus? = null,
    private val logger: ((String, Throwable) -> Unit)? = null
) {
    data class ModErrorState(
        val modId: String,
        val feature: String,
        val error: Throwable,
        val timestamp: Long,
        val disabled: Boolean
    )

    private val modErrors = ConcurrentHashMap<String, MutableList<ModErrorState>>()
    private val disabledFeatures = ConcurrentHashMap<String, MutableSet<String>>()
    private val isShutdown = AtomicBoolean(false)

    fun <T> execute(
        modId: String,
        feature: String,
        block: () -> T
    ): T? {
        if (isShutdown.get()) return null
        val featureKey = "$modId:$feature"
        if (disabledFeatures[modId]?.contains(featureKey) == true) {
            return null
        }

        return try {
            block.invoke()
        } catch (e: Exception) {
            handleFailure(modId, feature, e)
            null
        } catch (e: Error) {
            handleFailure(modId, feature, e)
            null
        }
    }

    fun <T> executeOrThrow(
        modId: String,
        feature: String,
        block: () -> T
    ): T {
        if (isShutdown.get()) {
            throw IllegalStateException("ErrorBoundary is shut down")
        }

        return try {
            block.invoke()
        } catch (e: Exception) {
            handleFailure(modId, feature, e)
            throw e
        } catch (e: Error) {
            handleFailure(modId, feature, e)
            throw e
        }
    }

    private fun handleFailure(modId: String, feature: String, error: Throwable) {
        val featureKey = "$modId:$feature"
        val state = ModErrorState(modId, feature, error, System.currentTimeMillis(), true)

        modErrors.getOrPut(modId) { mutableListOf() }.add(state)
        disabledFeatures.getOrPut(modId) { mutableSetOf() }.add(featureKey)

        logger?.invoke("[$modId] Feature '$feature' crashed: ${error.message}", error)

        eventBus?.post(
            ServiceUnavailableEvent(modId, error::class.java, error.message ?: "Unknown error")
        )
    }

    fun isFeatureDisabled(modId: String, feature: String): Boolean {
        return disabledFeatures[modId]?.contains("$modId:$feature") == true
    }

    fun reenableFeature(modId: String, feature: String) {
        disabledFeatures[modId]?.remove("$modId:$feature")
    }

    fun getErrors(modId: String): List<ModErrorState> {
        return modErrors[modId]?.toList() ?: emptyList()
    }

    fun getAllErrors(): List<ModErrorState> {
        return modErrors.values.flatten()
    }

    fun clearMod(modId: String) {
        modErrors.remove(modId)
        disabledFeatures.remove(modId)
    }

    fun shutdown() {
        isShutdown.set(true)
        modErrors.clear()
        disabledFeatures.clear()
    }

    fun isOperational(): Boolean = !isShutdown.get()
}
