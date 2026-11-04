package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: one colony's wall (key as RegionalTreasury.keyOf), so windows can show it. Level 0 means none.
 */
public record WallSyncPayload(String colony, List<Integer> xs, List<Integer> zs, boolean closed, int level, boolean building)
  implements CustomPacketPayload
{
    public static final Type<WallSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "wall_sync"));

    public static final StreamCodec<ByteBuf, WallSyncPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.STRING_UTF8, WallSyncPayload::colony,
      ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), WallSyncPayload::xs,
      ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), WallSyncPayload::zs,
      ByteBufCodecs.BOOL, WallSyncPayload::closed,
      ByteBufCodecs.VAR_INT, WallSyncPayload::level,
      ByteBufCodecs.BOOL, WallSyncPayload::building,
      WallSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
