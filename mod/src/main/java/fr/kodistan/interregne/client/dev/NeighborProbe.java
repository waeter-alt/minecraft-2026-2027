package fr.kodistan.interregne.client.dev;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.ldtteam.structurize.items.ModItems;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.concurrent.Future;

import static net.minecraft.commands.Commands.literal;

/**
 * Dev probe: {@code /voisins} (client command) says, for every building near the player, what MineColonies' "neighbour at
 * maximum level" preview would try to draw: the path it derives, the pack it asks, whether that blueprint loads. It exists to
 * find why a building shows no such ghost.
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class NeighborProbe
{
    private NeighborProbe()
    {
    }

    @SubscribeEvent
    public static void onCommands(final RegisterClientCommandsEvent event)
    {
        event.getDispatcher().register(literal("voisins").executes(ctx ->
        {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null)
            {
                return 0;
            }
            final IColonyView colony = IColonyManager.getInstance().getClosestColonyView(mc.level, mc.player.blockPosition());
            say("main en main : " + mc.player.getMainHandItem().getItem() + " (outil de construction MineColonies : " + (mc.player.getMainHandItem().getItem() == ModItems.buildTool.get()) + ")");
            if (colony == null)
            {
                say("aucune colonie proche");
                return 0;
            }
            for (final IBuildingView view : colony.getBuildings())
            {
                if (view.getPosition().distSqr(mc.player.blockPosition()) > 60 * 60)
                {
                    continue;
                }
                final String id = view.getBuildingType().getRegistryName().getPath();
                final var tile = mc.level.getBlockEntity(view.getPosition());
                final String corners = tile instanceof TileEntityColonyBuilding hut ? hut.getInWorldCorners().getA().toShortString() + " / " + hut.getInWorldCorners().getB().toShortString() : "pas de TE ("
                                                                                                                                                                                                  + tile + ")";
                String path = view.getStructurePath().replace(".blueprint", "");
                final String derived = path.isEmpty() ? "(chemin vide)" : path.substring(0, path.length() - 1) + view.getBuildingMaxLevel() + ".blueprint";
                String loaded;
                try
                {
                    // Waits for the load (up to 30 s) and says how long it took: the preview shows nothing until then
                    final long start = System.nanoTime();
                    final Future<Blueprint> future = StructurePacks.getBlueprintFuture(view.getStructurePack(), derived, mc.level.registryAccess());
                    final Blueprint blueprint = future == null ? null : future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    loaded = (blueprint == null ? "nul" : "chargé " + blueprint.getSizeX() + "x" + blueprint.getSizeY() + "x" + blueprint.getSizeZ()) + " en "
                               + (System.nanoTime() - start) / 1_000_000 + " ms";
                }
                catch (final Exception e)
                {
                    loaded = "erreur " + e;
                }
                say(id + " niv " + view.getBuildingLevel() + "/" + view.getBuildingMaxLevel() + " pack=" + view.getStructurePack() + " chemin=" + view.getStructurePath()
                      + " -> " + derived + " [" + loaded + "] coins=" + corners);
            }
            return 1;
        }));
    }

    private static void say(final String text)
    {
        InterregneMod.LOGGER.info("[Voisins] {}", text);
        if (Minecraft.getInstance().player != null)
        {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(text));
        }
    }
}
