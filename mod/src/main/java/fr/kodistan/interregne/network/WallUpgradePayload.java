package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: raise the colony's wall to its next level.
 */
public record WallUpgradePayload(int colony) implements CustomPacketPayload
{
    public static final Type<WallUpgradePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "wall_upgrade"));

    public static final StreamCodec<ByteBuf, WallUpgradePayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, WallUpgradePayload::colony,
      WallUpgradePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
