package fr.kodistan.interregne.commerce;

import fr.kodistan.interregne.InterregneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: something done at a colony's Relais commercial. {@code colony} is the player's colony.
 * <ul>
 *   <li>{@link #OPEN}: send the current state;</li>
 *   <li>{@link #OFFER}: offer {@code quantity} crates of {@code crate} to colony {@code other} at {@code price} each, with escort tier {@code escort} (0 for none);</li>
 *   <li>{@link #ACCEPT}, {@link #CANCEL}: answer or withdraw contract {@code contract}.</li>
 * </ul>
 */
public record RelaisActionPayload(int action, int colony, int other, long contract, int crate, int quantity, long price, int escort) implements CustomPacketPayload
{
    public static final int OPEN = 0;
    public static final int OFFER = 1;
    public static final int ACCEPT = 2;
    public static final int CANCEL = 3;

    public static final Type<RelaisActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InterregneMod.MODID, "relais_action"));

    public static final StreamCodec<ByteBuf, RelaisActionPayload> STREAM_CODEC = StreamCodec.of(RelaisActionPayload::write, RelaisActionPayload::read);

    private static void write(final ByteBuf buf, final RelaisActionPayload p)
    {
        ByteBufCodecs.VAR_INT.encode(buf, p.action);
        ByteBufCodecs.VAR_INT.encode(buf, p.colony);
        ByteBufCodecs.VAR_INT.encode(buf, p.other);
        ByteBufCodecs.VAR_LONG.encode(buf, p.contract);
        ByteBufCodecs.VAR_INT.encode(buf, p.crate);
        ByteBufCodecs.VAR_INT.encode(buf, p.quantity);
        ByteBufCodecs.VAR_LONG.encode(buf, p.price);
        ByteBufCodecs.VAR_INT.encode(buf, p.escort);
    }

    private static RelaisActionPayload read(final ByteBuf buf)
    {
        return new RelaisActionPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
          ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
