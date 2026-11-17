// [claude-build] Attaque tourbillon de la cape. Client -> serveur : le joueur la declenche (l'identifiant est ignore ; le serveur verifie tout :
// cape, eau, nage rapide, recharge, portee, degats). Serveur -> clients proches : tel joueur tourbillonne, pour jouer l'animation.
package fr.atelier.grappin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TourbillonPayload(int idEntite) implements CustomPacketPayload {
    public static final Type<TourbillonPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "tourbillon"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TourbillonPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TourbillonPayload::idEntite,
            TourbillonPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void traiter(TourbillonPayload message, IPayloadContext contexte) {
        if (contexte.flow().isServerbound()) {
            contexte.enqueueWork(() -> {
                if (contexte.player() instanceof ServerPlayer joueur) {
                    CapeEvenements.tourbillon(joueur);
                }
            });
        } else if (FMLEnvironment.dist.isClient()) {
            contexte.enqueueWork(() -> CapeClient.recevoirTourbillon(message));
        }
    }
}
