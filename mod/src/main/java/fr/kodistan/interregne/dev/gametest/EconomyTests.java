package fr.kodistan.interregne.dev.gametest;

import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.BankCounterBlock;
import fr.kodistan.interregne.economy.Couronnes;
import fr.kodistan.interregne.economy.ImperialBank;
import fr.kodistan.interregne.economy.TreasuryPayment;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.BankActionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Money handled through a real (simulated) player on a real server: run with {@code ./gradlew runGameTestServer}.
 * Things that need a colony are checked by the scenarios instead ({@link fr.kodistan.interregne.dev.ScenarioRunner}).
 */
@GameTestHolder(InterregneMod.MODID)
@PrefixGameTestTemplate(false)
public final class EconomyTests
{
    private static final BlockPos COUNTER = new BlockPos(2, 1, 2);

    private EconomyTests()
    {
    }

    /** A player standing next to a bank counter, with this much money on them. */
    private static ServerPlayer customer(final GameTestHelper helper, final long money)
    {
        helper.setBlock(COUNTER, Couronnes.GUICHET.get());
        // A fake player: real inventory and position, but no network and no login, so our sync packets go nowhere
        final ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "client-test"));
        final BlockPos at = helper.absolutePos(COUNTER.east());
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.getInventory().clearContent();
        Wallet.give(purse(player), money, stack -> helper.fail("the starting purse overflowed"));
        return player;
    }

    private static PlayerMainInvWrapper purse(final ServerPlayer player)
    {
        return new PlayerMainInvWrapper(player.getInventory());
    }

    /** The packet context the network would hand the server, reduced to who sent it. */
    private static IPayloadContext from(final ServerPlayer player)
    {
        return (IPayloadContext) Proxy.newProxyInstance(EconomyTests.class.getClassLoader(), new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> {
            if (method.getName().equals("player"))
            {
                return player;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static void act(final GameTestHelper helper, final ServerPlayer player, final int action, final long amount)
    {
        BankCounterBlock.onAction(new BankActionPayload(helper.absolutePos(COUNTER), action, amount), from(player));
    }

    private static void expect(final GameTestHelper helper, final long expected, final long actual, final String what)
    {
        if (expected != actual)
        {
            helper.fail(what + ": expected " + expected + ", got " + actual);
        }
    }

    /** Coins lying on the ground around the test, dropped because they did not fit. */
    private static long dropped(final GameTestHelper helper)
    {
        final AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().mapToLong(e -> Wallet.value(e.getItem())).sum();
    }

    @GameTest(template = "empty")
    public static void depositThenWithdraw(final GameTestHelper helper)
    {
        final ServerPlayer player = customer(helper, 1_234);
        final ImperialBank bank = ImperialBank.get(helper.getLevel().getServer());

        act(helper, player, BankActionPayload.DEPOSIT, 0);
        expect(helper, 0, Wallet.value(purse(player)), "purse after deposit");
        expect(helper, 1_234, bank.balance(player.getUUID()), "account after deposit");

        act(helper, player, BankActionPayload.WITHDRAW, 1_000);
        final long fee = ImperialBank.feeFor(1_000);
        expect(helper, 1_000, Wallet.value(purse(player)), "purse after withdrawal");
        expect(helper, 1_234 - 1_000 - fee, bank.balance(player.getUUID()), "account after withdrawal and fee");

        // More than the account holds with the fee: refused, nothing moves
        act(helper, player, BankActionPayload.WITHDRAW, 1_000);
        expect(helper, 1_000, Wallet.value(purse(player)), "purse after a refused withdrawal");
        expect(helper, 1_234 - 1_000 - fee, bank.balance(player.getUUID()), "account after a refused withdrawal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void counterOutOfReachDoesNothing(final GameTestHelper helper)
    {
        final ServerPlayer player = customer(helper, 500);
        player.moveTo(player.getX() + 30, player.getY(), player.getZ());
        act(helper, player, BankActionPayload.DEPOSIT, 0);
        expect(helper, 500, Wallet.value(purse(player)), "purse of a player too far away");
        expect(helper, 0, ImperialBank.get(helper.getLevel().getServer()).balance(player.getUUID()), "account of a player too far away");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void withdrawalIntoAFullInventoryDropsTheRest(final GameTestHelper helper)
    {
        final ServerPlayer player = customer(helper, 0);
        ImperialBank.get(helper.getLevel().getServer()).credit(player.getUUID(), 200_000);
        // Every slot but one full of dirt: most of the coins cannot fit
        for (int slot = 1; slot < player.getInventory().items.size(); slot++)
        {
            player.getInventory().items.set(slot, new ItemStack(Items.DIRT, 64));
        }
        act(helper, player, BankActionPayload.WITHDRAW, 123_456);
        helper.runAfterDelay(2, () -> {
            expect(helper, 123_456, Wallet.value(purse(player)) + dropped(helper), "coins carried plus dropped");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void paymentWithoutColonyComesFromThePurse(final GameTestHelper helper)
    {
        final ServerPlayer player = customer(helper, 300);
        expect(helper, -1, TreasuryPayment.pay(player, null, 301) == null ? -1 : 0, "paying more than carried is refused");
        expect(helper, 300, Wallet.value(purse(player)), "purse after a refused payment");

        final TreasuryPayment payment = TreasuryPayment.pay(player, null, 120);
        if (payment == null)
        {
            helper.fail("a payment within the purse was refused");
            return;
        }
        expect(helper, 120, payment.fromPurse(), "share paid from the purse");
        expect(helper, 180, Wallet.value(purse(player)), "purse after paying 120");
        payment.refund(player, null);
        expect(helper, 300, Wallet.value(purse(player)), "purse after the refund");
        helper.succeed();
    }
}
