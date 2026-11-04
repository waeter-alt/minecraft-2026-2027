package fr.kodistan.interregne.dev;

import com.minecolonies.api.colony.IColony;
import com.mojang.authlib.GameProfile;
import fr.kodistan.interregne.colony.IntendanceDesk;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.IntendanceActionPayload;
import fr.kodistan.interregne.network.IntendanceQuotePayload;
import fr.kodistan.interregne.politics.Influence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * « Tout régler » must settle a whole card in one payment. A rich fake player settles the supplies card, then the
 * grievances card, and the measures show whether each card empties at once or one row at a time.
 */
public class IntendanceScenario implements Scenario
{
    private ServerPlayer payer;
    private int step;
    private int suppliesBefore;
    private int suppliesAfter = -1;
    private int careBefore;
    private int careAfter = -1;
    private boolean oneRow;

    @Override
    public List<String> setup(final IColony colony, final ServerLevel level)
    {
        payer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "intendant-test"));
        Influence.add(level.getServer(), payer.getUUID(), 100_000, "test");
        Wallet.give(new PlayerMainInvWrapper(payer.getInventory()), 1_000_000, stack -> {});
        final IntendanceQuotePayload quote = IntendanceDesk.quote(colony);
        suppliesBefore = quote.supplies().size();
        careBefore = quote.care().size();
        return List.of("payer with 1 000 000 Couronnes and 100 000 Influence", "quote: " + describe(quote));
    }

    @Override
    public String snapshot(final IColony colony, final ServerLevel level)
    {
        // One action per measure: supplies at the 2nd, grievances at the 4th
        step++;
        String did = "";
        if (step == 2)
        {
            IntendanceDesk.settle(payer, colony, IntendanceActionPayload.SETTLE_SUPPLIES);
            suppliesAfter = IntendanceDesk.quote(colony).supplies().size();
            did = "SETTLED SUPPLIES -> ";
        }
        else if (step == 3 && !IntendanceDesk.quote(colony).care().isEmpty())
        {
            // One row alone, as the list's own button does
            final IntendanceQuotePayload.Care row = IntendanceDesk.quote(colony).care().getFirst();
            final int before = IntendanceDesk.quote(colony).care().size();
            IntendanceDesk.settleCitizen(payer, colony, row.citizen(), row.sick());
            oneRow = IntendanceDesk.quote(colony).care().size() == before - 1;
            did = "SETTLED ONE ROW (" + row.name() + (row.sick() ? ", cure" : ", meal") + ") -> ";
        }
        else if (step == 4)
        {
            IntendanceDesk.settle(payer, colony, IntendanceActionPayload.SETTLE_GRIEVANCES);
            careAfter = IntendanceDesk.quote(colony).care().size();
            did = "SETTLED GRIEVANCES -> ";
        }
        return did + describe(IntendanceDesk.quote(colony)) + " | payer gold " + Wallet.format(Wallet.value(new PlayerMainInvWrapper(payer.getInventory())));
    }

    private static String describe(final IntendanceQuotePayload quote)
    {
        final long rare = quote.supplies().stream().filter(IntendanceQuotePayload.Supply::rare).count();
        final List<String> items = new ArrayList<>();
        quote.supplies().forEach(s -> items.add(s.stack().getCount() + " " + s.stack().getHoverName().getString() + (s.rare() ? " (rare)" : " " + s.cost())));
        return "care " + quote.care().size() + " rows / " + quote.careGold() + " gold"
          + " | supplies " + quote.supplies().size() + " rows (" + rare + " rare) / " + quote.supplyGold() + " gold " + items;
    }

    @Override
    public List<String> verdict(final IColony colony, final ServerLevel level)
    {
        final IntendanceQuotePayload end = IntendanceDesk.quote(colony);
        final long rareLeft = end.supplies().stream().filter(IntendanceQuotePayload.Supply::rare).count();
        final List<String> lines = new ArrayList<>();
        lines.add((suppliesAfter >= 0 && end.supplies().size() - rareLeft == 0 ? "PASS " : "FAIL ") + "one click settles every buyable supply ("
          + suppliesBefore + " rows before, " + suppliesAfter + " right after, " + end.supplies().size() + " at the end, " + rareLeft + " rare)");
        lines.add((careAfter == 0 ? "PASS " : "FAIL ") + "one click settles every grievance (" + careBefore + " before, " + careAfter + " right after)");
        lines.add((oneRow ? "PASS " : "FAIL ") + "a row's own button settles just that row");
        return lines;
    }
}
