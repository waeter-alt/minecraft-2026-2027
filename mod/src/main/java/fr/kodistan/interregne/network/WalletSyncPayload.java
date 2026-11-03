package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the player's Couronnes at the Imperial Bank and in their colony's regional treasury, for the HUD.
 */
public record WalletSyncPayload(long bank, long regional) implements CustomPacketPayload
{
    public static final Type<WalletSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "wallet_sync"));

    public static final StreamCodec<ByteBuf, WalletSyncPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_LONG, WalletSyncPayload::bank,
      ByteBufCodecs.VAR_LONG, WalletSyncPayload::regional,
      WalletSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
