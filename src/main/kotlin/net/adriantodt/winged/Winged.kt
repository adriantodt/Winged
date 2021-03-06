package net.adriantodt.winged

import com.mojang.serialization.Lifecycle
import dev.onyxstudios.cca.api.v3.component.ComponentKey
import dev.onyxstudios.cca.api.v3.component.ComponentRegistryV3
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer
import io.github.ladysnake.pal.AbilitySource
import io.github.ladysnake.pal.Pal
import io.github.ladysnake.pal.VanillaAbilities
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigHolder
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer
import nerdhub.cardinal.components.api.util.RespawnCopyStrategy
import net.adriantodt.fallflyinglib.FallFlyingLib
import net.adriantodt.fallflyinglib.event.PreFallFlyingCallback
import net.adriantodt.winged.block.WingBenchBlock
import net.adriantodt.winged.command.WingedCommand
import net.adriantodt.winged.data.Wing
import net.adriantodt.winged.data.WingedConfig
import net.adriantodt.winged.data.components.WingedPlayerComponent
import net.adriantodt.winged.data.components.impl.DefaultPlayerComponent
import net.adriantodt.winged.recipe.WingcraftingRecipe
import net.adriantodt.winged.screen.WingBenchScreenHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry
import net.minecraft.block.Blocks
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.util.registry.DefaultedRegistry
import net.minecraft.util.registry.Registry
import net.minecraft.util.registry.RegistryKey
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Suppress("MemberVisibilityCanBePrivate")
object Winged : ModInitializer, EntityComponentInitializer {
    val logger: Logger = LogManager.getLogger(Winged.javaClass)

    val configHolder: ConfigHolder<WingedConfig> =
        AutoConfig.register(WingedConfig::class.java, ::JanksonConfigSerializer)

    val wingRegistry = DefaultedRegistry<Wing>(
        "minecraft:elytra",
        RegistryKey.ofRegistry(identifier("wing")),
        Lifecycle.stable()
    )

    val playerComponentType: ComponentKey<WingedPlayerComponent> =
        ComponentRegistryV3.INSTANCE.getOrCreate(identifier("player_data"), WingedPlayerComponent::class.java)

    val wingSource: AbilitySource = Pal.getAbilitySource(identifier("wing"))

    val wingbenchType = ScreenHandlerRegistry.registerSimple(WingBenchScreenHandler.ID) { syncId, inv ->
        WingBenchScreenHandler(syncId, inv)
    }!!

    val mainGroup: ItemGroup = FabricItemGroupBuilder.create(identifier("main"))
        .icon { ItemStack(WingedLoreItems.coreOfFlight) }
        .build()

    val showcaseGroup: ItemGroup = FabricItemGroupBuilder.create(identifier("showcase"))
        .icon { ItemStack(WingItems.elytra.standard) }
        .build()

    val wingBenchBlock = WingBenchBlock(FabricBlockSettings.copyOf(Blocks.END_STONE))

    override fun onInitialize() {
        PreFallFlyingCallback.EVENT.register(this::handleWingsAndCreativeFlight)
        WingedLoreItems.register()
        WingedUtilityItems.register()
        WingItems.register()
        WingedLootTables.register(configHolder.config)
        WingedCommand.init()

        Registry.register(Registry.RECIPE_TYPE, WingcraftingRecipe.ID, WingcraftingRecipe.TYPE)
        Registry.register(Registry.RECIPE_SERIALIZER, WingcraftingRecipe.ID, WingcraftingRecipe.SERIALIZER)

        Registry.register(Registry.BLOCK, identifier("wingbench"), wingBenchBlock)
        Registry.register(Registry.ITEM, identifier("wingbench"), BlockItem(wingBenchBlock, itemSettings()))
    }

    override fun registerEntityComponentFactories(registry: EntityComponentFactoryRegistry) {
        registry.registerForPlayers(
            playerComponentType,
            ::DefaultPlayerComponent,
            if (configHolder.config.keepWingsAfterDeath) RespawnCopyStrategy.ALWAYS_COPY
            else RespawnCopyStrategy.INVENTORY
        )
    }

    private fun handleWingsAndCreativeFlight(player: PlayerEntity) {
        if (player.world.isClient) return
        updatePalAndFfl(player, playerComponentType.getNullable(player) ?: return)
    }

    fun updatePalAndFfl(player: PlayerEntity, component: WingedPlayerComponent) {
        if (player.world.isClient) return
        val wing = component.wing

        val fallFlyingTracker = FallFlyingLib.ABILITY.getTracker(player)
        val allowFlyingTracker = VanillaAbilities.ALLOW_FLYING.getTracker(player)

        if (wing == null) {
            fallFlyingTracker.removeSource(wingSource)
            allowFlyingTracker.removeSource(wingSource)
        } else {
            fallFlyingTracker.addSource(wingSource)

            if (component.creativeFlight) {
                allowFlyingTracker.addSource(wingSource)
            } else {
                allowFlyingTracker.removeSource(wingSource)
            }
        }
    }
}
