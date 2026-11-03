package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an operation at a Comptoir régional or a Marché impérial.
 * The crate (ordinal) and amount only matter at the market.
 */
public record RegionActionPayload(BlockPos block, int action, int crate, int amount) implements CustomPacketPayload
{
    /**
     * Pay for the Comptoir's next level.
     */
    public static final int COMPTOIR_UPGRADE = 0;

    /**
     * Take the crates kept inside the Comptoir.
     */
    public static final int COMPTOIR_TAKE = 1;

    public static final int MARKET_BUY = 2;

    public static final int MARKET_SELL = 3;

    public static final Type<RegionActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "region_action"));

    public static final StreamCodec<ByteBuf, RegionActionPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, RegionActionPayload::block,
      ByteBufCodecs.VAR_INT, RegionActionPayload::action,
      ByteBufCodecs.VAR_INT, RegionActionPayload::crate,
      ByteBufCodecs.VAR_INT, RegionActionPayload::amount,
      RegionActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
