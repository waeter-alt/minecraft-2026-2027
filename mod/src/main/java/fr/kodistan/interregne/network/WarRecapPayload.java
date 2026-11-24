package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Server to client, when a war ends in victory or capitulation: everything the victory / defeat recap shows, sent to
 * each online member of both colonies ({@code victory} from the receiver's side). Figures are the whole war's.
 *
 * @param capitulation the loser paid the tribute instead of losing its town hall
 * @param durationTicks from the declaration to the end
 * @param sent          attacking troops engaged, over all assaults
 * @param lost          of those, the ones that never came back
 * @param fallen        defending citizens killed
 * @param shieldTicks   how long nobody may declare war on the loser
 */
public record WarRecapPayload(boolean victory, boolean capitulation, String winner, String loser, BannerPatternLayers winnerFlag,
                              BannerPatternLayers loserFlag, long durationTicks, int assaults, int sent, int lost, int destroyed, long loot,
                              int fallen, long tribute, long influence, long shieldTicks) implements CustomPacketPayload
{
    public static final Type<WarRecapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "war_recap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WarRecapPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.victory());
        buf.writeBoolean(p.capitulation());
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
    }, buf -> new WarRecapPayload(buf.readBoolean(), buf.readBoolean(), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
      BannerPatternLayers.STREAM_CODEC.decode(buf), BannerPatternLayers.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(),
      buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
      buf.readVarLong()));

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
