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
 * The winner is the attacker after a {@link #CONQUEST} or a {@link #CAPITULATION}, the defender after an {@link #ABANDON};
 * for {@link #BREAK}, the colony that broke the alliance.
 *
 * @param outcome       how it ended: {@link #CONQUEST}, {@link #CAPITULATION}, {@link #ABANDON} or {@link #BREAK}
 * @param durationTicks from the declaration to the end
 * @param sent          attacking troops engaged, over all assaults
 * @param lost          of those, the ones that never came back
 * @param fallen        defending citizens killed
 * @param shieldTicks   how long nobody may declare war on the defender
 */
public record WarRecapPayload(boolean victory, int outcome, String winner, String loser, BannerPatternLayers winnerFlag,
                              BannerPatternLayers loserFlag, long durationTicks, int assaults, int sent, int lost, int destroyed, long loot,
                              int fallen, long tribute, long influence, long shieldTicks) implements CustomPacketPayload
{
    /** The loser's town hall fell. */
    public static final int CONQUEST     = 0;
    /** The loser paid the tribute. */
    public static final int CAPITULATION = 1;
    /** The attacker gave up: the defender wins. */
    public static final int ABANDON      = 2;
    /** An alliance was broken: no war, no figures. */
    public static final int BREAK        = 3;

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
    }, buf -> new WarRecapPayload(buf.readBoolean(), buf.readVarInt(), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
      BannerPatternLayers.STREAM_CODEC.decode(buf), BannerPatternLayers.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(),
      buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
      buf.readVarLong()));

    /** A war that ended with a winner and a loser (shield, Influence, gold or red tiles). */
    public boolean decisive()
    {
        return outcome != BREAK;
    }

    /** Whether the receiver's colony was the one that declared the war. */
    public boolean attacking()
    {
        return victory != (outcome == ABANDON);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
