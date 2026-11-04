package fr.kodistan.interregne.colony;

import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.core.colony.workorders.AbstractWorkOrder;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.colony.workorders.WorkOrderDecoration;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.economy.TreasuryPayment;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.production.HutProduction;
import fr.kodistan.interregne.production.Huts;
import fr.kodistan.interregne.production.WaterSeal;
import fr.kodistan.interregne.region.Crate;
import fr.kodistan.interregne.region.CrateRequirements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;


import static com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE.TAG_BLUEPRINTDATA;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_NAME;

/**
 * Replaces the builder workflow: a build, upgrade, repair or decoration order is paid by the requesting player
 * and placed directly, without ever entering the colony's work manager.
 */
public final class InstantBuilder
{
    private InstantBuilder()
    {
    }

    /**
     * Try to take over a new work order.
     *
     * @return true if the order was handled here and must not reach the builders.
     */
    public static boolean tryTakeOver(final IColony colony, final IServerWorkOrder order)
    {
        if (!Config.INSTANT_BUILD.get()
              || !(order instanceof WorkOrderBuilding || order instanceof WorkOrderDecoration)
              || !(colony.getWorld() instanceof ServerLevel level))
        {
            return false;
        }

        final ServerPlayer player = BuildContext.requester();
        if (order instanceof WorkOrderBuilding && order.getWorkOrderType() != WorkOrderType.REPAIR && order.getWorkOrderType() != WorkOrderType.REMOVE)
        {
            final IBuilding building = colony.getBuildingManager().getBuilding(order.getLocation());
            final ITownHall townHall = colony.getBuildingManager().getTownHall();
            final String refusal = TownHallCap.refusal(building instanceof ITownHall, order.getTargetLevel(), townHall == null ? 0 : townHall.getBuildingLevel());
            if (refusal != null)
            {
                if (player != null)
                {
                    player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
                }
                return true;
            }
        }
        if (player != null && FarmChoice.ask(player, colony, order))
        {
            // Resumed by proceed() once the player has picked the farm's crop
            return true;
        }
        return proceed(colony, order, player);
    }

    /**
     * Pay for and start a work order.
     */
    public static boolean proceed(final IColony colony, final IServerWorkOrder order, @Nullable final ServerPlayer player)
    {
        final ServerLevel level = (ServerLevel) colony.getWorld();
        if (order instanceof WorkOrderDecoration)
        {
            // A decoration's price depends on its size, known once its blueprint is loaded
            takeOverDecoration(colony, level, order, player);
            return true;
        }
        // Regional crates (from level 2): checked before any Couronne is charged
        final CrateRequirements.Plan crates = CrateRequirements.plan(colony, order, player);
        if (crates.refused())
        {
            return true;
        }
        final int cost = player == null || player.isCreative() ? 0 : costOf(order);
        // Regional treasury: the colony's treasury pays first, the coins carried make up the rest
        final TreasuryPayment payment = TreasuryPayment.pay(player, colony, cost);
        if (payment == null)
        {
            player.sendSystemMessage(Component.literal("Il te faut " + Wallet.couronnes(cost, null) + " pour ce chantier (trésor régional : "
                                                         + Wallet.format(RegionalTreasury.get(level.getServer()).balance(colony)) + ", sur toi : "
                                                         + Wallet.format(Wallet.value(purse(player))) + ").")
                                       .withStyle(ChatFormatting.RED));
            return true;
        }
        crates.take();
        if (player != null && payment.fromTreasury() > 0)
        {
            player.sendSystemMessage(Component.literal("Payé : " + payment.describe() + ".").withStyle(ChatFormatting.GRAY));
        }

        final IBuilding building = order instanceof WorkOrderBuilding ? colony.getBuildingManager().getBuilding(order.getLocation()) : null;
        order.loadBlueprint(level, blueprint -> level.getServer().execute(() -> {
            if (blueprint == null)
            {
                InterregneMod.LOGGER.warn("Instant build: no blueprint for {} at {}", order.getStructurePath(), order.getLocation());
                if (player != null)
                {
                    payment.refund(player, colony);
                    crates.refund();
                    player.sendSystemMessage(Component.literal("Plan introuvable, chantier annulé et remboursé.").withStyle(ChatFormatting.RED));
                }
                return;
            }

            if (order.getWorkOrderType() == WorkOrderType.REMOVE)
            {
                ConstructionManager.start(new DemolitionSite(level, colony, order, building, blueprint));
                if (player != null)
                {
                    player.sendSystemMessage(Component.literal("Démolition lancée : ")
                                               .append(order.getDisplayName())
                                               .append(" (durée " + BuildCosts.formatSeconds(DemolitionSite.durationSeconds()) + ")")
                                               .withStyle(ChatFormatting.GOLD));
                }
                return;
            }

            ConstructionManager.start(new ConstructionSite(level, colony, order, building, blueprint, player));
            if (player != null)
            {
                player.sendSystemMessage(Component.literal("Chantier lancé : ")
                                           .append(order.getDisplayName())
                                           .append(" (durée " + BuildCosts.formatSeconds(ConstructionSite.durationSeconds(order)) + ")")
                                           .withStyle(ChatFormatting.GOLD));
            }
        }));
        return true;
    }

    /**
     * Finish an order once its blueprint is fully placed, mirroring what a builder does on completion.
     */
    static void complete(final IColony colony, final IServerWorkOrder order, @Nullable final IBuilding building, final Blueprint blueprint)
    {
        final ServerLevel level = (ServerLevel) colony.getWorld();
        syncBlueprintData(level, order.getLocation(), blueprint);

        if (order instanceof WorkOrderBuilding workOrderBuilding && building != null)
        {
            // Levels normally come from the hut's schematic data; fall back like the builder does if it was missing
            if (building.getBuildingLevel() < order.getTargetLevel())
            {
                building.onUpgradeComplete(order.getTargetLevel());
                building.setBuildingLevel(order.getTargetLevel());
            }
            IMinecoloniesAPI.getInstance().getEventBus().post(new BuildingConstructionModEvent(building, workOrderBuilding));
            building.markDirty();
        }
        if (order instanceof AbstractWorkOrder abstractOrder)
        {
            abstractOrder.onCompleted(colony, null);
        }
        if (building != null && order instanceof WorkOrderBuilding && order.getWorkOrderType() != WorkOrderType.REPAIR)
        {
            try
            {
                if (Huts.FISHERMAN.equals(Huts.id(building)))
                {
                    WaterSeal.seal(level, building);
                }
                HutProduction.afterConstruction(level, colony, building);
            }
            catch (final RuntimeException e)
            {
                InterregneMod.LOGGER.error("Setting up {} after construction failed", building.getPosition(), e);
            }
        }

        final BlockPos pos = order.getLocation();
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 40, 2.0, 1.5, 2.0, 0.1);
        colony.getMessagePlayerEntities().forEach(p -> p.sendSystemMessage(
          Component.literal("Chantier terminé : ").append(order.getDisplayName()).withStyle(ChatFormatting.GREEN)));
    }

    /**
     * Finish a demolition, mirroring what a builder does once a building is taken down.
     */
    static void completeDemolition(final IColony colony, final IServerWorkOrder order, @Nullable final IBuilding building)
    {
        if (building != null)
        {
            building.setDeconstructed();
            if (order instanceof WorkOrderBuilding workOrderBuilding)
            {
                IMinecoloniesAPI.getInstance().getEventBus().post(new BuildingConstructionModEvent(building, workOrderBuilding));
            }
            building.markDirty();
        }
        if (order instanceof AbstractWorkOrder abstractOrder)
        {
            abstractOrder.onCompleted(colony, null);
        }
    }

    /**
     * Decorations pay by size: one Couronne per so many blocks (at least the flat decoration cost), and regional
     * crates (wood and stone in turn) for the big ones.
     */
    private static void takeOverDecoration(final IColony colony, final ServerLevel level, final IServerWorkOrder order, @Nullable final ServerPlayer player)
    {
        order.loadBlueprint(level, blueprint -> level.getServer().execute(() -> {
            if (blueprint == null)
            {
                InterregneMod.LOGGER.warn("Instant build: no blueprint for {} at {}", order.getStructurePath(), order.getLocation());
                if (player != null)
                {
                    player.sendSystemMessage(Component.literal("Plan introuvable, chantier annulé.").withStyle(ChatFormatting.RED));
                }
                return;
            }
            final long blocks = blueprint.getBlockInfoAsList().stream().filter(info -> !info.getState().isAir()).count();
            final boolean free = player == null || player.isCreative();
            final int cost = free ? 0 : BuildCosts.forDecoration(blocks);
            final CrateRequirements.Plan crates = free ? CrateRequirements.Plan.NONE : CrateRequirements.plan(colony, player, CrateRequirements.forDecoration(blocks));
            if (crates.refused())
            {
                return;
            }
            final TreasuryPayment payment = TreasuryPayment.pay(player, colony, cost);
            if (payment == null)
            {
                player.sendSystemMessage(Component.literal("Il te faut " + Wallet.couronnes(cost, null) + " pour cette décoration de " + blocks + " blocs (trésor régional : "
                                                             + Wallet.format(RegionalTreasury.get(level.getServer()).balance(colony)) + ", sur toi : "
                                                             + Wallet.format(Wallet.value(purse(player))) + ").")
                                           .withStyle(ChatFormatting.RED));
                return;
            }
            crates.take();
            if (player != null && cost > 0)
            {
                player.sendSystemMessage(Component.literal("Décoration de " + blocks + " blocs, payé : " + payment.describe() + ".").withStyle(ChatFormatting.GRAY));
            }
            ConstructionManager.start(new ConstructionSite(level, colony, order, null, blueprint, player));
            if (player != null)
            {
                player.sendSystemMessage(Component.literal("Chantier lancé : ")
                                           .append(order.getDisplayName())
                                           .append(" (durée " + BuildCosts.formatSeconds(ConstructionSite.durationSeconds(order)) + ")")
                                           .withStyle(ChatFormatting.GOLD));
            }
        }));
    }

    private static int costOf(final IServerWorkOrder order)
    {
        if (order.getWorkOrderType() == WorkOrderType.REMOVE)
        {
            return Config.DEMOLITION_COST.get();
        }
        if (order instanceof WorkOrderDecoration)
        {
            return BuildCosts.forDecoration();
        }
        if (order.getWorkOrderType() == WorkOrderType.REPAIR)
        {
            return BuildCosts.forRepair();
        }
        return BuildCosts.forLevel(order.getTargetLevel());
    }

    /**
     * Push the blueprint data (schematic name and pack) into every placed hut and decoration controller,
     * which is how MineColonies learns the new building level.
     */
    private static void syncBlueprintData(final ServerLevel level, final BlockPos anchor, final Blueprint blueprint)
    {
        final CompoundTag[][][] tileEntityData = blueprint.getTileEntities();
        for (short x = 0; x < blueprint.getSizeX(); x++)
        {
            for (short y = 0; y < blueprint.getSizeY(); y++)
            {
                for (short z = 0; z < blueprint.getSizeZ(); z++)
                {
                    final CompoundTag compound = tileEntityData[y][z][x];
                    if (compound == null || !compound.contains(TAG_BLUEPRINTDATA))
                    {
                        continue;
                    }
                    final BlockPos tePos = anchor.subtract(blueprint.getPrimaryBlockOffset()).offset(x, y, z);
                    final BlockEntity te = level.getBlockEntity(tePos);
                    if (te instanceof IBlueprintDataProviderBE provider)
                    {
                        final CompoundTag tagData = compound.getCompound(TAG_BLUEPRINTDATA);
                        final String location = StructurePacks.getStructurePack(blueprint.getPackName())
                                                  .getSubPath(Utils.resolvePath(blueprint.getFilePath(), tagData.getString(TAG_NAME)));
                        tagData.putString(TAG_NAME, location);
                        tagData.putString(NbtTagConstants.TAG_PACK, blueprint.getPackName());
                        try
                        {
                            provider.readSchematicDataFromNBT(compound);
                        }
                        catch (final Exception e)
                        {
                            InterregneMod.LOGGER.warn("Instant build: broken blueprint data at {}", tePos, e);
                        }
                        level.getChunkSource().blockChanged(tePos);
                        te.setChanged();
                    }
                }
            }
        }
    }

    private static IItemHandler purse(final ServerPlayer player)
    {
        return new PlayerMainInvWrapper(player.getInventory());
    }
}
