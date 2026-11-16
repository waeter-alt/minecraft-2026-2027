package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an alliance move from {@code colony} toward {@code target} (or, for a refresh, just a request for
 * the colonies list as this colony sees it).
 */
public record DiplomacyActionPayload(int colony, int action, int target) implements CustomPacketPayload
{
    public static final int REFRESH = 0;
    public static final int PROPOSE = 1;
    public static final int ACCEPT = 2;
    /** Refuses a received proposal or withdraws a sent one. */
    public static final int WITHDRAW = 3;
    public static final int BREAK = 4;

    public static final Type<DiplomacyActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "diplomacy_action"));

    public static final StreamCodec<ByteBuf, DiplomacyActionPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, DiplomacyActionPayload::colony,
      ByteBufCodecs.VAR_INT, DiplomacyActionPayload::action,
      ByteBufCodecs.VAR_INT, DiplomacyActionPayload::target,
      DiplomacyActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
