package fr.kodistan.interregne.garrison;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server, from the combat academy or the archery page: {@link #OPEN} asks for the state, {@link #BUY} recruits one
 * guard of {@code kind} ({@link Garrison#LEGIONNAIRE} or {@link Garrison#ARCHERE}).
 */
public record GarrisonActionPayload(int action, int colony, int kind) implements CustomPacketPayload
{
    public static final int OPEN = 0;
    public static final int BUY = 1;

    public static final Type<GarrisonActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "garrison_action"));

    public static final StreamCodec<ByteBuf, GarrisonActionPayload> STREAM_CODEC = StreamCodec.of(GarrisonActionPayload::write, GarrisonActionPayload::read);

    private static void write(final ByteBuf buf, final GarrisonActionPayload p)
    {
        ByteBufCodecs.VAR_INT.encode(buf, p.action);
        ByteBufCodecs.VAR_INT.encode(buf, p.colony);
        ByteBufCodecs.VAR_INT.encode(buf, p.kind);
    }

    private static GarrisonActionPayload read(final ByteBuf buf)
    {
        return new GarrisonActionPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
