package fr.kodistan.interregne.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

public final class Net
{
    /**
     * Registers a server-to-client payload whose handler only runs on a client.
     * Pass a lambda ({@code p -> ClientWalls.put(p)}), never a method reference: a method reference to a
     * client class is linked at registration time and would load that class on a dedicated server.
     */
    public static <T extends CustomPacketPayload> void toClient(
      final PayloadRegistrar registrar,
      final CustomPacketPayload.Type<T> type,
      final StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
      final Consumer<T> onClient)
    {
        registrar.playToClient(type, codec, (payload, context) -> {
            if (FMLEnvironment.dist.isClient())
            {
                onClient.accept(payload);
            }
        });
    }

    private Net()
    {
    }
}
