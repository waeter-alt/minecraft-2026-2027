package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an alliance move from {@code colony} toward {@code target} (or, for a refresh, just a request for
 * the colonies list as this colony sees it). {@code amount} carries the troops of an assault, see {@link #troops}.
 */
public record DiplomacyActionPayload(int colony, int action, int target, int amount) implements CustomPacketPayload
{
    public static final int REFRESH = 0;
    public static final int PROPOSE = 1;
    public static final int ACCEPT = 2;
    /** Refuses a received proposal or withdraws a sent one. */
    public static final int WITHDRAW = 3;
    public static final int BREAK = 4;
    public static final int DECLARE_WAR = 5;
    /** Ends a war this colony declared. */
    public static final int MAKE_PEACE = 6;
    /** Sends troops of the bought garrison on the enemy, along the war road. */
    public static final int LAUNCH_ASSAULT = 7;
    /** Calls back the troops marching on or assaulting the enemy. */
    public static final int RECALL_TROOPS = 8;
    /** The defender gives up: pays the tribute and loses the war. */
    public static final int CAPITULATE = 9;

    /** Packs an assault's troops into {@code amount}. */
    public static int troops(final int legionnaires, final int archers)
    {
        return legionnaires * 1000 + archers;
    }

    public static final Type<DiplomacyActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "diplomacy_action"));

    public static final StreamCodec<ByteBuf, DiplomacyActionPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, DiplomacyActionPayload::colony,
      ByteBufCodecs.VAR_INT, DiplomacyActionPayload::action,
      ByteBufCodecs.VAR_INT, DiplomacyActionPayload::target,
      ByteBufCodecs.VAR_INT, DiplomacyActionPayload::amount,
      DiplomacyActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
