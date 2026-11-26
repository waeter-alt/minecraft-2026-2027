package fr.kodistan.interregne.dev.gametest;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.politics.Alliances;
import fr.kodistan.interregne.politics.AllyAid;
import fr.kodistan.interregne.politics.Influence;
import fr.kodistan.interregne.politics.InfluenceSources;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * The Influence sources of dealings between players (assault repelled, clan aid, alliance signed, alliance that lasts)
 * on fresh {@link Alliances} and {@link AllyAid} and stand-in colonies with an owner. Influence itself is the real one,
 * on random owners. The assault's own end (repelled or recalled) is left to playtests: only its hook is tested here.
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class InfluenceSourcesTests
{
    private static final int A = 9401;
    private static final int B = 9402;
    private static final int C = 9403;

    private InfluenceSourcesTests()
    {
    }

    private static IColony colony(final int id, final UUID owner)
    {
        final IPermissions permissions = (IPermissions) Proxy.newProxyInstance(InfluenceSourcesTests.class.getClassLoader(), new Class<?>[] {IPermissions.class},
          (proxy, method, args) -> switch (method.getName())
          {
              case "getOwner" -> owner;
              case "hashCode" -> 1;
              case "equals" -> proxy == args[0];
              default -> throw new UnsupportedOperationException(method.getName());
          });
        return (IColony) Proxy.newProxyInstance(InfluenceSourcesTests.class.getClassLoader(), new Class<?>[] {IColony.class}, (proxy, method, args) -> switch (method.getName())
        {
            case "getID" -> id;
            case "getDimension" -> Level.OVERWORLD;
            case "getName", "toString" -> "colonie-test-" + id;
            case "getPermissions" -> permissions;
            case "hashCode" -> id;
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static void expect(final GameTestHelper helper, final long expected, final long actual, final String what)
    {
        if (expected != actual)
        {
            helper.fail(what + ": expected " + expected + ", got " + actual);
        }
    }

    /** An assault repelled pays the defender's owner; the attacker's owner gets nothing. With no contingent, no ally is paid. */
    @GameTest(template = "empty")
    public static void repelledPaysTheDefender(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final UUID ownerA = UUID.randomUUID();
        final UUID ownerB = UUID.randomUUID();
        final IColony attacker = colony(A, ownerA);
        final IColony defender = colony(B, ownerB);

        InfluenceSources.repelled(server, attacker, defender, new AllyAid(), id -> null);
        expect(helper, Config.INFLUENCE_REPELLED.get(), Influence.get(server, ownerB), "the defender earns the repelled assault");
        expect(helper, 0, Influence.get(server, ownerA), "the attacker earns nothing");
        helper.succeed();
    }

    /** A contingent at the defender pays its helper once, however many soldiers it sent, and an absent ally nothing. */
    @GameTest(template = "empty")
    public static void aidPaysTheHelperOncePerAssault(final GameTestHelper helper)
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final UUID ownerA = UUID.randomUUID();
        final UUID ownerB = UUID.randomUUID();
        final UUID ownerC = UUID.randomUUID();
        final IColony attacker = colony(A, ownerA);
        final IColony defender = colony(B, ownerB);
        final IColony helper1 = colony(C, ownerC);

        final AllyAid aid = new AllyAid();
        check(helper, aid.refill(C, B, true, cost -> true) == null, "the reserve must fill");
        final int[] next = {0};
        aid.engage(B, id -> true, (who, kind) -> new AllyAid.Guest(who, next[0]++, new UUID(0, next[0]), kind));

        InfluenceSources.repelled(server, attacker, defender, aid, id -> id == C ? helper1 : null);
        expect(helper, Config.INFLUENCE_ALLY_AID.get(), Influence.get(server, ownerC), "the helper earns once, not once per soldier");
        expect(helper, Config.INFLUENCE_REPELLED.get(), Influence.get(server, ownerB), "the defender still earns its own");
        expect(helper, 0, Influence.get(server, ownerA), "the attacker earns nothing");

        // An ally with only a reserve (nothing sent) earns nothing
        final UUID ownerD = UUID.randomUUID();
        final AllyAid idle = new AllyAid();
        idle.refill(C, B, true, cost -> true);
        InfluenceSources.repelled(server, attacker, defender, idle, id -> colony(id, ownerD));
        expect(helper, 0, Influence.get(server, ownerD), "a reserve that stayed home earns nothing");
        helper.succeed();
    }

    /** Signing pays both owners once; breaking and signing again pays nothing; the memory survives a reload. */
    @GameTest(template = "empty")
    public static void signingPaysOncePerPairEvenAfterReload(final GameTestHelper helper) throws ReflectiveOperationException
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final UUID ownerA = UUID.randomUUID();
        final UUID ownerB = UUID.randomUUID();
        final IColony a = colony(A, ownerA);
        final IColony b = colony(B, ownerB);
        final Alliances alliances = new Alliances();

        InfluenceSources.allianceSigned(server, alliances, a, b);
        expect(helper, Config.INFLUENCE_ALLIANCE_SIGNED.get(), Influence.get(server, ownerA), "A earns the signing");
        expect(helper, Config.INFLUENCE_ALLIANCE_SIGNED.get(), Influence.get(server, ownerB), "B earns the signing");

        // Break, sign again: no second gain
        alliances.doBreak(A, B);
        InfluenceSources.allianceSigned(server, alliances, a, b);
        expect(helper, Config.INFLUENCE_ALLIANCE_SIGNED.get(), Influence.get(server, ownerA), "no second signing for A");

        // The memory is saved and survives a reload
        final HolderLookup.Provider registries = helper.getLevel().registryAccess();
        final CompoundTag tag = alliances.save(new CompoundTag(), registries);
        final var load = Alliances.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true);
        final Alliances reloaded = (Alliances) load.invoke(null, tag, registries);
        InfluenceSources.allianceSigned(server, reloaded, a, b);
        expect(helper, Config.INFLUENCE_ALLIANCE_SIGNED.get(), Influence.get(server, ownerB), "no second signing after a reload");

        // Another pair is a first signing of its own
        final UUID ownerC = UUID.randomUUID();
        InfluenceSources.allianceSigned(server, reloaded, a, colony(C, ownerC));
        expect(helper, Config.INFLUENCE_ALLIANCE_SIGNED.get(), Influence.get(server, ownerC), "a new pair earns its own signing");
        helper.succeed();
    }

    /** A day of alliance pays both; no catch-up for a long gap; nothing without an alliance; the count survives a reload. */
    @GameTest(template = "empty")
    public static void dailyBonusPaysFullDaysOnly(final GameTestHelper helper) throws ReflectiveOperationException
    {
        final MinecraftServer server = helper.getLevel().getServer();
        final UUID ownerA = UUID.randomUUID();
        final UUID ownerB = UUID.randomUUID();
        final IColony a = colony(A, ownerA);
        final IColony b = colony(B, ownerB);
        final java.util.function.IntFunction<IColony> colonies = id -> id == A ? a : id == B ? b : null;
        final Alliances alliances = new Alliances();
        final long day = InfluenceSources.DAY_TICKS;
        final long bonus = Config.INFLUENCE_ALLIANCE_DAILY.get();

        InfluenceSources.dailyBonus(server, alliances, 1000, colonies);
        expect(helper, 0, Influence.get(server, ownerA), "no alliance, no bonus");

        alliances.doPropose(A, B);
        alliances.doAccept(B, A);
        InfluenceSources.dailyBonus(server, alliances, 1000, colonies);
        InfluenceSources.dailyBonus(server, alliances, 1000 + day - 1, colonies);
        expect(helper, 0, Influence.get(server, ownerA), "a day not complete pays nothing");

        InfluenceSources.dailyBonus(server, alliances, 1000 + day, colonies);
        expect(helper, bonus, Influence.get(server, ownerA), "a full day pays A");
        expect(helper, bonus, Influence.get(server, ownerB), "a full day pays B");

        // The count survives a reload: the next day is not paid early nor twice
        final HolderLookup.Provider registries = helper.getLevel().registryAccess();
        final var load = Alliances.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true);
        final Alliances reloaded = (Alliances) load.invoke(null, alliances.save(new CompoundTag(), registries), registries);
        InfluenceSources.dailyBonus(server, reloaded, 1000 + day + 10, colonies);
        expect(helper, bonus, Influence.get(server, ownerA), "no second payment right after the first");

        // Ten days of gap pay one day, not ten
        InfluenceSources.dailyBonus(server, reloaded, 1000 + day * 11, colonies);
        expect(helper, bonus * 2, Influence.get(server, ownerA), "a long gap is not made up");

        // A broken alliance stops the bonus
        reloaded.doBreak(A, B);
        InfluenceSources.dailyBonus(server, reloaded, 1000 + day * 30, colonies);
        expect(helper, bonus * 2, Influence.get(server, ownerA), "no bonus once the alliance is broken");
        helper.succeed();
    }

    private static void check(final GameTestHelper helper, final boolean ok, final String what)
    {
        if (!ok)
        {
            helper.fail(what);
        }
    }
}
