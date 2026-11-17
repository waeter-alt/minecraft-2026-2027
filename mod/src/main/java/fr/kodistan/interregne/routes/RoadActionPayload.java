package fr.kodistan.interregne.routes;

import fr.kodistan.interregne.InterregneMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: something done on the road screen of a Poste commercial. The server finds the road itself from the
 * post, it never trusts a road number from the client.
 */
public record RoadActionPayload(BlockPos post, int action) implements CustomPacketPayload
{
    /** Send the current state. */
    public static final int OPEN = 0;
    /** Pay and start the works for the next level. */
    public static final int UPGRADE = 1;

    public static final Type<RoadActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "road_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoadActionPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, RoadActionPayload::post,
      ByteBufCodecs.VAR_INT, RoadActionPayload::action,
      RoadActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
