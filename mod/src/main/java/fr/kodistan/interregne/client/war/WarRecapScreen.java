package fr.kodistan.interregne.client.war;

import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.ParchmentScreen;
import fr.kodistan.interregne.client.ui.Ui;
import fr.kodistan.interregne.economy.Wallet;
import fr.kodistan.interregne.network.WarRecapPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * The end-of-war recap, shown to each member of both colonies: the two banners face to face, VICTOIRE / DÉFAITE, then
 * the war's figures counting up one tile after the other, from the receiver's side (what was won is green, what was lost
 * red). Opened by the server when the war ends; Fermer or Escape closes it.
 */
public final class WarRecapScreen extends ParchmentScreen
{
    private static final int TILE_H = 30;
    private static final int TILE_MIN_W = 110;
    private static final int GAP = 4;
    private static final long COUNT_MS = 700;
    private static final long STAGGER_MS = 120;
    private static final long TITLE_MS = 250;

    private static final Runnable3 COIN = (g, x, y) -> Ui.coin(g, x, y, 16);

    private final WarRecapPayload recap;
    private final List<Tile> tiles = new ArrayList<>();
    private long openedAt = -1;

    /** Neutral, good for the reader, bad for the reader. */
    private enum Tone { NEUTRAL, GOOD, BAD, INFLUENCE }

    private record Tile(Runnable3 icon, String label, long value, LongFunction<String> format, Tone tone) {}

    @FunctionalInterface
    private interface Runnable3
    {
        void draw(GuiGraphics graphics, int x, int y);
    }

    public WarRecapScreen(final WarRecapPayload recap)
    {
        super(Component.literal((recap.outcome() == WarRecapPayload.BREAK ? "Alliance avec " : "Guerre contre ")
          + (recap.victory() ? recap.loser() : recap.winner())), null);
        this.recap = recap;
        if (recap.outcome() == WarRecapPayload.BREAK)
        {
            return;
        }
        final boolean won = recap.victory();
        final LongFunction<String> plain = Long::toString;
        tiles.add(new Tile(COIN, won ? "Butin" : "Or pillé", recap.loot(), Wallet::format, won ? Tone.GOOD : Tone.BAD));
        if (recap.outcome() == WarRecapPayload.CAPITULATION)
        {
            tiles.add(new Tile(COIN, won ? "Tribut" : "Tribut payé", recap.tribute(), Wallet::format, won ? Tone.GOOD : Tone.BAD));
        }
        tiles.add(new Tile(item(Items.TNT), won ? "Bâtiments rasés" : "Bâtiments perdus", recap.destroyed(), plain, won ? Tone.GOOD : Tone.BAD));
        tiles.add(new Tile(item(Items.IRON_SWORD), won ? "Soldats engagés" : "Assaillants", recap.sent(), plain, Tone.NEUTRAL));
        tiles.add(new Tile(item(Items.SKELETON_SKULL), won ? "Soldats perdus" : "Assaillants abattus", recap.lost(), plain, won ? Tone.BAD : Tone.GOOD));
        tiles.add(new Tile(item(Items.BONE), won ? "Défenseurs tombés" : "Habitants tombés", recap.fallen(), plain, won ? Tone.GOOD : Tone.BAD));
        tiles.add(new Tile(item(Items.CROSSBOW), won ? "Assauts" : "Assauts subis", recap.assaults(), plain, Tone.NEUTRAL));
        tiles.add(new Tile(item(Items.CLOCK), "Durée", recap.durationTicks(), WarRecapScreen::duration, Tone.NEUTRAL));
        if (won && recap.influence() > 0)
        {
            tiles.add(new Tile((g, x, y) -> Ui.influence(g, x, y, 16), "Influence", recap.influence(), v -> "+" + v, Tone.INFLUENCE));
        }
    }

    public static void open(final WarRecapPayload recap)
    {
        Minecraft.getInstance().setScreen(new WarRecapScreen(recap));
    }

    private static Runnable3 item(final ItemLike item)
    {
        final Icon icon = Icon.of(item);
        return (g, x, y) -> icon.draw(g, x, y, 16);
    }

    @Override
    protected void init()
    {
        super.init();
        if (openedAt < 0)
        {
            openedAt = Util.getMillis();
            final var sounds = Minecraft.getInstance().getSoundManager();
            sounds.play(switch (recap.outcome())
            {
                case WarRecapPayload.PEACE -> SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1f);
                case WarRecapPayload.BREAK -> SimpleSoundInstance.forUI(SoundEvents.SHIELD_BREAK, 0.8f);
                default -> recap.victory()
                  ? SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f)
                  : SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 0.5f);
            });
        }
    }

    @Override
    protected boolean showWallet()
    {
        return false;
    }

    @Override
    protected Integer headerFill()
    {
        return switch (recap.outcome())
        {
            case WarRecapPayload.PEACE -> Palette.GREEN;
            case WarRecapPayload.BREAK -> Palette.LEATHER;
            default -> recap.victory() ? Palette.AMBER : Palette.RED;
        };
    }

    @Override
    protected int headerText()
    {
        return Palette.WHITE;
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final long elapsed = Util.getMillis() - openedAt;
        final boolean won = recap.victory();
        final String mine = won ? recap.winner() : recap.loser();
        final String theirs = won ? recap.loser() : recap.winner();
        final BannerPatternLayers myFlag = won ? recap.winnerFlag() : recap.loserFlag();
        final BannerPatternLayers theirFlag = won ? recap.loserFlag() : recap.winnerFlag();

        // Hero: our banner on the left, theirs on the right; after a real win, the loser's dimmed, the winner's framed in gold
        final int top = y0 + HEADER + 4;
        final boolean decisive = recap.decisive();
        drawBanner(g, x0 + 10, top, myFlag, decisive && won, decisive && !won);
        drawBanner(g, x0 + w - 44, top, theirFlag, decisive && !won, decisive && won);
        final int nameW = w / 2 - 16;
        g.drawString(font, Ui.fit(font, mine, nameW), x0 + 8, top + 37, Palette.INK, false);
        final String theirName = Ui.fit(font, theirs, nameW);
        g.drawString(font, theirName, x0 + w - 8 - font.width(theirName), top + 37, Palette.INK, false);

        // VICTOIRE ! / DÉFAITE / PAIX SIGNÉE / ALLIANCE ROMPUE, popping in from 1.5x its size, 2x at most between the banners
        final String word = switch (recap.outcome())
        {
            case WarRecapPayload.PEACE -> "PAIX SIGNÉE";
            case WarRecapPayload.BREAK -> "ALLIANCE ROMPUE";
            default -> won ? "VICTOIRE !" : "DÉFAITE";
        };
        final float pop = Math.min(1f, elapsed / (float) TITLE_MS);
        final float scale = Math.min(2f, (w - 100f) / font.width(word)) * (1.5f - 0.5f * pop);
        final int cx = x0 + w / 2;
        final int titleY = top + 4;
        g.pose().pushPose();
        g.pose().translate(cx, titleY + 8, 0);
        g.pose().scale(scale, scale, 1f);
        final int ink = switch (recap.outcome())
        {
            case WarRecapPayload.PEACE -> Palette.GREEN;
            case WarRecapPayload.BREAK -> Palette.RED;
            default -> won ? 0xFFE0A21E : Palette.RED;
        };
        // Shadow straight below only: a sideways one turns the accent of DÉFAITE into a circumflex
        g.drawString(font, word, -font.width(word) / 2, -4 + 1, Palette.EDGE, false);
        g.drawString(font, word, -font.width(word) / 2, -4, ink, false);
        g.pose().popPose();

        final String cause = switch (recap.outcome())
        {
            case WarRecapPayload.CAPITULATION -> won ? "Ils ont capitulé" : "Vous avez capitulé";
            case WarRecapPayload.PEACE -> won ? "Vous avez signé la paix" : theirs + " a signé la paix";
            case WarRecapPayload.BREAK -> won ? "Vous avez rompu l'alliance" : theirs + " a rompu l'alliance";
            default -> won ? "Hôtel de ville pris" : "Hôtel de ville perdu";
        };
        final String causeShown = Ui.fit(font, cause, w - 100);
        g.drawString(font, causeShown, cx - font.width(causeShown) / 2, titleY + 22, Palette.MUTED, false);

        int y = top + 48;
        g.fill(x0 + 8, y, x0 + w - 8, y + 1, Palette.RULE);
        y += 4;

        // A broken alliance has no figures: only what it changes
        if (recap.outcome() == WarRecapPayload.BREAK)
        {
            y += 4;
            Icon.of(Items.CHEST).draw(g, x0 + 8, y, 16);
            Ui.wrapped(g, font, Component.literal("Plus de contrats commerciaux entre vos deux colonies."), x0 + 28, y + 4, w - 36, Palette.INK, 9);
            y += 22;
            Icon.of(Items.IRON_SWORD).draw(g, x0 + 8, y, 16);
            Ui.wrapped(g, font, Component.literal("Une guerre entre vous redevient possible."), x0 + 28, y + 4, w - 36, Palette.INK, 9);
        }

        // Figures, counting up one after the other
        final int avail = w - 16;
        final int cols = Math.max(1, Math.min(tiles.size(), Math.max(2, (avail + GAP) / (TILE_MIN_W + GAP))));
        final int tileW = (avail - (cols - 1) * GAP) / cols;
        for (int i = 0; i < tiles.size(); i++)
        {
            final Tile tile = tiles.get(i);
            final int tx = x0 + 8 + (i % cols) * (tileW + GAP);
            final int ty = y + (i / cols) * (TILE_H + GAP);
            Ui.plate(g, tx, ty, tx + tileW, ty + TILE_H, Palette.PAPER_LIGHT, Palette.RULE, Palette.PAPER_LIGHT);
            tile.icon().draw(g, tx + 4, ty + 3);
            final float t = Math.max(0f, Math.min(1f, (elapsed - TITLE_MS - i * STAGGER_MS) / (float) COUNT_MS));
            final float eased = 1f - (1f - t) * (1f - t) * (1f - t);
            final long shown = t >= 1f ? tile.value() : Math.round(tile.value() * eased);
            g.drawString(font, tile.format().apply(shown), tx + 24, ty + 7, color(tile, shown), false);
            g.drawString(font, Ui.fit(font, tile.label(), tileW - 8), tx + 4, ty + 20, Palette.MUTED, false);
        }
        final int rows = (tiles.size() + cols - 1) / cols;
        y += rows * (TILE_H + GAP) + 2;

        // The loser's shield, under a white flag of truce
        if (recap.shieldTicks() > 0)
        {
            Icon.of(Items.WHITE_BANNER).draw(g, x0 + 8, y, 16);
            final String text = won
              ? theirs + " ne peut plus être attaquée pendant " + duration(recap.shieldTicks()) + "."
              : "Votre colonie est à l'abri de toute guerre pendant " + duration(recap.shieldTicks()) + ".";
            final int lines = font.split(Component.literal(text), w - 36).size();
            Ui.wrapped(g, font, Component.literal(text), x0 + 28, y + (lines > 1 ? 0 : 4), w - 36, Palette.INK, 9);
        }

        final int bw = 90;
        final int bx = x0 + (w - bw) / 2;
        Ui.button(g, font, bx, actionY(), bw, Ui.BUTTON_H, "Fermer", Ui.ButtonStyle.PRIMARY, true,
          Ui.inside(mouseX, mouseY, bx, actionY(), bw, Ui.BUTTON_H));
    }

    private void drawBanner(final GuiGraphics g, final int x, final int y, final BannerPatternLayers flag, final boolean framed, final boolean dimmed)
    {
        if (framed)
        {
            Ui.plate(g, x - 2, y - 1, x + 34, y + 35, Palette.AMBER_SOFT, Palette.AMBER, Palette.AMBER_SOFT);
        }
        final ItemStack banner = new ItemStack(Items.WHITE_BANNER);
        banner.set(DataComponents.BANNER_PATTERNS, flag);
        Icon.of(banner).draw(g, x, y, 32);
        if (dimmed)
        {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(x, y, x + 32, y + 34, 0x88000000);
            g.pose().popPose();
        }
    }

    private static int color(final Tile tile, final long shown)
    {
        if (shown == 0)
        {
            return Palette.INK;
        }
        return switch (tile.tone())
        {
            case GOOD -> Palette.GREEN;
            case BAD -> Palette.RED;
            case INFLUENCE -> 0xFF7A3FC0;
            case NEUTRAL -> Palette.INK;
        };
    }

    /**
     * « 1 h 12 min », « 12 min », « 45 s ».
     */
    static String duration(final long ticks)
    {
        final long seconds = ticks / 20;
        final long hours = seconds / 3600;
        final long minutes = seconds % 3600 / 60;
        if (hours > 0)
        {
            return minutes > 0 ? hours + " h " + minutes + " min" : hours + " h";
        }
        return minutes > 0 ? minutes + " min" : seconds + " s";
    }

    @Override
    protected boolean clicked(final double mouseX, final double mouseY)
    {
        final int bw = 90;
        if (Ui.inside(mouseX, mouseY, x0 + (w - bw) / 2, actionY(), bw, Ui.BUTTON_H))
        {
            onClose();
            return true;
        }
        return false;
    }
}
