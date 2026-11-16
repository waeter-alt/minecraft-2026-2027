package fr.kodistan.interregne.commerce;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.economy.TreasuryPayment;
import fr.kodistan.interregne.politics.Alliances;
import fr.kodistan.interregne.politics.Diplomacy;
import fr.kodistan.interregne.region.Crate;
import fr.kodistan.interregne.region.CrateStores;
import fr.kodistan.interregne.routes.RouteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The trade contracts between colonies and the caravans they send.
 * <p>
 * A seller's member makes an offer from the colony's Relais commercial; a member of the allied buyer accepts it. The
 * crates leave the seller's warehouse and the price leaves the buyer's treasury at that moment; the caravan walks the
 * road between the two colonies, on paper while no player is near and as a real animal when one is. On arrival the crates
 * go to the buyer's warehouse and the price to the seller's treasury. If the alliance breaks on the way, the crates go
 * back to the seller and the price to the buyer; if the caravan is killed, the crates are lost to whoever kills it.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Contracts extends SavedData
{
    public static final String PUPPET_TAG = "interregne_caravan";
    public static final int MAX_QUANTITY = 256;

    private static final String NAME = "interregne_contracts";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("interregne_commerce", Comparator.comparingLong(ChunkPos::toLong), 20 * 10);

    private final List<Contract> contracts = new ArrayList<>();
    private long nextId = 1;

    public static Contracts get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Contracts::new, Contracts::load, null), NAME);
    }

    public List<Contract> all()
    {
        return contracts;
    }

    @Nullable
    public Contract byId(final long id)
    {
        for (final Contract contract : contracts)
        {
            if (contract.id == id)
            {
                return contract;
            }
        }
        return null;
    }

    /**
     * Every contract a colony is part of, as seller or as buyer.
     */
    public List<Contract> of(final int colony)
    {
        final List<Contract> list = new ArrayList<>();
        for (final Contract contract : contracts)
        {
            if (contract.seller == colony || contract.buyer == colony)
            {
                list.add(contract);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ rules

    /**
     * The level of a colony's Relais commercial, 0 while none is built.
     */
    public static int relaisLevel(final IColony colony)
    {
        int best = 0;
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof BuildingRelaisCommercial)
            {
                best = Math.max(best, building.getBuildingLevel());
            }
        }
        return best;
    }

    /**
     * How many contracts a colony may have at once, offers and caravans together.
     */
    public static int capacity(final IColony colony)
    {
        return relaisLevel(colony) * 2;
    }

    /**
     * Whether two colonies may trade: allied, and a road joins them.
     */
    public static boolean mayTrade(final MinecraftServer server, final int a, final int b)
    {
        return Alliances.get(server).areAllied(a, b) && RouteRegistry.get(server).pathBetween(a, b) != null;
    }

    /**
     * @return a refusal for the player, or null once the offer is made.
     */
    @Nullable
    public String offer(@Nullable final ServerPlayer player, final MinecraftServer server, final IColony seller, final IColony buyer, final Crate crate, final int quantity,
      final long unitPrice)
    {
        if (player != null && !seller.getPermissions().isColonyMember(player))
        {
            return "Vous ne faites pas partie de cette colonie.";
        }
        if (seller.getID() == buyer.getID())
        {
            return "Choisissez une autre colonie.";
        }
        if (relaisLevel(seller) < 1)
        {
            return "Il faut un Relais commercial construit.";
        }
        if (!mayTrade(server, seller.getID(), buyer.getID()))
        {
            return "Il faut une alliance et une route entre les deux colonies.";
        }
        if (quantity < 1 || quantity > MAX_QUANTITY || unitPrice < 1)
        {
            return "Quantité ou prix invalide.";
        }
        if (of(seller.getID()).size() >= capacity(seller))
        {
            return "Trop de contrats en cours : améliorez le Relais commercial ou attendez.";
        }
        if (stock(seller, crate) < quantity)
        {
            return "L'entrepôt n'a pas assez de " + crate.displayName().toLowerCase() + ".";
        }
        contracts.add(new Contract(nextId++, seller.getID(), buyer.getID(), crate.id(), quantity, unitPrice));
        setDirty();
        Diplomacy.notifyMembers(server, buyer, seller.getName() + " vous propose un contrat : " + quantity + " × " + crate.displayName().toLowerCase()
                                                 + " pour " + quantity * unitPrice + ". Répondez-lui depuis votre Relais commercial.");
        return null;
    }

    /**
     * The buyer accepts: pay, load the caravan, send it.
     *
     * @return a refusal, or null once the caravan has left.
     */
    @Nullable
    public String accept(@Nullable final ServerPlayer player, final MinecraftServer server, final IColony buyer, final long id)
    {
        final Contract contract = byId(id);
        if (contract == null || contract.state != Contract.OFFER || contract.buyer != buyer.getID())
        {
            return "Cette offre n'existe plus.";
        }
        if (player != null && !buyer.getPermissions().isColonyMember(player))
        {
            return "Vous ne faites pas partie de cette colonie.";
        }
        final IColony seller = IColonyManager.getInstance().getColonyByWorld(contract.seller, server.overworld());
        final Crate crate = Crate.byId(contract.crate);
        if (seller == null || crate == null)
        {
            return "La colonie vendeuse n'existe plus.";
        }
        if (relaisLevel(buyer) < 1)
        {
            return "Il faut un Relais commercial construit.";
        }
        if (!mayTrade(server, contract.seller, contract.buyer))
        {
            return "Il n'y a plus d'alliance ou de route entre vos colonies.";
        }
        if (stock(seller, crate) < contract.quantity)
        {
            return seller.getName() + " n'a plus assez de caisses en réserve.";
        }
        final TreasuryPayment payment = player != null ? TreasuryPayment.pay(player, buyer, contract.total())
                                          : RegionalTreasury.get(server).spend(buyer, contract.total()) ? new TreasuryPayment(contract.total(), 0) : null;
        if (payment == null)
        {
            return "Il vous faut " + contract.total() + " (trésor régional et couronnes sur vous).";
        }
        final int taken = CrateStores.extract(racks(seller), crate, contract.quantity);
        if (taken < contract.quantity)
        {
            // Never lose crates: put back what came out, give the money back
            CrateStores.storeInWarehouse(seller, crate, taken);
            if (player != null)
            {
                payment.refund(player, buyer);
            }
            else
            {
                RegionalTreasury.get(server).credit(buyer, contract.total());
            }
            return "L'entrepôt de " + seller.getName() + " n'a pas pu fournir les caisses.";
        }
        final IBuilding warehouse = CrateStores.warehouse(seller);
        if (warehouse != null)
        {
            warehouse.markDirty();
        }
        final BlockPos[] path = RouteRegistry.get(server).pathBetween(contract.seller, contract.buyer);
        contract.path = new long[path.length];
        for (int i = 0; i < path.length; i++)
        {
            contract.path[i] = path[i].asLong();
        }
        contract.escrow = contract.total();
        contract.progress = 0;
        contract.left = contract.quantity;
        contract.state = Contract.TRANSIT;
        setDirty();
        final String what = contract.quantity + " × " + crate.displayName().toLowerCase();
        Diplomacy.notifyMembers(server, seller, "La caravane de " + what + " part vers " + buyer.getName() + ".");
        Diplomacy.notifyMembers(server, buyer, "Contrat accepté : " + what + " de " + seller.getName() + " arrivent par la route.");
        return null;
    }

    /**
     * The seller withdraws an offer, or the buyer declines it. A caravan on its way cannot be called back.
     */
    @Nullable
    public String cancel(@Nullable final ServerPlayer player, final IColony colony, final long id)
    {
        final Contract contract = byId(id);
        if (contract == null || contract.state != Contract.OFFER || (contract.seller != colony.getID() && contract.buyer != colony.getID()))
        {
            return "Cette offre n'existe plus.";
        }
        if (player != null && !colony.getPermissions().isColonyMember(player))
        {
            return "Vous ne faites pas partie de cette colonie.";
        }
        contracts.remove(contract);
        setDirty();
        return null;
    }

    public static int stockOf(final IColony colony, final Crate crate)
    {
        return stock(colony, crate);
    }

    private static int stock(final IColony colony, final Crate crate)
    {
        return CrateStores.count(racks(colony), crate);
    }

    /**
     * The warehouse racks, loading the warehouse's chunk first: a colony far from every player still trades.
     */
    @Nullable
    private static net.neoforged.neoforge.items.IItemHandler racks(final IColony colony)
    {
        loadWarehouse(colony);
        return CrateStores.warehouseRacks(colony);
    }

    /**
     * Files crates in a colony's warehouse, loading its chunk first.
     *
     * @return how many were stored.
     */
    public static int store(final IColony colony, final Crate crate, final int count)
    {
        loadWarehouse(colony);
        return CrateStores.storeInWarehouse(colony, crate, count);
    }

    private static void loadWarehouse(final IColony colony)
    {
        if (!(colony.getWorld() instanceof ServerLevel level))
        {
            return;
        }
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (building instanceof IWareHouse)
            {
                keepLoaded(level, building.getPosition());
                return;
            }
        }
    }

    private static void keepLoaded(final ServerLevel level, final BlockPos pos)
    {
        final ChunkPos chunk = new ChunkPos(pos);
        level.getChunkSource().addRegionTicket(TICKET, chunk, 1, chunk);
        level.getChunk(chunk.x, chunk.z);
    }

    // ------------------------------------------------------------------ the road

    private static final int CHECK_EVERY = 20;

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        final ServerLevel level = server.overworld();
        if (level == null)
        {
            return;
        }
        final Contracts contracts = get(server);
        if (contracts.contracts.isEmpty())
        {
            return;
        }
        final double step = Config.CARAVAN_BLOCKS_PER_SECOND.get() / 20.0;
        final boolean check = server.getTickCount() % CHECK_EVERY == 0;
        boolean changed = false;
        for (final Iterator<Contract> it = contracts.contracts.iterator(); it.hasNext(); )
        {
            final Contract contract = it.next();
            if (contract.state == Contract.OFFER)
            {
                if (check && !mayTrade(server, contract.seller, contract.buyer))
                {
                    it.remove();
                    changed = true;
                }
                continue;
            }
            if (check && !mayTrade(server, contract.seller, contract.buyer))
            {
                contracts.abort(server, level, contract);
                it.remove();
                changed = true;
                continue;
            }
            if (contract.state == Contract.TRANSIT)
            {
                contract.progress = Math.min(contract.path.length - 1, contract.progress + step);
                if (contract.progress >= contract.path.length - 1)
                {
                    contract.state = Contract.WAITING;
                    changed = true;
                }
                else if (check)
                {
                    contracts.showOrHide(server, level, contract, playerNear(level, contract.cell((int) contract.progress)));
                    changed = true;
                }
            }
            if (contract.state == Contract.WAITING && check && server.getTickCount() % (CHECK_EVERY * 5) == 0 && contracts.deliver(server, level, contract))
            {
                it.remove();
                changed = true;
            }
            else if (contract.state == Contract.TRANSIT && !check)
            {
                contracts.movePuppet(level, contract);
            }
        }
        if (changed)
        {
            contracts.setDirty();
        }
    }

    /**
     * Puts the caravan's animal on the road when a player is near, removes it when none is.
     */
    private static boolean playerNear(final ServerLevel level, final BlockPos at)
    {
        final double distance = Config.CARAVAN_SHOW_DISTANCE.get();
        for (final ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(at.getX(), player.getY(), at.getZ()) < distance * distance)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Public so the game tests can drive it without a real player.
     */
    public void showOrHide(final MinecraftServer server, final ServerLevel level, final Contract contract, final boolean near)
    {
        final BlockPos at = contract.cell((int) contract.progress);
        final Entity existing = contract.puppet == null ? null : level.getEntity(contract.puppet);
        if (!near || !level.isLoaded(at))
        {
            if (existing != null)
            {
                existing.discard();
            }
            contract.puppet = null;
            return;
        }
        if (existing != null && existing.isAlive())
        {
            return;
        }
        final IColony seller = IColonyManager.getInstance().getColonyByWorld(contract.seller, level);
        final IColony buyer = IColonyManager.getInstance().getColonyByWorld(contract.buyer, level);
        final net.minecraft.world.entity.animal.horse.TraderLlama llama = EntityType.TRADER_LLAMA.create(level);
        if (llama == null)
        {
            return;
        }
        llama.setNoAi(true);
        llama.setSilent(true);
        llama.setPersistenceRequired();
        llama.addTag(PUPPET_TAG);
        llama.setGlowingTag(true);
        llama.setCustomName(net.minecraft.network.chat.Component.literal("Caravane " + (seller == null ? "?" : seller.getName()) + " → " + (buyer == null ? "?" : buyer.getName())));
        llama.setCustomNameVisible(true);
        llama.setDespawnDelay(Integer.MAX_VALUE);
        contract.puppet = llama.getUUID();
        level.addFreshEntity(llama);
        movePuppet(level, contract);
    }

    private void movePuppet(final ServerLevel level, final Contract contract)
    {
        if (contract.puppet == null)
        {
            return;
        }
        final Entity puppet = level.getEntity(contract.puppet);
        if (puppet == null)
        {
            return;
        }
        final int index = (int) contract.progress;
        final BlockPos at = contract.cell(index);
        final BlockPos next = contract.cell(index + 1);
        final float yaw = (float) (Math.toDegrees(Math.atan2(-(next.getX() - at.getX()), next.getZ() - at.getZ())));
        final double t = contract.progress - index;
        puppet.moveTo(at.getX() + 0.5 + (next.getX() - at.getX()) * t, at.getY() + 1.0, at.getZ() + 0.5 + (next.getZ() - at.getZ()) * t, yaw, 0);
    }

    private void removePuppet(final ServerLevel level, final Contract contract)
    {
        if (contract.puppet != null)
        {
            final Entity puppet = level.getEntity(contract.puppet);
            if (puppet != null)
            {
                puppet.discard();
            }
            contract.puppet = null;
        }
    }

    /**
     * The caravan is at the buyer's door: file the crates, pay the seller.
     *
     * @return true once everything is delivered.
     */
    private boolean deliver(final MinecraftServer server, final ServerLevel level, final Contract contract)
    {
        final IColony buyer = IColonyManager.getInstance().getColonyByWorld(contract.buyer, level);
        final IColony seller = IColonyManager.getInstance().getColonyByWorld(contract.seller, level);
        final Crate crate = Crate.byId(contract.crate);
        if (buyer == null || crate == null)
        {
            return false;
        }
        loadWarehouse(buyer);
        contract.left -= CrateStores.storeInWarehouse(buyer, crate, contract.left);
        setDirty();
        if (contract.left > 0)
        {
            return false;
        }
        removePuppet(level, contract);
        if (seller != null)
        {
            RegionalTreasury.get(server).credit(seller, contract.escrow);
            Diplomacy.notifyMembers(server, seller, "La caravane est arrivée chez " + buyer.getName() + " : " + contract.escrow + " ont été versés au trésor régional.");
        }
        Diplomacy.notifyMembers(server, buyer, "La caravane de " + contract.quantity + " × " + crate.displayName().toLowerCase() + " est arrivée à l'entrepôt.");
        return true;
    }

    /**
     * The alliance or the road is gone: the crates go back to the seller, the price to the buyer.
     */
    private void abort(final MinecraftServer server, final ServerLevel level, final Contract contract)
    {
        removePuppet(level, contract);
        final IColony seller = IColonyManager.getInstance().getColonyByWorld(contract.seller, level);
        final IColony buyer = IColonyManager.getInstance().getColonyByWorld(contract.buyer, level);
        final Crate crate = Crate.byId(contract.crate);
        if (seller != null && crate != null)
        {
            loadWarehouse(seller);
            final int stored = CrateStores.storeInWarehouse(seller, crate, contract.quantity);
            if (stored < contract.quantity)
            {
                dropCrates(level, contract.cell((int) contract.progress), crate, contract.quantity - stored);
            }
        }
        if (buyer != null)
        {
            RegionalTreasury.get(server).credit(buyer, contract.escrow);
            Diplomacy.notifyMembers(server, buyer, "Le contrat est annulé (alliance ou route rompue) : " + contract.escrow + " sont rendus au trésor régional.");
        }
        if (seller != null)
        {
            Diplomacy.notifyMembers(server, seller, "La caravane a fait demi-tour : les caisses sont revenues à l'entrepôt.");
        }
    }

    private static void dropCrates(final ServerLevel level, final BlockPos at, final Crate crate, final int count)
    {
        int left = count;
        while (left > 0)
        {
            final ItemStack stack = crate.stack(Math.min(left, 16));
            left -= stack.getCount();
            level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, stack));
        }
    }

    // ------------------------------------------------------------------ pillage

    @Nullable
    private Contract ofPuppet(final Entity entity)
    {
        for (final Contract contract : contracts)
        {
            if (entity.getUUID().equals(contract.puppet))
            {
                return contract;
            }
        }
        return null;
    }

    /**
     * Only a colony at open war with the seller or the buyer may hurt a caravan.
     */
    @SubscribeEvent
    public static void onDamage(final LivingIncomingDamageEvent event)
    {
        final Entity target = event.getEntity();
        if (!target.getTags().contains(PUPPET_TAG) || !(target.level() instanceof ServerLevel level))
        {
            return;
        }
        final Contract contract = get(level.getServer()).ofPuppet(target);
        final Entity source = event.getSource().getEntity();
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return;
        }
        if (contract == null || !(source instanceof Player player) || !atWarWith(level, player, contract))
        {
            event.setCanceled(true);
        }
    }

    private static boolean atWarWith(final ServerLevel level, final Player player, final Contract contract)
    {
        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            if (colony.getID() != contract.seller && colony.getID() != contract.buyer && colony.getPermissions().isColonyMember(player)
                  && (Diplomacy.mayAssault(level.getServer(), colony.getID(), contract.seller) || Diplomacy.mayAssault(level.getServer(), colony.getID(), contract.buyer)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Killing a caravan loses the contract: the crates drop where it fell, the price goes back to the buyer.
     */
    @SubscribeEvent
    public static void onDeath(final LivingDeathEvent event)
    {
        final Entity target = event.getEntity();
        if (!target.getTags().contains(PUPPET_TAG) || !(target.level() instanceof ServerLevel level))
        {
            return;
        }
        final Contracts contracts = get(level.getServer());
        final Contract contract = contracts.ofPuppet(target);
        if (contract == null)
        {
            return;
        }
        final Crate crate = Crate.byId(contract.crate);
        if (crate != null)
        {
            dropCrates(level, target.blockPosition(), crate, contract.quantity);
        }
        final IColony buyer = IColonyManager.getInstance().getColonyByWorld(contract.buyer, level);
        final IColony seller = IColonyManager.getInstance().getColonyByWorld(contract.seller, level);
        if (buyer != null)
        {
            RegionalTreasury.get(level.getServer()).credit(buyer, contract.escrow);
            Diplomacy.notifyMembers(level.getServer(), buyer, "§cLa caravane de " + (seller == null ? "?" : seller.getName()) + " a été pillée§r : " + contract.escrow + " rendus au trésor régional.");
        }
        if (seller != null)
        {
            Diplomacy.notifyMembers(level.getServer(), seller, "§cVotre caravane vers " + (buyer == null ? "?" : buyer.getName()) + " a été pillée§r : les caisses sont perdues.");
        }
        contracts.contracts.remove(contract);
        contracts.setDirty();
    }

    /**
     * A caravan animal saved in a world without its contract (or reloaded after a restart) is just removed: the
     * road tick spawns a fresh one when a player is near.
     */
    @SubscribeEvent
    public static void onJoin(final EntityJoinLevelEvent event)
    {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity().getTags().contains(PUPPET_TAG))
        {
            final Contract contract = get(level.getServer()).ofPuppet(event.getEntity());
            if (contract == null)
            {
                event.setCanceled(true);
            }
        }
    }

    // ------------------------------------------------------------------ save

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final ListTag list = new ListTag();
        for (final Contract contract : contracts)
        {
            list.add(contract.save());
        }
        tag.put("contracts", list);
        tag.putLong("next", nextId);
        return tag;
    }

    private static Contracts load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final Contracts data = new Contracts();
        for (final Tag entry : tag.getList("contracts", Tag.TAG_COMPOUND))
        {
            data.contracts.add(Contract.load((CompoundTag) entry));
        }
        data.nextId = Math.max(1, tag.getLong("next"));
        return data;
    }
}
