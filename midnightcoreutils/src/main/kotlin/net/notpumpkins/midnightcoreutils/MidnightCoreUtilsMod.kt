package net.notpumpkins.midnightcoreutils

import net.notpumpkins.midnightcoreutils.bridge.CoreSystems
import net.notpumpkins.midnightcoreutils.bridge.ForgeLifecycleBridge
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import org.apache.logging.log4j.LogManager
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

@Mod(MidnightCoreUtilsMod.ID)
@EventBusSubscriber(modid = MidnightCoreUtilsMod.ID)
object MidnightCoreUtilsMod {
    const val ID = "midnightcoreutils"

    val LOGGER = LogManager.getLogger(ID)

    init {
        CoreSystems.initialize()

        val lifecycleBridge = ForgeLifecycleBridge(CoreSystems.lifecycleHooks)

        MOD_BUS.addListener { event: FMLCommonSetupEvent ->
            lifecycleBridge.onCommonSetup(event)
        }

        runForDist(
            clientTarget = {
                MOD_BUS.addListener { event: FMLClientSetupEvent ->
                    lifecycleBridge.onClientSetup(event)
                }
                NeoForge.EVENT_BUS.addListener { event: LevelTickEvent ->
                    if (event.level.isClientSide) {
                        CoreSystems.onClientTick()
                    }
                }
                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener { event: FMLDedicatedServerSetupEvent ->
                    lifecycleBridge.onServerSetup(event)
                }
                NeoForge.EVENT_BUS.addListener { _: ServerTickEvent.Pre ->
                    CoreSystems.onServerTick()
                }
                "server"
            }
        )

        NeoForge.EVENT_BUS.register(this)
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.info("MidnightCoreUtils initialized")
    }
}
