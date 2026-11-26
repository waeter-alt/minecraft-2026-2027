// [claude-build] La poussee des courants de la Station, chez le joueur (bus du jeu, client) : s'il flotte (de l'eau sous les pieds : qui marche au fond, leste, n'est pas pousse) dans un bloc
// CourantStation, il est deplace de `vitesse` dixiemes de bloc par tick dans le sens du courant, par un vrai deplacement (collisions comprises), en plus de sa nage. C'est doux, contrairement a
// l'ancienne poussee du datapack (une teleportation a chaque tick) qui saccadait des qu'on nageait a contre-courant. La nage rapide de la cape (vitesse 0,45 a 0,7 bloc par tick) vainc un
// courant de 0,5 seulement en sprint : c'est le « boost » qu'il faut.
package fr.atelier.grappin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Grappin.MODID, value = Dist.CLIENT)
public final class CourantClient {
    private CourantClient() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post evenement) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer j = mc.player;
        if (j == null || mc.level == null || mc.isPaused() || j.isSpectator() || !j.isInWater()) {
            return;
        }
        Level niveau = j.level();
        BlockPos pos = j.blockPosition();
        BlockState etat = niveau.getBlockState(pos);
        if (!(etat.getBlock() instanceof CourantStation)) {
            etat = niveau.getBlockState(pos.above());
            if (!(etat.getBlock() instanceof CourantStation)) {
                return;
            }
        }
        if (!niveau.getFluidState(BlockPos.containing(j.getX(), j.getY() - 0.6, j.getZ())).is(FluidTags.WATER)) {
            return;                                                    // il marche au fond (leste) ou tient une echelle : le courant ne le pousse pas
        }
        Direction sens = etat.getValue(CourantStation.SENS);
        Vec3 pousse = new Vec3(sens.getStepX(), sens.getStepY(), sens.getStepZ()).scale(etat.getValue(CourantStation.VITESSE) / 10.0);
        if (!niveau.getFluidState(BlockPos.containing(j.position().add(pousse))).is(FluidTags.WATER)) {
            return;
        }
        j.move(MoverType.SELF, pousse);
    }
}
