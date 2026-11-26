package fr.kodistan.interregne.politics;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.RegionalTreasury;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.garrison.Garrison;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

/**
 * Aide de clan : a colony pays, from its town hall treasury, for a contingent of soldiers kept in reserve for an ally.
 * The helper's own troops never move. At the first assault the ally suffers, the contingent appears inside the ally's
 * colony as guests of its towers, beyond their capacity; when that colony is no longer at war as a defender, the guests
 * vanish and the survivors become the new reserve, which the helper can fill up again.
 * <p>
 * Every rule runs on colony ids so it can be tested without a colony; the server side (spawning, treasury, messages)
 * sits in the static methods.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class AllyAid extends SavedData
{
    private static final String NAME = "interregne_ally_aid";
    private static final int CHECK_EVERY = 20;

    /** A soldier of {@code helper}'s contingent, living in the helped colony as citizen {@code citizen}. */
    public record Guest(int helper, int citizen, UUID uuid, int kind)
    {
    }

    /** One helper's contingent engaged at an assault: soldiers taken from the reserve, and how many appeared. */
    public record Sent(int helper, int taken, int spawned)
    {
    }

    /** One helper's contingent at the end of the war: how many it sent, how many came back into the reserve. */
    public record Back(int helper, int sent, int survivors, boolean allied)
    {
    }

    /** Creates one guest soldier of {@code kind} for {@code helper}, or returns null when the colony has no place. */
    public interface Spawner
    {
        @Nullable
        Guest spawn(int helper, int kind);
    }

    /** Reserved soldiers by {@code helper << 32 | helped}. */
    private final Map<Long, Integer> reserves = new HashMap<>();
    /** Pairs whose contingent is out in the helped colony, to the number of soldiers that appeared. */
    private final Map<Long, Integer> engaged = new HashMap<>();
    /** Guests alive in each helped colony. */
    private final Map<Integer, List<Guest>> guests = new HashMap<>();
    /** Per helped colony, during its war: {soldiers received, soldiers fallen}. */
    private final Map<Integer, int[]> tally = new HashMap<>();

    /**
     * The citizen being enlisted right now: it must pass the towers' capacity check before it is registered here.
     */
    @Nullable
    private static ICitizenData enlisting;

    public static AllyAid get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(AllyAid::new, AllyAid::load, null), NAME);
    }

    public static long pair(final int helper, final int helped)
    {
        return (long) helper << 32 | (helped & 0xFFFFFFFFL);
    }

    private static int helperOf(final long pair)
    {
        return (int) (pair >> 32);
    }

    private static int helpedOf(final long pair)
    {
        return (int) pair;
    }

    public static int max()
    {
        return Config.ALLY_AID_MAX_TROOPS.get();
    }

    public static long price()
    {
        return Config.ALLY_AID_TROOP_PRICE.get();
    }

    public static int level()
    {
        return Config.ALLY_AID_TROOP_LEVEL.get();
    }

    // ---- Rules, on colony ids ----

    public int reserve(final int helper, final int helped)
    {
        return reserves.getOrDefault(pair(helper, helped), 0);
    }

    public boolean isEngaged(final int helper, final int helped)
    {
        return engaged.containsKey(pair(helper, helped));
    }

    /** Soldiers of {@code helper}'s contingent still standing in {@code helped}. */
    public int alive(final int helper, final int helped)
    {
        int count = 0;
        for (final Guest guest : guests.getOrDefault(helped, List.of()))
        {
            count += guest.helper() == helper ? 1 : 0;
        }
        return count;
    }

    /** What filling the reserve up to the maximum costs now. */
    public long refillCost(final int helper, final int helped)
    {
        return Math.max(0, max() - reserve(helper, helped)) * price();
    }

    /**
     * Fills {@code helper}'s reserve for {@code helped} up to the maximum, {@code pay} taking the price from the helper's
     * treasury (false when it cannot).
     *
     * @return a refusal for the player, or null when the reserve is full and paid
     */
    @Nullable
    public String refill(final int helper, final int helped, final boolean allied, final LongPredicate pay)
    {
        if (!allied)
        {
            return "Un contingent ne se réserve que pour un allié.";
        }
        if (isEngaged(helper, helped))
        {
            return "Votre contingent se bat déjà chez eux : il revient à la fin de la guerre.";
        }
        final int missing = max() - reserve(helper, helped);
        if (missing <= 0)
        {
            return "Le contingent est déjà au complet.";
        }
        final long cost = missing * price();
        if (!pay.test(cost))
        {
            return "Il faut " + Wallet.format(cost) + " couronnes au trésor de la colonie pour remplir le contingent.";
        }
        reserves.put(pair(helper, helped), max());
        setDirty();
        return null;
    }

    /** An alliance broke: both reserves between the two are lost. A contingent already out stays until its war ends. */
    public void forget(final int a, final int b)
    {
        if (reserves.remove(pair(a, b)) != null | reserves.remove(pair(b, a)) != null)
        {
            setDirty();
        }
    }

    /**
     * The colony is gone. Its reserves and the contingents it received vanish with it (those were citizens of the gone
     * colony); the contingents it had sent elsewhere are taken out of those colonies.
     *
     * @return the guests of the gone helper still standing in other colonies, as {helped colony, guest}, for the caller
     * to dismiss from their towers
     */
    public List<Map.Entry<Integer, Guest>> removeColony(final int colony)
    {
        final List<Map.Entry<Integer, Guest>> toDismiss = new ArrayList<>();
        reserves.keySet().removeIf(key -> helperOf(key) == colony || helpedOf(key) == colony);
        engaged.keySet().removeIf(key -> helperOf(key) == colony || helpedOf(key) == colony);
        guests.remove(colony);
        tally.remove(colony);
        for (final Map.Entry<Integer, List<Guest>> entry : guests.entrySet())
        {
            for (final Guest guest : entry.getValue())
            {
                if (guest.helper() == colony)
                {
                    toDismiss.add(Map.entry(entry.getKey(), guest));
                }
            }
            entry.getValue().removeIf(guest -> guest.helper() == colony);
        }
        guests.values().removeIf(List::isEmpty);
        setDirty();
        return toDismiss;
    }

    /** Every colony id this state mentions, for the purge of colonies that no longer exist. */
    public Set<Integer> colonyIds()
    {
        final Set<Integer> ids = new HashSet<>(guests.keySet());
        tally.keySet().forEach(ids::add);
        for (final long key : reserves.keySet())
        {
            ids.add(helperOf(key));
            ids.add(helpedOf(key));
        }
        for (final long key : engaged.keySet())
        {
            ids.add(helperOf(key));
            ids.add(helpedOf(key));
        }
        for (final List<Guest> list : guests.values())
        {
            list.forEach(guest -> ids.add(guest.helper()));
        }
        return ids;
    }

    /**
     * The first assault on {@code helped}: every allied helper's reserve that is not out yet appears, half légionnaires
     * and half archères (the odd one is a légionnaire). Soldiers the colony cannot take stay in the reserve.
     */
    public List<Sent> engage(final int helped, final IntPredicate allied, final Spawner spawner)
    {
        final List<Sent> sent = new ArrayList<>();
        for (final Map.Entry<Long, Integer> entry : new ArrayList<>(reserves.entrySet()))
        {
            final long key = entry.getKey();
            final int helper = helperOf(key);
            final int taken = entry.getValue();
            if (helpedOf(key) != helped || taken <= 0 || engaged.containsKey(key) || !allied.test(helper))
            {
                continue;
            }
            int spawned = 0;
            final int legion = (taken + 1) / 2;
            for (int i = 0; i < taken; i++)
            {
                final Guest guest = spawner.spawn(helper, i < legion ? Garrison.LEGIONNAIRE : Garrison.ARCHERE);
                if (guest != null)
                {
                    guests.computeIfAbsent(helped, k -> new ArrayList<>()).add(guest);
                    spawned++;
                }
            }
            if (spawned > 0)
            {
                reserves.put(key, taken - spawned);
                engaged.put(key, spawned);
                tally.computeIfAbsent(helped, k -> new int[2])[0] += spawned;
                setDirty();
            }
            sent.add(new Sent(helper, taken, spawned));
        }
        return sent;
    }

    public boolean isGuest(final int helped, final int citizen, final UUID uuid)
    {
        for (final Guest guest : guests.getOrDefault(helped, List.of()))
        {
            if (guest.citizen() == citizen && guest.uuid().equals(uuid))
            {
                return true;
            }
        }
        return false;
    }

    /** A guest fell: it leaves the contingent for good. */
    public boolean fell(final int helped, final int citizen, final UUID uuid)
    {
        final List<Guest> list = guests.get(helped);
        if (list != null && list.removeIf(guest -> guest.citizen() == citizen && guest.uuid().equals(uuid)))
        {
            tally.computeIfAbsent(helped, k -> new int[2])[1]++;
            setDirty();
            return true;
        }
        return false;
    }

    /** Guests standing in {@code helped}. */
    public List<Guest> guestsOf(final int helped)
    {
        return List.copyOf(guests.getOrDefault(helped, List.of()));
    }

    /** Colonies with a contingent out in them. */
    public Set<Integer> helpedWithGuests()
    {
        final Set<Integer> out = new HashSet<>();
        for (final long key : engaged.keySet())
        {
            out.add(helpedOf(key));
        }
        return out;
    }

    /** {received, fallen} for {@code helped}'s current war. */
    public int[] tally(final int helped)
    {
        return tally.getOrDefault(helped, new int[2]).clone();
    }

    /**
     * {@code helped}'s war is over: every contingent in it goes home. The guests still {@code standing} become their
     * helper's new reserve (never above the maximum), unless the alliance broke meanwhile: then they are lost.
     */
    public List<Back> release(final int helped, final IntPredicate allied, final Predicate<Guest> standing)
    {
        final List<Back> back = new ArrayList<>();
        final List<Guest> present = guests.getOrDefault(helped, List.of());
        for (final Map.Entry<Long, Integer> entry : new ArrayList<>(engaged.entrySet()))
        {
            final long key = entry.getKey();
            if (helpedOf(key) != helped)
            {
                continue;
            }
            final int helper = helperOf(key);
            int survivors = 0;
            for (final Guest guest : present)
            {
                survivors += guest.helper() == helper && standing.test(guest) ? 1 : 0;
            }
            final boolean stillAllied = allied.test(helper);
            if (stillAllied)
            {
                reserves.put(key, Math.min(max(), reserve(helper, helped) + survivors));
            }
            else
            {
                reserves.remove(key);
            }
            engaged.remove(key);
            back.add(new Back(helper, entry.getValue(), survivors, stillAllied));
        }
        guests.remove(helped);
        tally.remove(helped);
        setDirty();
        return back;
    }

    // ---- Server side ----

    /**
     * Whether this citizen is a guest soldier (or one being enlisted as such). On the client, or without a server: no.
     */
    public static boolean isGuest(@Nullable final ICitizenData citizen)
    {
        if (citizen == null)
        {
            return false;
        }
        if (citizen == enlisting)
        {
            return true;
        }
        final IColony colony = citizen.getColony();
        if (colony == null || colony.getWorld() == null || colony.getWorld().isClientSide)
        {
            return false;
        }
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.overworld() != null && get(server).isGuest(colony.getID(), citizen.getId(), citizen.getUUID());
    }

    /** Lets {@code citizen} through the towers' capacity check while it is being enlisted ({@code null} when done). */
    public static void enlisting(@Nullable final ICitizenData citizen)
    {
        enlisting = citizen;
    }

    /** Pays a refill from {@code helper}'s town hall treasury; a refusal or null. */
    @Nullable
    public static String refill(final MinecraftServer server, final IColony helper, final IColony helped)
    {
        final RegionalTreasury treasury = RegionalTreasury.get(server);
        return get(server).refill(helper.getID(), helped.getID(), Alliances.get(server).areAllied(helper.getID(), helped.getID()),
          cost -> treasury.spend(helper, cost));
    }

    /** Called once an assault on {@code defender} is under way: the allies' contingents appear. */
    public static void onAssault(final MinecraftServer server, final IColony defender)
    {
        final AllyAid aid = get(server);
        final Alliances alliances = Alliances.get(server);
        final int level = level();
        final List<Sent> sent = aid.engage(defender.getID(), helper -> alliances.areAllied(helper, defender.getID()), (helper, kind) -> {
            final ICitizenData citizen = Garrison.enlistGuest(server, defender, kind, level);
            return citizen == null ? null : new Guest(helper, citizen.getId(), citizen.getUUID(), kind);
        });
        for (final Sent one : sent)
        {
            final IColony helper = IColonyManager.getInstance().getColonyByWorld(one.helper(), server.overworld());
            if (helper == null || one.spawned() == 0)
            {
                continue;
            }
            Diplomacy.notifyMembers(server, defender, "§a" + helper.getName() + " envoie " + one.spawned() + " soldat(s) en renfort.");
            Diplomacy.notifyMembers(server, helper, "Votre contingent est engagé chez " + defender.getName() + " (" + one.spawned() + " soldat(s)).");
            Alliances.broadcast(server, helper, defender);
        }
    }

    /** Sends every contingent of {@code helped} home: guests vanish, survivors refill the reserve. */
    private static void releaseAll(final MinecraftServer server, final int helped)
    {
        final AllyAid aid = get(server);
        final Alliances alliances = Alliances.get(server);
        final IColony colony = IColonyManager.getInstance().getColonyByWorld(helped, server.overworld());
        final List<Guest> present = aid.guestsOf(helped);
        final List<Back> back = aid.release(helped, helper -> alliances.areAllied(helper, helped),
          guest -> colony != null && standing(colony, guest));
        if (colony != null)
        {
            for (final Guest guest : present)
            {
                final ICitizenData citizen = colony.getCitizenManager().getCivilian(guest.citizen());
                if (citizen != null && citizen.getUUID().equals(guest.uuid()))
                {
                    Garrison.dismiss(server, colony, citizen);
                }
            }
        }
        for (final Back one : back)
        {
            final IColony helper = IColonyManager.getInstance().getColonyByWorld(one.helper(), server.overworld());
            if (helper == null)
            {
                continue;
            }
            final String from = colony != null ? colony.getName() : "la guerre";
            Diplomacy.notifyMembers(server, helper, one.allied()
              ? "Retour de " + from + " : " + one.survivors() + "/" + one.sent() + " soldat(s) rentrent dans la réserve."
              : "Retour de " + from + " : l'alliance est rompue, les " + one.survivors() + " survivant(s) ne rentrent pas dans la réserve.");
            if (colony != null)
            {
                Alliances.broadcast(server, helper, colony);
            }
        }
    }

    private static boolean standing(final IColony colony, final Guest guest)
    {
        final ICitizenData citizen = colony.getCitizenManager().getCivilian(guest.citizen());
        return citizen != null && citizen.getUUID().equals(guest.uuid());
    }

    /** A contingent goes home once its colony is no longer defending any war (peace, capitulation, conquest, admin). */
    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY != 0 || server.overworld() == null)
        {
            return;
        }
        final AllyAid aid = get(server);
        if (aid.engaged.isEmpty())
        {
            return;
        }
        final Wars wars = Wars.get(server);
        for (final int helped : aid.helpedWithGuests())
        {
            if (!wars.defending(helped))
            {
                releaseAll(server, helped);
            }
        }
    }

    /** Runs after the assault's own death count, which must still see the guest to leave it out. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(final LivingDeathEvent event)
    {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof EntityCitizen entity))
        {
            return;
        }
        final ICitizenData citizen = entity.getCitizenData();
        final MinecraftServer server = event.getEntity().getServer();
        if (citizen != null && citizen.getColony() != null && server != null)
        {
            get(server).fell(citizen.getColony().getID(), citizen.getId(), citizen.getUUID());
        }
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final ListTag reserveList = new ListTag();
        reserves.forEach((key, count) -> {
            final CompoundTag entry = new CompoundTag();
            entry.putLong("pair", key);
            entry.putInt("count", count);
            entry.putInt("engaged", engaged.getOrDefault(key, -1));
            reserveList.add(entry);
        });
        // A pair engaged with an empty reserve has no reserve entry
        engaged.forEach((key, count) -> {
            if (!reserves.containsKey(key))
            {
                final CompoundTag entry = new CompoundTag();
                entry.putLong("pair", key);
                entry.putInt("count", 0);
                entry.putInt("engaged", count);
                reserveList.add(entry);
            }
        });
        tag.put("reserves", reserveList);
        final ListTag guestList = new ListTag();
        guests.forEach((helped, list) -> list.forEach(guest -> {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("helped", helped);
            entry.putInt("helper", guest.helper());
            entry.putInt("citizen", guest.citizen());
            entry.putUUID("uuid", guest.uuid());
            entry.putInt("kind", guest.kind());
            guestList.add(entry);
        }));
        tag.put("guests", guestList);
        final ListTag tallyList = new ListTag();
        tally.forEach((helped, counts) -> {
            final CompoundTag entry = new CompoundTag();
            entry.putInt("helped", helped);
            entry.putInt("received", counts[0]);
            entry.putInt("fallen", counts[1]);
            tallyList.add(entry);
        });
        tag.put("tally", tallyList);
        return tag;
    }

    private static AllyAid load(final CompoundTag tag, final HolderLookup.Provider registries)
    {
        final AllyAid data = new AllyAid();
        for (final Tag t : tag.getList("reserves", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) t;
            final long key = entry.getLong("pair");
            data.reserves.put(key, entry.getInt("count"));
            if (entry.getInt("engaged") >= 0)
            {
                data.engaged.put(key, entry.getInt("engaged"));
            }
        }
        for (final Tag t : tag.getList("guests", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) t;
            data.guests.computeIfAbsent(entry.getInt("helped"), k -> new ArrayList<>())
              .add(new Guest(entry.getInt("helper"), entry.getInt("citizen"), entry.getUUID("uuid"), entry.getInt("kind")));
        }
        for (final Tag t : tag.getList("tally", Tag.TAG_COMPOUND))
        {
            final CompoundTag entry = (CompoundTag) t;
            data.tally.put(entry.getInt("helped"), new int[] {entry.getInt("received"), entry.getInt("fallen")});
        }
        return data;
    }
}
