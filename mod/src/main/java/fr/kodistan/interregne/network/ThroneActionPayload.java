package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an action at the Trône impérial. Target and amount only matter for favor and disgrace.
 */
public record ThroneActionPayload(BlockPos throne, int action, String target, int amount) implements CustomPacketPayload
{
    public static final int DELIVER   = 0;
    public static final int CLEMENTE  = 1;
    public static final int ORDINAIRE = 2;
    public static final int ECRASANTE = 3;
    public static final int AMNESTY   = 4;
    public static final int FAVOR     = 5;
    public static final int DISGRACE  = 6;

    public static final Type<ThroneActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "throne_action"));

    public static final StreamCodec<ByteBuf, ThroneActionPayload> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, ThroneActionPayload::throne,
      ByteBufCodecs.VAR_INT, ThroneActionPayload::action,
      ByteBufCodecs.STRING_UTF8, ThroneActionPayload::target,
      ByteBufCodecs.VAR_INT, ThroneActionPayload::amount,
      ThroneActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
