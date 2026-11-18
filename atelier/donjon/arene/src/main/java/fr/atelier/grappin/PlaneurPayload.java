// [claude-build] Message de la paravoile, dans les deux sens. Client -> serveur : le joueur deploie ou range sa voile (l'identifiant est ignore, le serveur
// prend le joueur qui envoie). Serveur -> clients : tel joueur plane ou ne plane plus ; sert au dessin de la voile et, pour soi, a imposer un arret (jauge vide, eau...).
// La classe ne touche aucun code client : le dessin et le pilotage sont dans ParavoileClient.
package fr.atelier.grappin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlaneurPayload(int idEntite, boolean actif) implements CustomPacketPayload {
    public static final Type<PlaneurPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Grappin.MODID, "planeur"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaneurPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlaneurPayload::idEntite,
            ByteBufCodecs.BOOL, PlaneurPayload::actif,
            PlaneurPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void traiter(PlaneurPayload message, IPayloadContext contexte) {
        if (contexte.flow().isServerbound()) {
            contexte.enqueueWork(() -> {
                if (contexte.player() instanceof ServerPlayer joueur) {
                    if (message.actif()) {
                        ParavoileEvenements.demarrer(joueur);
                    } else {
                        ParavoileEvenements.arreter(joueur);
                    }
                }
            });
        } else if (FMLEnvironment.dist.isClient()) {
            contexte.enqueueWork(() -> ParavoileClient.recevoir(message));
        }
    }
}
