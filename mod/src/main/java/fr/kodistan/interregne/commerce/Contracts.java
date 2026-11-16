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
        return offer(player, server, seller, buyer, crate, quantity, unitPrice, 0);
    }

    /**
     * @param escort the escort tier the seller buys for this contract, 0 for none; paid now, refunded if the offer is withdrawn
     * @return a refusal for the player, or null once the offer is made.
     */
    @Nullable
    public String offer(@Nullable final ServerPlayer player, final MinecraftServer server, final IColony seller, final IColony buyer, final Crate crate, final int quantity,
      final long unitPrice, final int escort)
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
        if (escort < 0 || escort > Escort.maxTier(relaisLevel(seller)))
        {
            return "Votre Relais commercial ne permet pas cette escorte (palier " + Escort.maxTier(relaisLevel(seller)) + " au plus).";
        }
        final Contract contract = new Contract(nextId++, seller.getID(), buyer.getID(), crate.id(), quantity, unitPrice);
        if (escort > 0)
        {
            final long cost = Escort.cost(escort);
            final TreasuryPayment payment = player != null ? TreasuryPayment.pay(player, seller, cost)
                                              : RegionalTreasury.get(server).spend(seller, cost) ? new TreasuryPayment(cost, 0) : null;
            if (payment == null)
            {
                nextId--;
                return "L'escorte de palier " + escort + " coûte " + cost + " (trésor régional et couronnes sur vous).";
            }
            contract.escort = escort;
            contract.escortFromTreasury = payment.fromTreasury();
            contract.escortFromPurse = payment.fromPurse();
        }
        contracts.add(contract);
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
        contract.swordsmenLeft = Escort.swordsmen(contract.escort);
        contract.archersLeft = Escort.archers(contract.escort);
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
        refundEscort(player, colony, contract, colony.getWorld() instanceof ServerLevel level ? level.getServer() : null);
        setDirty();
        return null;
    }

    /**
     * An offer that never left gives its escort money back: to the seller's purse and treasury as they paid it when the
     * seller himself withdraws, to the seller's treasury in every other case (the buyer declines, the alliance breaks).
     */
    private static void refundEscort(@Nullable final ServerPlayer player, @Nullable final IColony answering, final Contract contract, @Nullable final MinecraftServer server)
    {
        if (contract.escortPaid() <= 0 || server == null)
        {
            return;
        }
        final IColony seller = IColonyManager.getInstance().getColonyByWorld(contract.seller, server.overworld());
        if (seller == null)
        {
            return;
        }
        if (player != null && answering != null && answering.getID() == contract.seller)
        {
            new TreasuryPayment(contract.escortFromTreasury, contract.escortFromPurse).refund(player, seller);
        }
        else
        {
            RegionalTreasury.get(server).credit(seller, contract.escortPaid());
        }
        contract.escortFromTreasury = 0;
        contract.escortFromPurse = 0;
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

    /** The soldiers of an escort; PUPPET_TAG marks the mule and its driver. */
    public static final String ESCORT_TAG = "interregne_escort";

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
                    refundEscort(null, null, contract, server);
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
                contract.engaged = contracts.updateEngaged(level, contract);
                // A soldier is fighting: the caravan waits for the fight to end
                if (!contract.engaged)
                {
                    contract.progress = Math.min(contract.path.length - 1, contract.progress + step);
                }
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
        if (!near || !level.isLoaded(at))
        {
            removePuppet(level, contract);
            return;
        }
        final IColony seller = IColonyManager.getInstance().getColonyByWorld(contract.seller, level);
        final IColony buyer = IColonyManager.getInstance().getColonyByWorld(contract.buyer, level);
        final String name = "Caravane " + (seller == null ? "?" : seller.getName()) + " → " + (buyer == null ? "?" : buyer.getName());
        boolean added = false;

        final Entity mule = contract.puppet == null ? null : level.getEntity(contract.puppet);
        if (mule == null || !mule.isAlive())
        {
            final net.minecraft.world.entity.animal.horse.Mule fresh = EntityType.MULE.create(level);
            if (fresh == null)
            {
                return;
            }
            fresh.setNoAi(true);
            fresh.setSilent(true);
            fresh.setPersistenceRequired();
            fresh.setTamed(true);
            fresh.setChest(true);
            fresh.getSlot(400).set(new ItemStack(net.minecraft.world.item.Items.SADDLE));
            fresh.addTag(PUPPET_TAG);
            fresh.setCustomName(net.minecraft.network.chat.Component.literal(name));
            fresh.setCustomNameVisible(true);
            contract.puppet = fresh.getUUID();
            level.addFreshEntity(fresh);
            added = true;
        }

        final Entity driver = contract.driver == null ? null : level.getEntity(contract.driver);
        if (driver == null || !driver.isAlive())
        {
            final EntConducteur fresh = CaravanEntities.CONDUCTEUR.get().create(level);
            if (fresh != null)
            {
                fresh.setNoAi(true);
                fresh.setSilent(true);
                fresh.addTag(PUPPET_TAG);
                contract.driver = fresh.getUUID();
                level.addFreshEntity(fresh);
                added = true;
            }
        }

        // The soldiers still alive: the dead stay dead, the missing (a chunk unloaded) are put back
        contract.escorts.removeIf(id -> {
            final Entity soldier = level.getEntity(id);
            return soldier == null || !soldier.isAlive();
        });
        int swordsmen = 0;
        int archers = 0;
        for (final UUID id : contract.escorts)
        {
            if (level.getEntity(id) instanceof EntEscorte soldier)
            {
                if (soldier.isArcher())
                {
                    archers++;
                }
                else
                {
                    swordsmen++;
                }
            }
        }
        for (int i = swordsmen; i < contract.swordsmenLeft; i++)
        {
            added |= spawnSoldier(level, contract, false);
        }
        for (int i = archers; i < contract.archersLeft; i++)
        {
            added |= spawnSoldier(level, contract, true);
        }
        if (added)
        {
            movePuppet(level, contract);
        }
    }

    private static boolean spawnSoldier(final ServerLevel level, final Contract contract, final boolean archer)
    {
        final EntEscorte soldier = CaravanEntities.ESCORTE.get().create(level);
        if (soldier == null)
        {
            return false;
        }
        soldier.equip(contract.escort, archer, contract.seller, contract.buyer);
        soldier.addTag(ESCORT_TAG);
        contract.escorts.add(soldier.getUUID());
        level.addFreshEntity(soldier);
        return true;
    }

    /**
     * The soldiers of the escort that exist now, in formation order: swordsmen first, then archers.
     */
    private List<EntEscorte> soldiers(final ServerLevel level, final Contract contract)
    {
        final List<EntEscorte> list = new ArrayList<>();
        for (final UUID id : contract.escorts)
        {
            if (level.getEntity(id) instanceof EntEscorte soldier && soldier.isAlive())
            {
                list.add(soldier);
            }
        }
        list.sort(Comparator.comparing(EntEscorte::isArcher));
        return list;
    }

    /**
     * Whether a soldier is fighting; the others join in on the same enemy.
     */
    private boolean updateEngaged(final ServerLevel level, final Contract contract)
    {
        if (contract.escorts.isEmpty())
        {
            return false;
        }
        final List<EntEscorte> soldiers = soldiers(level, contract);
        net.minecraft.world.entity.LivingEntity enemy = null;
        for (final EntEscorte soldier : soldiers)
        {
            if (soldier.engaged())
            {
                enemy = soldier.getTarget();
                break;
            }
        }
        if (enemy == null)
        {
            return false;
        }
        for (final EntEscorte soldier : soldiers)
        {
            if (!soldier.engaged())
            {
                soldier.setTarget(enemy);
            }
        }
        return true;
    }

    /** The mule's place at this progress along the road, and the way it faces. */
    private record Spot(double x, double y, double z, float yaw)
    {
        /** A point beside or ahead of this one: {@code along} blocks forward, {@code side} blocks to the right. */
        double[] offset(final double along, final double side)
        {
            final double fx = -Math.sin(Math.toRadians(yaw));
            final double fz = Math.cos(Math.toRadians(yaw));
            return new double[] {x + fx * along - fz * side, z + fz * along + fx * side};
        }
    }

    private static Spot spotAt(final Contract contract, final double progress)
    {
        final double p = Math.clamp(progress, 0, Math.max(0, contract.path.length - 1));
        final int index = (int) p;
        final BlockPos a = contract.cell(index);
        final BlockPos b = contract.cell(index + 1);
        // At the very end there is no next cell: keep the heading of the last stretch
        final BlockPos from = a.equals(b) ? contract.cell(index - 1) : a;
        final double t = p - index;
        final float yaw = (float) Math.toDegrees(Math.atan2(-(b.getX() - from.getX()), b.getZ() - from.getZ()));
        return new Spot(a.getX() + 0.5 + (b.getX() - a.getX()) * t, a.getY() + 1.0, a.getZ() + 0.5 + (b.getZ() - a.getZ()) * t, yaw);
    }

    /**
     * Where the paper says the caravan is: x, z and the way it faces (Minecraft degrees).
     */
    public static double[] where(final Contract contract)
    {
        final Spot spot = spotAt(contract, contract.progress);
        return new double[] {spot.x, spot.z, spot.yaw};
    }

    /**
     * Sets an entity down without teleporting its old position, so the walking animation plays.
     */
    private static void place(final Entity entity, final double x, final double y, final double z, final float yaw)
    {
        entity.setPos(x, y, z);
        entity.setYRot(yaw);
        entity.setXRot(0);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        entity.fallDistance = 0;
        if (entity instanceof net.minecraft.world.entity.LivingEntity living)
        {
            living.setYHeadRot(yaw);
            living.setYBodyRot(yaw);
        }
    }

    /**
     * Puts the mule, its driver (one block beside it) and the soldiers (in rows ahead of and behind them, alternating
     * sides) where the road says the caravan is.
     */
    public void movePuppet(final ServerLevel level, final Contract contract)
    {
        final Spot spot = spotAt(contract, contract.progress);
        final Entity mule = contract.puppet == null ? null : level.getEntity(contract.puppet);
        if (mule != null)
        {
            place(mule, spot.x, spot.y, spot.z, spot.yaw);
        }
        final Entity driver = contract.driver == null ? null : level.getEntity(contract.driver);
        if (driver != null)
        {
            final double[] xz = spot.offset(0, 1.3);
            place(driver, xz[0], spot.y, xz[1], spot.yaw);
        }
        if (contract.engaged)
        {
            return;
        }
        int slot = 0;
        for (final EntEscorte soldier : soldiers(level, contract))
        {
            final int row = slot / 2;
            final double side = slot % 2 == 0 ? -1.6 : 1.6;
            final Spot there = spotAt(contract, contract.progress + 3.0 - 2.5 * row);
            final double[] xz = there.offset(0, side);
            place(soldier, xz[0], there.y, xz[1], there.yaw);
            slot++;
        }
    }

    /**
     * Takes the whole caravan off the road: mule, driver and soldiers.
     */
    private void removePuppet(final ServerLevel level, final Contract contract)
    {
        discard(level, contract.puppet);
        discard(level, contract.driver);
        for (final UUID id : contract.escorts)
        {
            discard(level, id);
        }
        contract.puppet = null;
        contract.driver = null;
        contract.escorts.clear();
        contract.engaged = false;
    }

    private static void discard(final ServerLevel level, @Nullable final UUID id)
    {
        if (id != null)
        {
            final Entity entity = level.getEntity(id);
            if (entity != null)
            {
                entity.discard();
            }
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

    /**
     * The contract a caravan entity (mule, driver or soldier) belongs to.
     */
    @Nullable
    private Contract ofPuppet(final Entity entity)
    {
        for (final Contract contract : contracts)
        {
            if (entity.getUUID().equals(contract.puppet) || entity.getUUID().equals(contract.driver) || contract.escorts.contains(entity.getUUID()))
            {
                return contract;
            }
        }
        return null;
    }

    private static boolean isCaravanEntity(final Entity entity)
    {
        return entity.getTags().contains(PUPPET_TAG) || entity.getTags().contains(ESCORT_TAG);
    }

    /**
     * Only a colony at open war with the seller or the buyer may hurt a caravan, and while soldiers of its escort live,
     * the mule and its driver cannot be touched: the player must go through them first.
     */
    @SubscribeEvent
    public static void onDamage(final LivingIncomingDamageEvent event)
    {
        final Entity target = event.getEntity();
        if (!isCaravanEntity(target) || !(target.level() instanceof ServerLevel level))
        {
            return;
        }
        final Contracts all = get(level.getServer());
        final Contract contract = all.ofPuppet(target);
        final Entity source = event.getSource().getEntity();
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return;
        }
        if (contract == null || !(source instanceof Player player) || !hostile(level, player, contract.seller, contract.buyer))
        {
            event.setCanceled(true);
            return;
        }
        // The escort takes up arms for whoever strikes the caravan
        for (final EntEscorte soldier : all.soldiers(level, contract))
        {
            if (soldier != target && !soldier.engaged())
            {
                soldier.setTarget(player);
            }
        }
        if (target.getTags().contains(PUPPET_TAG) && contract.swordsmenLeft + contract.archersLeft > 0)
        {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("L'escorte de la caravane la protège : abattez ses soldats d'abord."), true);
            event.setCanceled(true);
        }
    }

    /**
     * Whether this player is at open war with the seller's or the buyer's colony (as a member of a colony that may assault one).
     */
    public static boolean hostile(final ServerLevel level, final Player player, final int seller, final int buyer)
    {
        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            if (colony.getID() != seller && colony.getID() != buyer && colony.getPermissions().isColonyMember(player)
                  && (Diplomacy.mayAssault(level.getServer(), colony.getID(), seller) || Diplomacy.mayAssault(level.getServer(), colony.getID(), buyer)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Killing the mule or its driver loses the contract: the crates drop where it fell, the price goes back to the buyer.
     * Killing a soldier only thins the escort, for good.
     */
    @SubscribeEvent
    public static void onDeath(final LivingDeathEvent event)
    {
        final Entity target = event.getEntity();
        if (!isCaravanEntity(target) || !(target.level() instanceof ServerLevel level))
        {
            return;
        }
        final Contracts contracts = get(level.getServer());
        final Contract contract = contracts.ofPuppet(target);
        if (contract == null)
        {
            return;
        }
        if (target instanceof EntEscorte soldier)
        {
            contract.escorts.remove(soldier.getUUID());
            if (soldier.isArcher())
            {
                contract.archersLeft = Math.max(0, contract.archersLeft - 1);
            }
            else
            {
                contract.swordsmenLeft = Math.max(0, contract.swordsmenLeft - 1);
            }
            contracts.setDirty();
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
        // The rest of the caravan (the other half of the pair, the soldiers) goes with it
        final UUID dead = target.getUUID();
        if (!dead.equals(contract.puppet))
        {
            discard(level, contract.puppet);
        }
        if (!dead.equals(contract.driver))
        {
            discard(level, contract.driver);
        }
        for (final UUID id : contract.escorts)
        {
            discard(level, id);
        }
        contract.puppet = null;
        contract.driver = null;
        contract.escorts.clear();
        contracts.setDirty();
    }

    /**
     * A caravan entity saved in a world without its contract (or reloaded after a restart) is just removed: the
     * road tick spawns a fresh one when a player is near.
     */
    @SubscribeEvent
    public static void onJoin(final EntityJoinLevelEvent event)
    {
        if (event.getLevel() instanceof ServerLevel level && isCaravanEntity(event.getEntity()))
        {
            final Contract contract = get(level.getServer()).ofPuppet(event.getEntity());
            if (contract == null)
            {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Nobody mounts the mule, opens its chest or talks to the driver: they belong to the road.
     */
    @SubscribeEvent
    public static void onInteract(final net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event)
    {
        if (isCaravanEntity(event.getTarget()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractAt(final net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific event)
    {
        if (isCaravanEntity(event.getTarget()))
        {
            event.setCanceled(true);
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
