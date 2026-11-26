package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, sent just before the death screen: this death happened during an assault, the player waits
 * {@code waitTicks} before respawning at {@code where} (a town hall, in words), and it is their {@code death}-th death of it.
 */
public record AssaultDeathPayload(int waitTicks, int death, String where) implements CustomPacketPayload
{
    public static final Type<AssaultDeathPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "assault_death"));

    public static final StreamCodec<ByteBuf, AssaultDeathPayload> STREAM_CODEC =
      StreamCodec.composite(ByteBufCodecs.VAR_INT, AssaultDeathPayload::waitTicks, ByteBufCodecs.VAR_INT, AssaultDeathPayload::death,
        ByteBufCodecs.STRING_UTF8, AssaultDeathPayload::where, AssaultDeathPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
