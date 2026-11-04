package fr.kodistan.interregne.client.blocks;

import fr.kodistan.interregne.client.ClientFarmCrops;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.PageScreen;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.network.FarmCropPayload;
import fr.kodistan.interregne.network.OpenFarmCropPayload;
import fr.kodistan.interregne.production.Farms.Crop;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Picking a crop, in two places: for a farm, right before its construction or upgrade starts (every crop allowed),
 * and for a scarecrow field (only the crops the colony's farms grow, listed first; the others greyed out with why).
 */
public final class CropChoiceScreen extends PageScreen
{
    @Nullable
    private final OpenFarmCropPayload farm;
    @Nullable
    private final Consumer<ItemStack> sow;
    /**
     * Back to the field's window after a choice or a cancel.
     */
    @Nullable
    private final Runnable back;
    private boolean answered;

    private CropChoiceScreen(final String title, @Nullable final OpenFarmCropPayload farm, @Nullable final Consumer<ItemStack> sow, @Nullable final Runnable back)
    {
        super(Component.literal(title), null, 300);
        this.farm = farm;
        this.sow = sow;
        this.back = back;
        this.preferredHeight = 170;
    }

    /**
     * The farm's crop, asked before its construction or upgrade.
     */
    public static void openForFarm(final OpenFarmCropPayload payload)
    {
        Minecraft.getInstance().setScreen(new CropChoiceScreen(payload.upgrade() ? "Amélioration de la ferme" : "Nouvelle ferme", payload, null, null));
    }

    /**
     * A scarecrow field's crop; the choice goes back to the field window.
     */
    public static void openForField(final Runnable back, final Consumer<ItemStack> sow)
    {
        Minecraft.getInstance().setScreen(new CropChoiceScreen("Semer ce champ", null, sow, back));
    }

    private List<Crop> order()
    {
        final List<Crop> order = new ArrayList<>();
        if (farm == null)
        {
            // The crops the colony grows first
            for (final Crop crop : Crop.values())
            {
                if (ClientFarmCrops.get().contains(crop.id()))
                {
                    order.add(crop);
                }
            }
        }
        for (final Crop crop : Crop.values())
        {
            if (!order.contains(crop))
            {
                order.add(crop);
            }
        }
        return order;
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        graphics.drawString(font, farm != null ? "Que produira cette ferme ? Une ferme, une culture." : "Le champ renforce les fermes de sa culture.", x + 2, y + 3,
          Palette.MUTED, false);
        return y + 16;
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final Crop current = farm == null ? null : Crop.byId(farm.current());
        for (final Crop crop : order())
        {
            final boolean available = farm != null || ClientFarmCrops.get().contains(crop.id());
            final boolean over = available && Ui.inside(mouseX, mouseY, x, y, width, 24);
            Ui.card(graphics, x, y, width, 24, crop == current, over, !available);
            Icon.of(crop.produceStack()).draw(graphics, x + 4, y + 4, 16);
            graphics.drawString(font, Component.literal(crop.label()).withStyle(ChatFormatting.BOLD), x + 26, y + 8, available ? Palette.INK : Palette.MUTED, false);
            final String note = crop == current ? "Culture actuelle" : available ? "" : "Il faut d'abord une ferme de " + crop.label().toLowerCase(Locale.FRENCH);
            graphics.drawString(font, note, x + width - 6 - font.width(note), y + 8, available ? Palette.GREEN : Palette.RED, false);
            if (available)
            {
                addHit(x, y, width, 24, () -> choose(crop));
            }
            y += 26;
        }
        return y;
    }

    private void choose(final Crop crop)
    {
        answered = true;
        if (farm != null)
        {
            PacketDistributor.sendToServer(new FarmCropPayload(farm.hut(), crop.id()));
            minecraft.setScreen(null);
        }
        else if (sow != null && back != null)
        {
            sow.accept(crop.seedStack());
            back.run();
        }
    }

    @Override
    public void onClose()
    {
        if (back != null)
        {
            back.run();
            return;
        }
        super.onClose();
    }

    @Override
    public void removed()
    {
        super.removed();
        if (farm != null && !answered)
        {
            // Closing without choosing cancels the construction (nothing has been paid yet)
            answered = true;
            PacketDistributor.sendToServer(new FarmCropPayload(farm.hut(), ""));
        }
    }
}
