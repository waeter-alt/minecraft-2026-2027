package fr.kodistan.interregne.commerce;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: what a colony's Relais commercial shows. {@code notice} is the server's answer to the last action
 * (a refusal, or empty).
 */
public record RelaisStatePayload(int colony, String colonyName, int level, int capacity, long available, String notice, List<Partner> partners, List<Row> contracts,
  List<Tier> tiers, Imperial imperial)
  implements CustomPacketPayload
{
    /**
     * A colony this one could trade with.
     *
     * @param ready true when it is allied and a road joins the two
     */
    public record Partner(int id, String name, boolean ready)
    {
    }

    /**
     * One contract, seen from this colony.
     *
     * @param percent how far the caravan has walked, 0 to 100
     * @param escort  the escort tier bought for it, 0 for none
     * @param soldiers how many of its soldiers are still alive
     */
    public record Row(long id, int seller, String sellerName, int buyer, String buyerName, int crate, int quantity, long unitPrice, int state, int percent, int escort,
      int soldiers)
    {
    }

    /**
     * One escort tier (index 0 is tier 1): what it costs per contract and who it hires.
     */
    public record Tier(long cost, int swordsmen, int archers)
    {
    }

    /**
     * What the colony needs to sell to the Empire: the destinations and the state of its road to each, the price of opening
     * one, the largest caravan, and the Empire's stock and price curve so the client can estimate a sale itself.
     *
     * @param treasury the regional treasury, which pays the road
     * @param stock    the Empire's stock of each crate, in {@code Crate} order
     * @param curve    {baseStock, baseSell, elasticity, minFactor, maxFactor} (see {@code ImperialMarket.sellCurve})
     */
    public record Imperial(long routeCost, long treasury, int maxCrates, int[] stock, double[] curve, List<Dock> docks)
    {
        public static final Imperial NONE = new Imperial(0, 0, 0, new int[0], new double[5], List.of());
    }

    /**
     * One Marché impérial a road can lead to.
     *
     * @param pos      its packed position
     * @param distance blocks from the colony
     * @param state    {@link #CLOSED}, {@link #BUILDING} or {@link #OPEN}
     */
    public record Dock(long pos, int distance, int state)
    {
        public static final int CLOSED = 0;
        public static final int BUILDING = 1;
        public static final int OPEN = 2;
    }

    public static final Type<RelaisStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "relais_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RelaisStatePayload> STREAM_CODEC = StreamCodec.of(RelaisStatePayload::write, RelaisStatePayload::read);

    private static void write(final RegistryFriendlyByteBuf buf, final RelaisStatePayload p)
    {
        buf.writeVarInt(p.colony);
        buf.writeUtf(p.colonyName);
        buf.writeVarInt(p.level);
        buf.writeVarInt(p.capacity);
        buf.writeVarLong(p.available);
        buf.writeUtf(p.notice);
        buf.writeVarInt(p.partners.size());
        for (final Partner partner : p.partners)
        {
            buf.writeVarInt(partner.id);
            buf.writeUtf(partner.name);
            buf.writeBoolean(partner.ready);
        }
        buf.writeVarInt(p.contracts.size());
        for (final Row row : p.contracts)
        {
            buf.writeVarLong(row.id);
            buf.writeVarInt(row.seller);
            buf.writeUtf(row.sellerName);
            buf.writeVarInt(row.buyer);
            buf.writeUtf(row.buyerName);
            buf.writeVarInt(row.crate);
            buf.writeVarInt(row.quantity);
            buf.writeVarLong(row.unitPrice);
            buf.writeVarInt(row.state);
            buf.writeVarInt(row.percent);
            buf.writeVarInt(row.escort);
            buf.writeVarInt(row.soldiers);
        }
        buf.writeVarInt(p.tiers.size());
        for (final Tier tier : p.tiers)
        {
            buf.writeVarLong(tier.cost);
            buf.writeVarInt(tier.swordsmen);
            buf.writeVarInt(tier.archers);
        }
        buf.writeVarLong(p.imperial.routeCost);
        buf.writeVarLong(p.imperial.treasury);
        buf.writeVarInt(p.imperial.maxCrates);
        buf.writeVarInt(p.imperial.stock.length);
        for (final int stock : p.imperial.stock)
        {
            buf.writeVarInt(stock);
        }
        for (final double value : p.imperial.curve)
        {
            buf.writeDouble(value);
        }
        buf.writeVarInt(p.imperial.docks.size());
        for (final Dock dock : p.imperial.docks)
        {
            buf.writeVarLong(dock.pos);
            buf.writeVarInt(dock.distance);
            buf.writeVarInt(dock.state);
        }
    }

    private static RelaisStatePayload read(final RegistryFriendlyByteBuf buf)
    {
        final int colony = buf.readVarInt();
        final String name = buf.readUtf();
        final int level = buf.readVarInt();
        final int capacity = buf.readVarInt();
        final long available = buf.readVarLong();
        final String notice = buf.readUtf();
        final List<Partner> partners = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--)
        {
            partners.add(new Partner(buf.readVarInt(), buf.readUtf(), buf.readBoolean()));
        }
        final List<Row> rows = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--)
        {
            rows.add(new Row(buf.readVarLong(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
              buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        final List<Tier> tiers = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--)
        {
            tiers.add(new Tier(buf.readVarLong(), buf.readVarInt(), buf.readVarInt()));
        }
        final long routeCost = buf.readVarLong();
        final long treasury = buf.readVarLong();
        final int maxCrates = buf.readVarInt();
        final int[] stock = new int[buf.readVarInt()];
        for (int i = 0; i < stock.length; i++)
        {
            stock[i] = buf.readVarInt();
        }
        final double[] curve = new double[5];
        for (int i = 0; i < curve.length; i++)
        {
            curve[i] = buf.readDouble();
        }
        final List<Dock> docks = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--)
        {
            docks.add(new Dock(buf.readVarLong(), buf.readVarInt(), buf.readVarInt()));
        }
        return new RelaisStatePayload(colony, name, level, capacity, available, notice, partners, rows, tiers, new Imperial(routeCost, treasury, maxCrates, stock, curve, docks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
