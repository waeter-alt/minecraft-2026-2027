package fr.kodistan.interregne;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import fr.kodistan.interregne.colony.BuildNow;
import fr.kodistan.interregne.client.ClientPayloads;
import fr.kodistan.interregne.economy.BankCounterBlock;
import fr.kodistan.interregne.economy.Couronnes;
import fr.kodistan.interregne.economy.RegionalBankBlock;
import fr.kodistan.interregne.economy.RegionalBanks;
import fr.kodistan.interregne.network.BankActionPayload;
import fr.kodistan.interregne.network.InfluenceSyncPayload;
import fr.kodistan.interregne.network.OpenThronePayload;
import fr.kodistan.interregne.network.ThroneActionPayload;
import fr.kodistan.interregne.politics.ThroneBlock;
import fr.kodistan.interregne.network.OpenBankPayload;
import fr.kodistan.interregne.network.OpenComptoirPayload;
import fr.kodistan.interregne.network.OpenMarketPayload;
import fr.kodistan.interregne.network.OpenRegionalBankPayload;
import fr.kodistan.interregne.network.PlacementModePayload;
import fr.kodistan.interregne.network.RegionActionPayload;
import fr.kodistan.interregne.network.RegionalBankActionPayload;
import fr.kodistan.interregne.network.TreasurySyncPayload;
import fr.kodistan.interregne.network.TerminalDepositPayload;
import fr.kodistan.interregne.network.TerminalWithdrawPayload;
import fr.kodistan.interregne.region.Crates;
import fr.kodistan.interregne.region.Regions;
import fr.kodistan.interregne.warehouse.Terminal;
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
      "com.minecolonies.core.colony.workorders.WorkManager",
      "com.minecolonies.core.colony.workorders.AbstractWorkOrder",
      "com.minecolonies.core.colony.workorders.WorkOrderBuilding",
      "com.minecolonies.core.network.messages.server.colony.building.BuildRequestMessage",
      "com.minecolonies.core.network.messages.server.DecorationBuildRequestMessage",
      "com.minecolonies.core.placementhandlers.main.SurvivalHandler",
      "com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic",
      "com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract",
      "com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting",
      "com.minecolonies.core.colony.requestsystem.resolvers.DeliverymenRequestResolver",
      "com.minecolonies.core.entity.other.NewBobberEntity",
      "com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFisherman",
      "com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkBeekeeper",
      "com.minecolonies.core.entity.ai.workers.production.herders.EntityAIWorkShepherd",
      "com.minecolonies.core.entity.ai.workers.CitizenAI",
      "com.minecolonies.core.entity.ai.workers.AbstractAISkeleton",
      "com.minecolonies.core.event.EventHandler");

    public InterregneMod(final IEventBus modEventBus, final ModContainer modContainer)
    {
        // Server config: lives in each world's serverconfig folder, so test and live worlds stay separate
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        Couronnes.register(modEventBus);
        RegionalBanks.register(modEventBus);
        Crates.register(modEventBus);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event)
    {
        event.registrar("1")
          .playToServer(ThroneActionPayload.TYPE, ThroneActionPayload.STREAM_CODEC, ThroneBlock::onAction)
          .playToClient(OpenThronePayload.TYPE, OpenThronePayload.STREAM_CODEC, (payload, context) -> {
              if (FMLEnvironment.dist.isClient())
              {
                  ClientPayloads.openThrone(payload);
              }
          })
          .playToClient(InfluenceSyncPayload.TYPE, InfluenceSyncPayload.STREAM_CODEC, (payload, context) -> {
              if (FMLEnvironment.dist.isClient())
              {
                  ClientPayloads.syncInfluence(payload);
              }
          })
          .playToServer(PlacementModePayload.TYPE, PlacementModePayload.STREAM_CODEC, BuildNow::onPayload)
          .playToServer(TerminalWithdrawPayload.TYPE, TerminalWithdrawPayload.STREAM_CODEC, Terminal::onWithdraw)
          .playToServer(TerminalDepositPayload.TYPE, TerminalDepositPayload.STREAM_CODEC, Terminal::onDeposit)
          .playToServer(BankActionPayload.TYPE, BankActionPayload.STREAM_CODEC, BankCounterBlock::onAction)
          .playToClient(OpenBankPayload.TYPE, OpenBankPayload.STREAM_CODEC, (payload, context) -> {
              if (FMLEnvironment.dist.isClient())
              {
                  ClientPayloads.openBank(payload);
              }
          })
          .playToServer(RegionalBankActionPayload.TYPE, RegionalBankActionPayload.STREAM_CODEC, RegionalBankBlock::onAction)
          .playToClient(OpenRegionalBankPayload.TYPE, OpenRegionalBankPayload.STREAM_CODEC, (payload, context) -> {
              if (FMLEnvironment.dist.isClient())
              {
                  ClientPayloads.openRegionalBank(payload);
              }
          })
          .playToClient(TreasurySyncPayload.TYPE, TreasurySyncPayload.STREAM_CODEC, (payload, context) -> {
              if (FMLEnvironment.dist.isClient())
              {
                  ClientPayloads.syncTreasury(payload);
              }
          })
          .playToServer(RegionActionPayload.TYPE, RegionActionPayload.STREAM_CODEC, Regions::onAction)
          .playToClient(OpenComptoirPayload.TYPE, OpenComptoirPayload.STREAM_CODEC, (payload, context) -> {
              if (FMLEnvironment.dist.isClient())
              {
                  ClientPayloads.openComptoir(payload);
              }
          })
          .playToClient(OpenMarketPayload.TYPE, OpenMarketPayload.STREAM_CODEC, (payload, context) -> {
              if (FMLEnvironment.dist.isClient())
              {
                  ClientPayloads.openMarket(payload);
              }
          });
    }

    /**
     * Client-only mixin targets, checked the same way when running a client.
     */
    private static final List<String> CLIENT_MIXIN_TARGETS = List.of(
      "com.minecolonies.core.client.gui.WindowBuildBuilding",
      "com.ldtteam.structurize.client.gui.WindowExtendedBuildTool",
      "com.minecolonies.core.client.gui.AbstractWindowModuleBuilding");

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
