package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.modules.IEntityListModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Which monsters a guard building goes after: every monster as a card with its spawn egg, green when on, with a search
 * and bulk switches. Replaces MineColonies' monster list.
 */
public final class EntityListScreen extends ModuleScreen
{
    private static final int CARD_H = 20;

    private final String moduleId;
    private final List<ResourceLocation> all;

    private EntityListScreen(final IBuildingView building, final IEntityListModuleView module, @Nullable final Screen parent)
    {
        super(Component.translatable(module.getDesc().toLowerCase(Locale.US)).getString(), building, parent, 420);
        this.moduleId = module.getId();
        final List<ResourceLocation> monsters = new ArrayList<>(IColonyManager.getInstance().getCompatibilityManager().getAllMonsters());
        monsters.sort(Comparator.comparing(EntityListScreen::name));
        this.all = monsters;
    }

    public static void open(final IBuildingView building, final IEntityListModuleView module, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new EntityListScreen(building, module, parent));
    }

    @Override
    protected void init()
    {
        super.init();
        addSearch(24);
    }

    @Nullable
    private IEntityListModuleView module()
    {
        return building.getModuleViewMatching(IEntityListModuleView.class, view -> view.getId().equals(moduleId));
    }

    private static String name(final ResourceLocation id)
    {
        return BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString();
    }

    private static ItemStack egg(final ResourceLocation id)
    {
        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        final SpawnEggItem egg = SpawnEggItem.byId(type);
        return new ItemStack(egg == null ? Items.ZOMBIE_HEAD : egg);
    }

    private static boolean on(final IEntityListModuleView module, final ResourceLocation id)
    {
        return module.isInverted() != module.isAllowedEntity(id);
    }

    private static void set(final IEntityListModuleView module, final ResourceLocation id, final boolean on)
    {
        if (on(module, id) == on)
        {
            return;
        }
        if (on != module.isInverted())
        {
            module.addEntity(id);
        }
        else
        {
            module.removeEntity(id);
        }
    }

    private List<ResourceLocation> visible()
    {
        final String query = query();
        return all.stream().filter(id -> query.isEmpty() || name(id).toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final IEntityListModuleView module = module();
        if (module == null)
        {
            return y;
        }
        final long count = all.stream().filter(id -> on(module, id)).count();
        Ui.banner(graphics, font, x, y, width, count == 0 ? Ui.State.WAIT : Ui.State.OK, count + " sur " + all.size() + (count > 1 ? " cochés" : " coché"),
          count == 0 ? "les gardes ne visent aucun monstre" : "les gardes s'en prennent aux monstres cochés");
        y += 18;
        final List<ResourceLocation> visible = visible();
        int bx = x;
        bx += button(graphics, mouseX, mouseY, bx, y, "Tout cocher", Ui.ButtonStyle.SECONDARY, !visible.isEmpty(), null,
          () -> visible.forEach(id -> set(module, id, true))) + 3;
        button(graphics, mouseX, mouseY, bx, y, "Tout décocher", Ui.ButtonStyle.SECONDARY, !visible.isEmpty(), null, () -> visible.forEach(id -> set(module, id, false)));
        return y + 19;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        final IEntityListModuleView module = module();
        if (module == null)
        {
            return y;
        }
        final List<ResourceLocation> visible = visible();
        if (visible.isEmpty())
        {
            graphics.drawString(font, "Aucun monstre ne correspond à « " + query() + " ».", x + 2, y + 2, Palette.MUTED, false);
            return y + 14;
        }
        final int columnW = (width - 3) / 2;
        for (int i = 0; i < visible.size(); i++)
        {
            final ResourceLocation id = visible.get(i);
            final int cx = x + (i % 2) * (columnW + 3);
            final int cy = y + (i / 2) * (CARD_H + 2);
            final boolean on = on(module, id);
            final boolean over = Ui.inside(mouseX, mouseY, cx, cy, columnW, CARD_H);
            final int fill = on ? (over ? Palette.GREEN_LIGHT : Palette.GREEN_SOFT) : (over ? Palette.CARD_HOVER : Palette.PAPER_DARK);
            Ui.plate(graphics, cx, cy, cx + columnW, cy + CARD_H, fill, on ? Palette.GREEN : Palette.RULE, fill);
            Icon.of(egg(id)).draw(graphics, cx + 3, cy + 2, 16);
            final String mark = on ? "✔" : "✕";
            graphics.drawString(font, mark, cx + columnW - 4 - font.width(mark), cy + 6, on ? Palette.GREEN : Palette.MUTED, false);
            graphics.drawString(font, fit(name(id), columnW - 40), cx + 22, cy + 6, on ? Palette.INK : Palette.MUTED, false);
            addHit(cx, cy, columnW, CARD_H, () -> set(module, id, !on));
        }
        return y + ((visible.size() + 1) / 2) * (CARD_H + 2);
    }
}
