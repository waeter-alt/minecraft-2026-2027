package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog;
import com.minecolonies.core.colony.buildings.moduleviews.ExpeditionLogModuleView;
import com.minecolonies.core.network.messages.server.colony.building.MarkBuildingDirtyMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Nether worker's last expedition: who went and how it went, their health and food, what they carried, what they
 * fought and what they brought back. Replaces MineColonies' expedition log.
 */
public final class ExpeditionScreen extends ModuleScreen
{
    private final int moduleId;
    private ExpeditionLogModuleView module;
    private int refresh;

    private ExpeditionScreen(final IBuildingView building, final ExpeditionLogModuleView module, @Nullable final Screen parent)
    {
        super("Expédition", building, parent, 360);
        this.module = module;
        this.moduleId = module.getProducer().getRuntimeID();
    }

    public static void open(final IBuildingView building, final ExpeditionLogModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new ExpeditionScreen(building, module, parent));
    }

    @Override
    public void tick()
    {
        super.tick();
        final ExpeditionLogModuleView fresh = building.getModuleViewMatching(ExpeditionLogModuleView.class, m -> m.getProducer().getRuntimeID() == moduleId);
        if (fresh != null)
        {
            module = fresh;
        }
        // As MineColonies' page: ask the server for news every second
        if (refresh-- <= 0)
        {
            refresh = 20;
            new MarkBuildingDirtyMessage(building).sendToServer();
        }
    }

    private static String status(final ExpeditionLog.Status status)
    {
        return switch (status)
        {
            case NONE -> "Aucune expédition pour l'instant";
            case STARTING -> "Sur le départ";
            case IN_PROGRESS -> "En route dans le Nether";
            case RETURNING_HOME -> "Sur le chemin du retour";
            case COMPLETED -> "Rentré sain et sauf";
            case KILLED -> "Mort pendant l'expédition";
        };
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final ExpeditionLog log = module.getLog();
        final ExpeditionLog.Status status = log.getStatus();
        final Ui.State state = status == ExpeditionLog.Status.KILLED ? Ui.State.BAD : status == ExpeditionLog.Status.COMPLETED || status == ExpeditionLog.Status.NONE ? Ui.State.OK
                                                                                                                               : Ui.State.WAIT;
        final String who = log.getName() == null || log.getName().isEmpty() ? "Expédition" : log.getName();
        y += Ui.banner(graphics, font, x, y, width, state, who, status(status).toLowerCase(java.util.Locale.FRENCH));
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final ExpeditionLog log = module.getLog();
        if (log.getStatus() == ExpeditionLog.Status.NONE)
        {
            return Ui.wrapped(graphics, font, Component.literal("Le journal se remplit au prochain départ."), x + 2, y + 2, width - (2), Palette.MUTED, 10) + 4;
        }
        // Health and food
        final int health = (int) log.getStat(ExpeditionLog.StatType.HEALTH);
        final int food = (int) log.getStat(ExpeditionLog.StatType.SATURATION);
        graphics.drawString(font, Component.literal("❤ " + health + " / 20").withStyle(ChatFormatting.BOLD), x + 2, y, Palette.RED, false);
        graphics.drawString(font, Component.literal("🍖 " + food + " / 20").withStyle(ChatFormatting.BOLD), x + 80, y, Palette.AMBER_TEXT, false);
        y += 14;

        y = items(graphics, mouseY, x, y, width, "ÉQUIPEMENT", log.getEquipment());

        final List<Tuple<EntityType<?>, Integer>> mobs = log.getMobs();
        Ui.label(graphics, font, "COMBATS", x, y);
        y += 11;
        if (mobs.isEmpty())
        {
            graphics.drawString(font, "Aucun.", x + 2, y, Palette.MUTED, false);
            y += 13;
        }
        else
        {
            int cx = x;
            for (final Tuple<EntityType<?>, Integer> mob : mobs)
            {
                final SpawnEggItem egg = SpawnEggItem.byId(mob.getA());
                Icon.of(new ItemStack(egg == null ? Items.ZOMBIE_HEAD : egg)).draw(graphics, cx, y, 16);
                final String text = mob.getA().getDescription().getString() + " ×" + mob.getB();
                graphics.drawString(font, text, cx + 18, y + 4, Palette.INK, false);
                cx += 18 + font.width(text) + 10;
                if (cx > x + width - 80)
                {
                    cx = x;
                    y += 18;
                }
            }
            y += 20;
        }

        return items(graphics, mouseY, x, y, width, "BUTIN", log.getLoot().stream().map(s -> {
            final ItemStack stack = s.getItemStack().copy();
            stack.setCount(s.getAmount());
            return stack;
        }).toList());
    }

    private int items(final GuiGraphics graphics, final int mouseY, final int x, int y, final int width, final String label, final List<ItemStack> stacks)
    {
        Ui.label(graphics, font, label, x, y);
        y += 11;
        if (stacks.isEmpty())
        {
            graphics.drawString(font, "Rien.", x + 2, y, Palette.MUTED, false);
            return y + 14;
        }
        final int columns = Math.max(1, width / 18);
        for (int i = 0; i < stacks.size(); i++)
        {
            final ItemStack stack = stacks.get(i);
            final int cx = x + (i % columns) * 18;
            final int cy = y + (i / columns) * 18;
            Icon.of(stack).draw(graphics, cx, cy, 16);
            graphics.renderItemDecorations(font, stack, cx, cy);
            addSlot(cx, cy, 16, stack, mouseY);
        }
        return y + ((stacks.size() + columns - 1) / columns) * 18 + 4;
    }
}
