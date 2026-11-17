package fr.kodistan.interregne.commerce;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: what the kingdom map shows to every player, the colonies and the caravans on the road right now. The
 * roads themselves already reach every client with the road lines packet.
 */
public record MapStatePayload(List<Colony> colonies, List<Caravan> caravans) implements CustomPacketPayload
{
    public record Colony(int id, String name, int x, int z)
    {
    }

    /**
     * @param label   what it carries and between whom, ready to print
     * @param yaw     the way it walks, in Minecraft degrees (0 faces +Z, 90 faces -X)
     * @param percent how far along its road, 0 to 100
     * @param escort  the escort tier, 0 for none
     * @param fighting whether its escort is fighting right now
     */
    public record Caravan(long id, int seller, int buyer, String label, int x, int z, int yaw, int percent, int escort, boolean fighting)
    {
    }

    public static final Type<MapStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "map_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapStatePayload> STREAM_CODEC = StreamCodec.of(MapStatePayload::write, MapStatePayload::read);

    private static void write(final RegistryFriendlyByteBuf buf, final MapStatePayload p)
    {
        buf.writeVarInt(p.colonies.size());
        for (final Colony colony : p.colonies)
        {
            buf.writeVarInt(colony.id);
            buf.writeUtf(colony.name);
            buf.writeInt(colony.x);
            buf.writeInt(colony.z);
        }
        buf.writeVarInt(p.caravans.size());
        for (final Caravan caravan : p.caravans)
        {
            buf.writeVarLong(caravan.id);
            buf.writeVarInt(caravan.seller);
            buf.writeVarInt(caravan.buyer);
            buf.writeUtf(caravan.label);
            buf.writeInt(caravan.x);
            buf.writeInt(caravan.z);
            buf.writeVarInt(caravan.yaw + 360);
            buf.writeVarInt(caravan.percent);
            buf.writeVarInt(caravan.escort);
            buf.writeBoolean(caravan.fighting);
        }
    }

    private static MapStatePayload read(final RegistryFriendlyByteBuf buf)
    {
        final List<Colony> colonies = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--)
        {
            colonies.add(new Colony(buf.readVarInt(), buf.readUtf(), buf.readInt(), buf.readInt()));
        }
        final List<Caravan> caravans = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--)
        {
            caravans.add(new Caravan(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readVarInt() - 360, buf.readVarInt(),
              buf.readVarInt(), buf.readBoolean()));
        }
        return new MapStatePayload(colonies, caravans);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
