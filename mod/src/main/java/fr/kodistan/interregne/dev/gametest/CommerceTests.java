package fr.kodistan.interregne.dev.gametest;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.commerce.Contract;
import fr.kodistan.interregne.commerce.Contracts;
import fr.kodistan.interregne.region.Crate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The caravan's animal: it appears on the road, cannot be hurt by chance, and its death loses the contract and drops the
 * crates. Colonies and warehouses are checked on a real world with {@code /commerce} (see mod/docs/outils-de-test.md).
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class CommerceTests
{
    private CommerceTests()
    {
    }

    private static Contract caravan(final GameTestHelper helper, final Contracts contracts)
    {
        final Contract contract = new Contract(9_001, 1, 2, Crate.BOIS.id(), 20, 5);
        contract.state = Contract.TRANSIT;
        contract.escrow = 100;
        contract.path = new long[20];
        for (int i = 0; i < contract.path.length; i++)
        {
            contract.path[i] = helper.absolutePos(new BlockPos(i / 2, 1, 2)).asLong();
        }
        contract.progress = 4.5;
        contracts.all().add(contract);
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
        final boolean gone = contracts.byId(9_001) == null;
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
}
