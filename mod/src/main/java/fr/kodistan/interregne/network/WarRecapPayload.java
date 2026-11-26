package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Server to client, when a war ends (or an alliance breaks): everything the recap screen shows, sent to each online
 * member of both colonies ({@code victory} from the receiver's side). Figures are the whole war's.
 * <p>
 * The winner is the attacker after a {@link #CONQUEST} or a {@link #CAPITULATION}, the defender after an {@link #ABANDON}
 * or a {@link #REDDITION};
 * for {@link #BREAK}, the colony that broke the alliance.
 *
 * @param outcome       how it ended: {@link #CONQUEST}, {@link #CAPITULATION}, {@link #ABANDON}, {@link #REDDITION} or {@link #BREAK}
 * @param durationTicks from the declaration to the end
 * @param sent          attacking troops engaged, over all assaults
 * @param lost          of those, the ones that never came back
 * @param fallen        defending citizens killed
 * @param shieldTicks   how long nobody may declare war on the defender
 * @param aidReceived   allies' soldiers that came to defend the defender (Aide de clan)
 * @param aidFallen     of those, the ones that fell
 */
public record WarRecapPayload(boolean victory, int outcome, String winner, String loser, BannerPatternLayers winnerFlag,
                              BannerPatternLayers loserFlag, long durationTicks, int assaults, int sent, int lost, int destroyed, long loot,
                              int fallen, long tribute, long influence, long shieldTicks, int aidReceived, int aidFallen) implements CustomPacketPayload
{
    public WarRecapPayload(final boolean victory, final int outcome, final String winner, final String loser, final BannerPatternLayers winnerFlag,
                           final BannerPatternLayers loserFlag, final long durationTicks, final int assaults, final int sent, final int lost, final int destroyed,
                           final long loot, final int fallen, final long tribute, final long influence, final long shieldTicks)
    {
        this(victory, outcome, winner, loser, winnerFlag, loserFlag, durationTicks, assaults, sent, lost, destroyed, loot, fallen, tribute, influence,
          shieldTicks, 0, 0);
    }

    /** The loser's town hall fell. */
    public static final int CONQUEST     = 0;
    /** The loser paid the tribute. */
    public static final int CAPITULATION = 1;
    /** The attacker gave up: the defender wins. */
    public static final int ABANDON      = 2;
    /** An alliance was broken: no war, no figures. */
    public static final int BREAK        = 3;
    /** The defender demanded the attacker's surrender: the defender wins, the attacker pays a tribute. */
    public static final int REDDITION    = 4;

    public static final Type<WarRecapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "war_recap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WarRecapPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.victory());
        buf.writeVarInt(p.outcome());
        ByteBufCodecs.STRING_UTF8.encode(buf, p.winner());
        ByteBufCodecs.STRING_UTF8.encode(buf, p.loser());
        BannerPatternLayers.STREAM_CODEC.encode(buf, p.winnerFlag());
        BannerPatternLayers.STREAM_CODEC.encode(buf, p.loserFlag());
        buf.writeVarLong(p.durationTicks());
        buf.writeVarInt(p.assaults());
        buf.writeVarInt(p.sent());
        buf.writeVarInt(p.lost());
        buf.writeVarInt(p.destroyed());
        buf.writeVarLong(p.loot());
        buf.writeVarInt(p.fallen());
        buf.writeVarLong(p.tribute());
        buf.writeVarLong(p.influence());
        buf.writeVarLong(p.shieldTicks());
        buf.writeVarInt(p.aidReceived());
        buf.writeVarInt(p.aidFallen());
    }, buf -> new WarRecapPayload(buf.readBoolean(), buf.readVarInt(), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
      BannerPatternLayers.STREAM_CODEC.decode(buf), BannerPatternLayers.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(),
      buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
      buf.readVarLong(), buf.readVarInt(), buf.readVarInt()));

    /** A war that ended with a winner and a loser (shield, Influence, gold or red tiles). */
    public boolean decisive()
    {
        return outcome != BREAK;
    }

    /** Whether the receiver's colony was the one that declared the war. */
    public boolean attacking()
    {
        return victory != (outcome == ABANDON || outcome == REDDITION);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
