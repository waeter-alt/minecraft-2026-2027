package fr.kodistan.interregne.network;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * From the town hall's Intendance page: ask for the current prices, settle a whole card at once, or settle one row
 * of its list (target: the citizen's id, or the supply's key).
 */
public record IntendanceActionPayload(int colony, int action, int target) implements CustomPacketPayload
{
    public static final int QUOTE = 0;
    public static final int SETTLE_GRIEVANCES = 1;
    public static final int SETTLE_SUPPLIES = 2;
    public static final int CURE = 3;
    public static final int FEED = 4;
    public static final int SUPPLY = 5;

    public IntendanceActionPayload(final int colony, final int action)
    {
        this(colony, action, 0);
    }

    public static final Type<IntendanceActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "intendance_action"));

    public static final StreamCodec<ByteBuf, IntendanceActionPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, IntendanceActionPayload::colony,
      ByteBufCodecs.VAR_INT, IntendanceActionPayload::action,
      ByteBufCodecs.INT, IntendanceActionPayload::target,
      IntendanceActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
