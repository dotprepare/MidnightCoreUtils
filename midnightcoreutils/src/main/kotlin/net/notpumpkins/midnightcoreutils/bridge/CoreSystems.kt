package net.notpumpkins.midnightcoreutils.bridge

import net.notpumpkins.midnightcoreutils.api.config.SharedConfig
import net.notpumpkins.midnightcoreutils.api.debug.DebugInspector
import net.notpumpkins.midnightcoreutils.api.error.ErrorBoundary
import net.notpumpkins.midnightcoreutils.api.lifecycle.ModLifecycleHooks
import net.notpumpkins.midnightcoreutils.api.registry.SharedRegistry
import net.notpumpkins.midnightcoreutils.api.scheduler.TickScheduler
import net.notpumpkins.midnightcoreutils.api.service.ServiceLocator
import net.notpumpkins.midnightcoreutils.api.scheduler.TickPhase

object CoreSystems {
    val eventBridge = ForgeEventBridge()
    val serviceLocator = ServiceLocator()
    val tickScheduler = TickScheduler()
    val networkBridge = ForgeNetworkBridge()
    val sharedConfig = SharedConfig()
    val sharedRegistry = SharedRegistry()
    val lifecycleHooks = ModLifecycleHooks()
    val errorBoundary = ErrorBoundary(
        eventBus = eventBridge.getApiEventBus(),
        logger = { msg, err ->
            org.apache.logging.log4j.LogManager.getLogger("ErrorBoundary").error(msg, err)
        }
    )
    val debugInspector = DebugInspector(enabled = false)

    fun initialize() {
        debugInspector.registerSystems(
            eventBus = eventBridge.getApiEventBus(),
            serviceLocator = serviceLocator,
            tickScheduler = tickScheduler,
            packetSync = networkBridge.getPacketSync(),
            sharedRegistry = sharedRegistry,
            lifecycleHooks = lifecycleHooks,
            errorBoundary = errorBoundary,
            sharedConfig = sharedConfig
        )
    }

    fun onServerTick() {
        tickScheduler.tick(TickPhase.SERVER)
        networkBridge.getPacketSync().processOutgoing()
    }

    fun onClientTick() {
        tickScheduler.tick(TickPhase.CLIENT)
    }

    fun onModUnload(modId: String) {
        eventBridge.unsubscribeAll(modId)
        serviceLocator.removeServices(modId)
        tickScheduler.onModUnload(modId)
        sharedRegistry.removeMod(modId)
        sharedConfig.removeNamespace(modId)
        lifecycleHooks.removeHooks(modId)
        errorBoundary.clearMod(modId)
    }

    fun shutdown() {
        tickScheduler.shutdown()
        networkBridge.getPacketSync().clearAll()
        lifecycleHooks.clear()
        errorBoundary.shutdown()
        eventBridge.clear()
        serviceLocator.clear()
        sharedRegistry.clear()
        sharedConfig.clear()
        debugInspector.clearSamples()
    }
}
