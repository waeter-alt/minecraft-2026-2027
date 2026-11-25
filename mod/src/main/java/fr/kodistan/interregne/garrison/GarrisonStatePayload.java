package fr.kodistan.interregne.garrison;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: what the recruiting page shows. {@code level} is the troop level (0 = hut not built), {@code count} the
 * guards of this kind, {@code total}/{@code cap} the whole garrison against its places.
 */
public record GarrisonStatePayload(int colony, int kind, int level, long price, long available, int count, int total, int cap, String notice)
  implements CustomPacketPayload
{
    public static final Type<GarrisonStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "garrison_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GarrisonStatePayload> STREAM_CODEC = StreamCodec.of(GarrisonStatePayload::write, GarrisonStatePayload::read);

    private static void write(final RegistryFriendlyByteBuf buf, final GarrisonStatePayload p)
    {
        buf.writeVarInt(p.colony);
        buf.writeVarInt(p.kind);
        buf.writeVarInt(p.level);
        buf.writeVarLong(p.price);
        buf.writeVarLong(p.available);
        buf.writeVarInt(p.count);
        buf.writeVarInt(p.total);
        buf.writeVarInt(p.cap);
        buf.writeUtf(p.notice);
    }

    private static GarrisonStatePayload read(final RegistryFriendlyByteBuf buf)
    {
        return new GarrisonStatePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
          buf.readVarInt(), buf.readVarInt(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
