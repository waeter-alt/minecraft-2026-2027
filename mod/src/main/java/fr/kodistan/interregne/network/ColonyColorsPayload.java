package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server to client: every colony's color (by colony id, for its citizens) and every online member's (for their name and glow).
 */
public record ColonyColorsPayload(Map<Integer, Integer> colonies, Map<UUID, Integer> players) implements CustomPacketPayload
{
    public static final Type<ColonyColorsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "colony_colors"));

    public static final StreamCodec<ByteBuf, ColonyColorsPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.INT), ColonyColorsPayload::colonies,
      ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.INT), ColonyColorsPayload::players,
      ColonyColorsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
