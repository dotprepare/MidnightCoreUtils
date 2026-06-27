package net.notpumpkins.midnightcoreutils.bridge

import net.notpumpkins.midnightcoreutils.api.lifecycle.ModLifecycleHooks
import net.notpumpkins.midnightcoreutils.api.lifecycle.LifecyclePhase
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class ForgeLifecycleBridge(
    private val lifecycleHooks: ModLifecycleHooks
) {
    private val logger: Logger = LogManager.getLogger("LifecycleBridge")

    fun onCommonSetup(event: FMLCommonSetupEvent) {
        logger.info("===== Lifecycle: PRE_INIT =====")
        lifecycleHooks.executePhase(LifecyclePhase.PRE_INIT)
        logger.info("===== Lifecycle: INIT =====")
        lifecycleHooks.executePhase(LifecyclePhase.INIT)
    }

    fun onClientSetup(event: FMLClientSetupEvent) {
        logger.info("===== Lifecycle: POST_INIT (Client) =====")
        lifecycleHooks.executePhase(LifecyclePhase.POST_INIT)
    }

    fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        logger.info("===== Lifecycle: POST_INIT (Server) =====")
        lifecycleHooks.executePhase(LifecyclePhase.POST_INIT)
    }

    fun onShutdown() {
        logger.info("===== Lifecycle: SHUTDOWN =====")
        lifecycleHooks.executeShutdown()
    }

    fun getLogs(): List<ModLifecycleHooks.LifecycleLogEntry> {
        return lifecycleHooks.getLogs()
    }
}
