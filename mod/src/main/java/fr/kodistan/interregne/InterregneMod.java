package fr.kodistan.interregne;

import com.mojang.logging.LogUtils;
import fr.kodistan.interregne.colony.ColonyNetwork;
import fr.kodistan.interregne.economy.Couronnes;
import fr.kodistan.interregne.economy.EconomyNetwork;
import fr.kodistan.interregne.economy.RegionalBanks;
import fr.kodistan.interregne.politics.PoliticsNetwork;
import fr.kodistan.interregne.region.Crates;
import fr.kodistan.interregne.region.RegionNetwork;
import fr.kodistan.interregne.routes.RoutesNetwork;
import fr.kodistan.interregne.walls.WallsNetwork;
import fr.kodistan.interregne.war.WarNetwork;
import fr.kodistan.interregne.warehouse.WarehouseNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(InterregneMod.MODID)
public class InterregneMod
{
    public static final String MODID  = "interregne";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * MineColonies classes our mixins target. Loading them at startup applies every mixin right away,
     * so a hook broken by a MineColonies update fails at launch instead of mid-game.
     */
    private static final List<String> MIXIN_TARGETS = List.of(
      "com.minecolonies.core.colony.buildings.modules.EnchanterStationsModule",
      "com.minecolonies.core.colony.requestsystem.resolvers.DeliverymenRequestResolver",
      "com.minecolonies.core.colony.workorders.AbstractWorkOrder",
      "com.minecolonies.core.colony.workorders.WorkManager",
      "com.minecolonies.core.colony.workorders.WorkOrderBuilding",
      "com.minecolonies.core.entity.ai.workers.AbstractAISkeleton",
      "com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic",
      "com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract",
      "com.minecolonies.core.entity.ai.workers.AbstractEntityAIUsesFurnace",
      "com.minecolonies.core.entity.ai.workers.CitizenAI",
      "com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting",
      "com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkBeekeeper",
      "com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer",
      "com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFisherman",
      "com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder",
      "com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkShepherd",
      "com.minecolonies.core.entity.other.NewBobberEntity",
      "com.minecolonies.core.event.EventHandler",
      "com.minecolonies.core.network.messages.server.DecorationBuildRequestMessage",
      "com.minecolonies.core.network.messages.server.colony.building.BuildRequestMessage",
      "com.minecolonies.core.network.messages.server.colony.building.fields.FarmFieldUpdateSeedMessage",
      "com.minecolonies.core.placementhandlers.main.SurvivalHandler");

    public InterregneMod(final IEventBus modEventBus, final ModContainer modContainer)
    {
        // Server config: lives in each world's serverconfig folder, so test and live worlds stay separate
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        Couronnes.register(modEventBus);
        RegionalBanks.register(modEventBus);
        Crates.register(modEventBus);
        fr.kodistan.interregne.war.WarEntities.register(modEventBus);
        fr.kodistan.interregne.war.WarItems.register(modEventBus);
        fr.kodistan.interregne.war.WarSounds.register(modEventBus);
        fr.kodistan.interregne.routes.Routes.register(modEventBus);
        fr.kodistan.interregne.commerce.Commerce.register(modEventBus);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event)
    {
        // One line per feature: each feature registers its own payloads, so features don't edit the same list
        final PayloadRegistrar registrar = event.registrar("1");
        fr.kodistan.interregne.commerce.CommerceNetwork.register(registrar);
        ColonyNetwork.register(registrar);
        EconomyNetwork.register(registrar);
        PoliticsNetwork.register(registrar);
        RegionNetwork.register(registrar);
        RoutesNetwork.register(registrar);
        WallsNetwork.register(registrar);
        WarNetwork.register(registrar);
        WarehouseNetwork.register(registrar);
    }

    /**
     * Client-only mixin targets, checked the same way when running a client.
     */
    private static final List<String> CLIENT_MIXIN_TARGETS = List.of(
      "com.ldtteam.structurize.client.gui.WindowExtendedBuildTool",
      "com.minecolonies.api.blocks.AbstractBlockHut",
      "com.minecolonies.core.blocks.huts.BlockHutTownHall",
      "com.minecolonies.core.client.gui.AbstractWindowModuleBuilding",
      "com.minecolonies.core.client.gui.WindowBuildBuilding",
      "com.minecolonies.core.client.gui.WindowBuildDecoration",
      "com.minecolonies.core.client.gui.containers.WindowField");

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        final List<String> targets = new ArrayList<>(MIXIN_TARGETS);
        if (FMLEnvironment.dist.isClient())
        {
            targets.addAll(CLIENT_MIXIN_TARGETS);
        }
        for (final String target : targets)
        {
            try
            {
                Class.forName(target, false, InterregneMod.class.getClassLoader());
            }
            catch (final ClassNotFoundException e)
            {
                throw new IllegalStateException("L'Interregne: MineColonies class " + target + " is missing, check the MineColonies version", e);
            }
        }
        LOGGER.info("L'Interregne: {} MineColonies mixin targets loaded", targets.size());
    }
}
