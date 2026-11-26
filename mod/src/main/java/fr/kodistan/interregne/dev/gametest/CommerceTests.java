package fr.kodistan.interregne.dev.gametest;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.commerce.Contract;
import fr.kodistan.interregne.commerce.CaravanEntities;
import fr.kodistan.interregne.commerce.Contracts;
import fr.kodistan.interregne.commerce.EntConducteur;
import fr.kodistan.interregne.commerce.EntEscorte;
import fr.kodistan.interregne.commerce.Escort;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The caravan's animal: it appears on the road, cannot be hurt by chance, and its death loses the contract and drops the
 * crates. Colonies and warehouses are checked on a real world with {@code /commerce} (see mod/docs/outils-de-test.md).
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class CommerceTests
{
    /** Tests of a batch run side by side on one server: each contract needs an id of its own. */
    private static final java.util.concurrent.atomic.AtomicLong NEXT_ID = new java.util.concurrent.atomic.AtomicLong(9_000);

    private CommerceTests()
    {
    }

    private static Contract caravan(final GameTestHelper helper, final Contracts contracts)
    {
        return caravan(helper, contracts, 0);
    }

    private static Contract caravan(final GameTestHelper helper, final Contracts contracts, final int escort)
    {
        final Contract contract = new Contract(NEXT_ID.incrementAndGet(), 1, 2, Crate.BOIS.id(), 20, 5);
        contract.state = Contract.TRANSIT;
        contract.escrow = 100;
        contract.path = new long[20];
        for (int i = 0; i < contract.path.length; i++)
        {
            contract.path[i] = helper.absolutePos(new BlockPos(i / 2, 1, 2)).asLong();
        }
        contract.progress = 4.5;
        contract.escort = escort;
        contract.swordsmenLeft = Escort.swordsmen(escort);
        contract.archersLeft = Escort.archers(escort);
        contracts.all().add(contract);
        // A floor for the soldiers to stand on
        for (int x = -2; x < 14; x++)
        {
            for (int z = -2; z < 12; z++)
            {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        return contract;
    }

    @GameTest(template = "empty")
    public static void theCaravanAppearsAndItsDeathDropsTheCrates(final GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final Contracts contracts = Contracts.get(level.getServer());
        final Contract contract = caravan(helper, contracts);
        contracts.showOrHide(level.getServer(), level, contract, true);
        final Entity animal = contract.puppet == null ? null : level.getEntity(contract.puppet);
        if (animal == null || !animal.getTags().contains(Contracts.PUPPET_TAG))
        {
            contracts.all().remove(contract);
            helper.fail("no caravan animal on the road");
            return;
        }
        if (!(animal instanceof Mob mob) || !mob.isNoAi())
        {
            contracts.all().remove(contract);
            helper.fail("the animal must be a mob without AI: it is moved by the contract");
            return;
        }
        // Where the paper says the caravan is, within a block
        final BlockPos expected = contract.cell((int) contract.progress);
        if (animal.position().distanceTo(expected.getCenter()) > 3)
        {
            contracts.all().remove(contract);
            helper.fail("the animal is not at the caravan's place on the road: " + animal.position() + " vs " + expected);
            return;
        }
        // Nobody at war with it: a blow does nothing
        animal.hurt(level.damageSources().generic(), 1000);
        if (!animal.isAlive())
        {
            contracts.all().remove(contract);
            helper.fail("a caravan must survive a blow from nobody at war with its colonies");
            return;
        }
        // /kill and the void still work
        animal.kill();
        final AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(20);
        final int crates = level.getEntitiesOfClass(ItemEntity.class, around).stream().filter(item -> Crate.of(item.getItem()) == Crate.BOIS).mapToInt(item -> item.getItem().getCount()).sum();
        final boolean gone = contracts.byId(contract.id) == null;
        contracts.all().remove(contract);
        if (!gone)
        {
            helper.fail("the contract must end with its caravan");
            return;
        }
        if (crates != 20)
        {
            helper.fail("the 20 crates must drop where the caravan fell, found " + crates);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theAnimalLeavesWhenNobodyIsNear(final GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final Contracts contracts = Contracts.get(level.getServer());
        final Contract contract = caravan(helper, contracts);
        contracts.showOrHide(level.getServer(), level, contract, true);
        final Entity animal = contract.puppet == null ? null : level.getEntity(contract.puppet);
        contracts.showOrHide(level.getServer(), level, contract, false);
        final boolean removed = animal != null && (animal.isRemoved() || level.getEntity(animal.getUUID()) == null) && contract.puppet == null;
        contracts.all().remove(contract);
        if (animal == null || !removed)
        {
            helper.fail("the animal must be removed when no player is near, and forgotten by the contract");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theCaravanIsAMuleWithItsChestAndADriverBesideIt(final GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final Contracts contracts = Contracts.get(level.getServer());
        final Contract contract = caravan(helper, contracts);
        contracts.showOrHide(level.getServer(), level, contract, true);
        final Entity mule = contract.puppet == null ? null : level.getEntity(contract.puppet);
        final Entity driver = contract.driver == null ? null : level.getEntity(contract.driver);
        final String problem;
        if (!(mule instanceof net.minecraft.world.entity.animal.horse.Mule chested) || !chested.hasChest() || !chested.isNoAi())
        {
            problem = "the caravan's animal must be a mule with its chest and no AI";
        }
        else if (!(driver instanceof EntConducteur man) || !man.isNoAi() || !man.getTags().contains(Contracts.PUPPET_TAG))
        {
            problem = "a driver must walk beside the mule";
        }
        else if (Math.abs(man.position().distanceTo(mule.position()) - 1.3) > 0.4)
        {
            problem = "the driver must walk one block beside the mule, found " + man.position().distanceTo(mule.position());
        }
        else
        {
            problem = null;
        }
        contracts.showOrHide(level.getServer(), level, contract, false);
        contracts.all().remove(contract);
        if (problem != null)
        {
            helper.fail(problem);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void killingTheDriverLosesTheCaravan(final GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final Contracts contracts = Contracts.get(level.getServer());
        final Contract contract = caravan(helper, contracts, 1);
        contracts.showOrHide(level.getServer(), level, contract, true);
        final Entity driver = level.getEntity(contract.driver);
        final UUID mule = contract.puppet;
        final List<UUID> soldiers = List.copyOf(contract.escorts);
        driver.kill();
        final boolean gone = contracts.byId(contract.id) == null;
        final boolean alone = level.getEntity(mule) == null && soldiers.stream().allMatch(id -> level.getEntity(id) == null);
        contracts.all().remove(contract);
        if (!gone || !alone)
        {
            helper.fail("the contract must end with its driver, and the rest of the caravan with it");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theEscortWalksWithTheCaravanAndTheDeadDoNotComeBack(final GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final Contracts contracts = Contracts.get(level.getServer());
        final Contract contract = caravan(helper, contracts, 1);
        contracts.showOrHide(level.getServer(), level, contract, true);
        final int expected = Escort.headcount(1);
        if (contract.escorts.size() != expected)
        {
            contracts.all().remove(contract);
            helper.fail("tier 1 must hire " + expected + " soldiers, found " + contract.escorts.size());
            return;
        }
        final EntEscorte first = (EntEscorte) level.getEntity(contract.escorts.get(0));
        // The caravan moves on: the soldiers follow, staying close to the mule
        contract.progress = 10.5;
        contracts.movePuppet(level, contract);
        final Entity mule = level.getEntity(contract.puppet);
        final double far = contract.escorts.stream().map(level::getEntity).mapToDouble(e -> e.position().distanceTo(mule.position())).max().orElse(0);
        if (far > 9)
        {
            contracts.showOrHide(level.getServer(), level, contract, false);
            contracts.all().remove(contract);
            helper.fail("the soldiers must keep with the mule, the farthest is " + far + " blocks away");
            return;
        }
        // One dies for good: hiding and showing the caravan again does not bring it back
        first.kill();
        contracts.showOrHide(level.getServer(), level, contract, false);
        contracts.showOrHide(level.getServer(), level, contract, true);
        final int back = contract.escorts.size();
        final int left = contract.swordsmenLeft + contract.archersLeft;
        contracts.showOrHide(level.getServer(), level, contract, false);
        contracts.all().remove(contract);
        if (back != expected - 1 || left != expected - 1)
        {
            helper.fail("a dead soldier must not be replaced: " + back + " on the road, " + left + " alive, expected " + (expected - 1));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anEntityOfAnUnknownCaravanIsRemovedAtLoad(final GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        final EntConducteur stray = CaravanEntities.CONDUCTEUR.get().create(level);
        stray.addTag(Contracts.PUPPET_TAG);
        stray.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 1, 2)));
        final EntEscorte soldier = CaravanEntities.ESCORTE.get().create(level);
        soldier.equip(1, false, 1, 2);
        soldier.addTag(Contracts.ESCORT_TAG);
        soldier.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3, 1, 2)));
        final boolean added = level.addFreshEntity(stray) | level.addFreshEntity(soldier);
        if (added || stray.isAddedToLevel() || soldier.isAddedToLevel())
        {
            helper.fail("a caravan entity without a contract must not join the world");
            return;
        }
        helper.succeed();
    }

    /**
     * A lone player in plain iron gear, sword in hand, against a tier 1 escort: the duel is played tick by tick, the player
     * landing a sword blow every 12 ticks on the nearest soldier in reach. The escort must at least leave him badly hurt.
     * <p>
     * The duel must give the same result on every run, so nothing is left to chance:
     * <ul>
     * <li>its own batch: the 14-block arena reaches over the grid slots of the other tests, which clear the entities of their
     * slot when they start or end (the archer used to vanish mid-duel);</li>
     * <li>each soldier's random is seeded (its path refresh delay is drawn from it);</li>
     * <li>each soldier thinks on the same tick parity: a mob only re-plans its goals when tick count plus entity id is even,
     * and the ids depend on the other tests of the run;</li>
     * <li>the soldiers do not hurt one another: the fake player is in no level list, so the archer's arrows fly through him into
     * the swordsmen fighting him, which a real player standing in the way would stop (and the arrows that should hit the player
     * do nothing here, so the escort stays weaker than in a real fight).</li>
     * </ul>
     */
    @GameTest(template = "empty", batch = "duelEscorte", timeoutTicks = 800)
    public static void aTierOneEscortHoldsAgainstOneIronPlayer(final GameTestHelper helper)
    {
        final ServerLevel level = helper.getLevel();
        // The arena sits inside the chunk of the test's origin: pathfinding and ticking stop at the edge of loaded chunks, and the
        // test's own neighbours are not always loaded
        final BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        final net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(origin);
        final int arenaX = chunk.getMinBlockX();
        final int arenaZ = chunk.getMinBlockZ();
        for (int x = 1; x < 15; x++)
        {
            for (int z = 1; z < 15; z++)
            {
                level.setBlock(new BlockPos(arenaX + x, origin.getY(), arenaZ + z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 1; y <= 4; y++)
                {
                    level.setBlock(new BlockPos(arenaX + x, origin.getY() + y, arenaZ + z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // The stock fake player is invulnerable (and never ticks off its spawn protection): this one takes the blows with the
        // armour's reduction like a real one, which is all the duel needs
        final var player = new net.neoforged.neoforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "duel-test"))
        {
            @Override
            public boolean isInvulnerable()
            {
                return false;
            }

            @Override
            public boolean hurt(final net.minecraft.world.damagesource.DamageSource source, final float amount)
            {
                if (!isAlive())
                {
                    return false;
                }
                System.out.println("[duel] player takes " + amount + " from " + source.getEntity());
                setHealth(getHealth() - getDamageAfterArmorAbsorb(source, amount));
                return true;
            }
        };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        player.setHealth(20);
        // The soldiers of a tier 1 escort, free of any contract (the war and contract rules need colonies, tested elsewhere)
        final java.util.ArrayList<EntEscorte> soldiers = new java.util.ArrayList<>();
        for (int i = 0; i < Escort.headcount(1); i++)
        {
            final EntEscorte soldier = new EntEscorte(CaravanEntities.ESCORTE.get(), level)
            {
                @Override
                public boolean hurt(final net.minecraft.world.damagesource.DamageSource source, final float amount)
                {
                    return !(source.getEntity() instanceof EntEscorte) && super.hurt(source, amount);
                }
            };
            soldier.getRandom().setSeed(1000 + i);
            soldier.tickCount = soldier.getId() & 1;
            soldier.equip(1, i >= Escort.swordsmen(1), 1, 2);
            soldier.setPos(arenaX + 9.5 + i * 1.5, origin.getY() + 1, arenaZ + 6.5 + i);
            level.addFreshEntity(soldier);
            soldiers.add(soldier);
        }
        player.setPos(arenaX + 7.5, origin.getY() + 1, arenaZ + 7.5);
        
        final int[] tick = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            // A fake player is in no level list, so the soldiers' own search cannot find it: they are told whom to fight, as a
            // blow on the caravan does in the real game
            soldiers.stream().filter(soldier -> soldier.isAlive() && soldier.getTarget() == null).forEach(soldier -> soldier.setTarget(player));
            if (tick[0] % 20 == 0 && tick[0] <= 120)
            {
                System.out.println("[duel] t=" + tick[0] + " player " + player.getHealth() + " soldiers " + soldiers.stream().map(x -> x.isAlive() ? String.format("%.0f d%.1f %s", x.getHealth(), x.distanceTo(player), x.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).map(g -> g.getGoal().getClass().getSimpleName()).toList()) : "dead(" + x.getRemovalReason() + "," + x.getCombatTracker().getDeathMessage().getString() + ")").toList());
            }
            if (!player.isAlive() || tick[0] % 12 != 0 )
            {
                return;
            }
            EntEscorte nearest = null;
            for (final EntEscorte soldier : soldiers)
            {
                if (soldier.isAlive() && soldier.distanceTo(player) < 3.0 && (nearest == null || soldier.distanceTo(player) < nearest.distanceTo(player)))
                {
                    nearest = soldier;
                }
            }
            if (nearest != null)
            {
                nearest.hurt(level.damageSources().playerAttack(player), 6.0F);
            }
        });
        helper.runAfterDelay(700, () -> {
            final long alive = soldiers.stream().filter(Entity::isAlive).count();
            final float health = player.isAlive() ? player.getHealth() : 0;
            System.out.println("[duel] tier 1 vs one iron player after 35 s: player health " + health + "/20, soldiers alive " + alive + "/" + soldiers.size());
            soldiers.forEach(Entity::discard);
            if (health > 8 && alive < soldiers.size())
            {
                helper.fail("a lone player in iron must not walk through a tier 1 escort: health " + health + ", soldiers left " + alive);
                return;
            }
            helper.succeed();
        });
    }
}
