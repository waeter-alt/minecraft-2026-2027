package fr.kodistan.interregne.colony;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;

/**
 * /interregne demandes: the workers nearest the player each ask for something, to try the request screens without
 * waiting for a real shortage. What the warehouse cannot supply lands on the clipboard.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class TestRequests
{
    private static final List<ItemStack> ASKED = List.of(
      new ItemStack(Items.BREAD, 16),
      new ItemStack(Items.IRON_PICKAXE),
      new ItemStack(Items.OAK_LOG, 32),
      new ItemStack(Items.TORCH, 24));

    private TestRequests()
    {
    }

    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("interregne")
          .then(Commands.literal("demandes").requires(source -> source.hasPermission(2)).executes(context -> {
              final ServerPlayer player = context.getSource().getPlayerOrException();
              final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.serverLevel(), player.blockPosition());
              if (colony == null)
              {
                  context.getSource().sendFailure(Component.literal("Place-toi dans une colonie."));
                  return 0;
              }
              final List<ICitizenData> workers = colony.getCitizenManager().getCitizens().stream()
                .filter(citizen -> citizen.getWorkBuilding() != null)
                .sorted(Comparator.comparingDouble(citizen -> citizen.getWorkBuilding().getPosition().distSqr(player.blockPosition())))
                .limit(ASKED.size())
                .toList();
              if (workers.isEmpty())
              {
                  context.getSource().sendFailure(Component.literal("Aucun ouvrier dans cette colonie : embauche-en un d'abord."));
                  return 0;
              }
              for (int i = 0; i < workers.size(); i++)
              {
                  final ICitizenData citizen = workers.get(i);
                  final IBuilding building = citizen.getWorkBuilding();
                  final ItemStack stack = ASKED.get(i);
                  building.createRequest(citizen, new Stack(stack.copy(), stack.getCount(), stack.getCount()), false);
              }
              final int made = workers.size();
              context.getSource().sendSuccess(() -> Component.literal(made + (made > 1 ? " demandes créées" : " demande créée")
                                                                        + " : regarde le presse-papiers ou la fiche des ouvriers."), false);
              return made;
          })));
    }
}
