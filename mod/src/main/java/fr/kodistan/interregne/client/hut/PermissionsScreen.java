package fr.kodistan.interregne.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.PermissionEvent;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.network.messages.PermissionsMessage;
import com.minecolonies.core.network.messages.server.colony.ChangeFreeToInteractBlockMessage;
import com.minecolonies.core.network.messages.server.colony.building.GiveToolMessage;
import fr.kodistan.interregne.client.ui.Icon;
import fr.kodistan.interregne.client.ui.Palette;
import fr.kodistan.interregne.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static com.minecolonies.api.util.constant.WindowConstants.KEY_TO_PERMISSIONS;

/**
 * Who may do what in the colony: the players and their rank, each rank's rights, the blocks anyone may use, and the
 * log of who tried what. Replaces MineColonies' permissions page.
 */
public final class PermissionsScreen extends ModuleScreen
{
    private static final List<String> TABS = List.of("Joueurs", "Rangs et droits", "Blocs libres", "Journal");
    private static final List<String> RANK_TYPES = List.of("Gestion", "Hostile", "Neutre");

    private int tab;
    @Nullable
    private Rank rank;

    private PermissionsScreen(final BuildingTownHall.View townHall, @Nullable final Screen parent)
    {
        super("Permissions", townHall, parent, 460);
    }

    public static void open(final BuildingTownHall.View townHall, @Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(new PermissionsScreen(townHall, parent));
    }

    @Override
    protected void init()
    {
        super.init();
        // The field serves to type a player's name, a rank's name or a block, depending on the tab
        addSearch(24);
        hintField();
    }

    private void hintField()
    {
        search.visible = tab != 3;
        search.setHint(Component.literal(switch (tab)
        {
            case 0 -> "Nom du joueur";
            case 1 -> "Nom du rang";
            default -> "minecraft:oak_door ou x y z";
        }));
    }

    private IColonyView colony()
    {
        return building.getColony();
    }

    private IPermissions permissions()
    {
        return colony().getPermissions();
    }

    private boolean canEdit()
    {
        return permissions().hasPermission(minecraft.player, Action.EDIT_PERMISSIONS);
    }

    private static final String CANNOT = "Ton rang ne te permet pas de changer les permissions.";

    private String typed()
    {
        return search.getValue().trim();
    }

    private Rank rank()
    {
        // Ranks are rebuilt on each colony update: find the chosen one again by its id
        if (rank != null)
        {
            final Rank fresh = permissions().getRanks().get(rank.getId());
            if (fresh != null)
            {
                return rank = fresh;
            }
        }
        return rank = permissions().getRankOfficer();
    }

    @Override
    protected int renderTop(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        segmented(graphics, mouseX, mouseY, x, y, TABS, tab, index -> {
            tab = index;
            search.setValue("");
            hintField();
            resetScroll();
        });
        y += 18;
        if (tab == 3)
        {
            return Ui.wrapped(graphics, font, Component.literal("Les tentatives des joueurs sans le droit, les plus récentes d'abord."), x + 2, y + 3, width - (2), Palette.MUTED, 10) + 8;
        }
        final String action = switch (tab)
        {
            case 0 -> "Ajouter le joueur";
            case 1 -> "Créer le rang";
            default -> "Libérer";
        };
        final String text = typed();
        String why = canEdit() ? null : CANNOT;
        if (why == null && text.isEmpty())
        {
            why = "Écris d'abord dans le champ à droite.";
        }
        if (why == null && tab == 1 && permissions().getRanks().values().stream().anyMatch(r -> r.getName().equalsIgnoreCase(text)))
        {
            why = "Un rang porte déjà ce nom.";
        }
        if (why == null && tab == 2 && block(text) == null && BlockPosUtil.getBlockPosOfString(text) == null)
        {
            why = "Écris l'identifiant d'un bloc (minecraft:oak_door) ou une position (x y z).";
        }
        final int bx = x0 + w - 132 - buttonWidth(action);
        button(graphics, mouseX, mouseY, bx, y + 1, action, Ui.ButtonStyle.PRIMARY, why == null, why, () -> add(text));
        if (tab == 2)
        {
            final String tool = "Recevoir le sceptre";
            button(graphics, mouseX, mouseY, x, y + 1, tool, Ui.ButtonStyle.SECONDARY, canEdit(), canEdit() ? "Un clic droit du sceptre sur un bloc le rend libre." : CANNOT,
              () -> new GiveToolMessage(building, ModItems.permTool).sendToServer());
        }
        return y + 20;
    }

    @Nullable
    private static Block block(final String text)
    {
        final ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id))
        {
            return null;
        }
        final Block block = BuiltInRegistries.BLOCK.get(id);
        return block.defaultBlockState().isAir() ? null : block;
    }

    private void add(final String text)
    {
        switch (tab)
        {
            case 0 -> new PermissionsMessage.AddPlayer(colony(), text).sendToServer();
            case 1 -> new PermissionsMessage.AddRank(colony(), text).sendToServer();
            default ->
            {
                final Block block = block(text);
                if (block != null)
                {
                    colony().addFreeBlock(block);
                    new ChangeFreeToInteractBlockMessage(colony(), block, ChangeFreeToInteractBlockMessage.MessageType.ADD_BLOCK).sendToServer();
                }
                final BlockPos pos = BlockPosUtil.getBlockPosOfString(text);
                if (pos != null)
                {
                    colony().addFreePosition(pos);
                    new ChangeFreeToInteractBlockMessage(colony(), pos, ChangeFreeToInteractBlockMessage.MessageType.ADD_BLOCK).sendToServer();
                }
            }
        }
        search.setValue("");
    }

    @Override
    protected int renderBody(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width)
    {
        return switch (tab)
        {
            case 0 -> players(graphics, mouseX, mouseY, x, y, width);
            case 1 -> ranks(graphics, mouseX, mouseY, x, y, width);
            case 2 -> freeBlocks(graphics, mouseX, mouseY, x, y, width);
            default -> log(graphics, mouseX, mouseY, x, y, width);
        };
    }

    // --- Players ---

    private List<Rank> assignableRanks()
    {
        final List<Rank> ranks = new ArrayList<>();
        for (final Rank each : permissions().getRanks().values())
        {
            if (!each.equals(permissions().getRankOwner()))
            {
                ranks.add(each);
            }
        }
        ranks.sort(Rank::compareTo);
        return ranks;
    }

    private int players(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<ColonyPlayer> players = new ArrayList<>(colony().getPlayers().values());
        players.sort(Comparator.comparing(ColonyPlayer::getRank, Rank::compareTo));
        final List<Rank> ranks = assignableRanks();
        for (final ColonyPlayer player : players)
        {
            Ui.card(graphics, x, y, width, 20, false, false, false);
            graphics.drawString(font, Component.literal(fit(player.getName(), width - 230)).withStyle(ChatFormatting.BOLD), x + 6, y + 6, Palette.INK, false);
            final boolean owner = player.getRank().getId() == IPermissions.OWNER_RANK_ID;
            int bx = x + width - 4;
            final String remove = "Retirer";
            bx -= buttonWidth(remove);
            button(graphics, mouseX, mouseY, bx, y + 3, remove, Ui.ButtonStyle.DANGER, !owner && canEdit(), owner ? "Le propriétaire ne peut pas être retiré." : canEdit() ? null : CANNOT,
              () -> new PermissionsMessage.RemovePlayer(colony(), player.getID()).sendToServer());
            final String rankLabel = player.getRank().getName() + (owner ? "" : " ⟳");
            final int rw = Math.max(110, buttonWidth(rankLabel));
            bx -= rw + 4;
            final boolean over = Ui.inside(mouseX, mouseY, bx, y + 3, rw, 14);
            Ui.button(graphics, font, bx, y + 3, rw, 14, fit(rankLabel, rw - 6), Ui.ButtonStyle.SECONDARY, !owner && canEdit(), over);
            if (!owner && canEdit() && !ranks.isEmpty())
            {
                addHit(bx, y + 3, rw, 14, () -> {
                    final Rank next = ranks.get((ranks.indexOf(player.getRank()) + 1) % ranks.size());
                    player.setRank(next);
                    new PermissionsMessage.ChangePlayerRank(colony(), player.getID(), next).sendToServer();
                });
                if (over)
                {
                    hint = "Clique pour passer au rang suivant.";
                }
            }
            y += 22;
        }
        return y;
    }

    // --- Ranks and rights ---

    private int ranks(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final Rank current = rank();
        final List<Rank> ranks = new ArrayList<>(permissions().getRanks().values());
        ranks.sort(Rank::compareTo);
        // One chip per rank, the chosen one green
        int cx = x;
        for (final Rank each : ranks)
        {
            final int cw = font.width(each.getName()) + 10;
            if (cx + cw > x + width)
            {
                cx = x;
                y += 16;
            }
            final boolean on = each.getId() == current.getId();
            final boolean over = Ui.inside(mouseX, mouseY, cx, y, cw, 14);
            Ui.plate(graphics, cx, y, cx + cw, y + 14, on ? Palette.GREEN : over ? Palette.CARD_HOVER : Palette.PAPER_LIGHT, on ? Palette.EDGE : Palette.RULE,
              on ? Palette.GREEN_LIGHT : Palette.PAPER_LIGHT);
            graphics.drawString(font, each.getName(), cx + 5, y + 3, on ? Palette.GREEN_TEXT : Palette.INK, false);
            if (!on)
            {
                addHit(cx, y, cw, 14, () -> rank = each);
            }
            cx += cw + 3;
        }
        y += 20;

        // The chosen rank: its kind, alerts, removal
        final boolean owner = current.equals(permissions().getRankOwner());
        final boolean editable = canEdit() && !owner;
        Ui.card(graphics, x, y, width, 40, false, false, false);
        graphics.drawString(font, Component.literal(current.getName()).withStyle(ChatFormatting.BOLD), x + 6, y + 5, Palette.INK, false);
        final int kind = current.isColonyManager() ? 0 : current.isHostile() ? 1 : 2;
        graphics.drawString(font, "Genre :", x + 6, y + 24, Palette.MUTED, false);
        if (editable)
        {
            segmented(graphics, mouseX, mouseY, x + 46, y + 21, RANK_TYPES, kind, index -> new PermissionsMessage.EditRankType(colony(), current, index).sendToServer());
        }
        else
        {
            graphics.drawString(font, RANK_TYPES.get(kind), x + 46, y + 24, Palette.INK, false);
        }
        final String alerts = current.isSubscriber() ? "Alertes : oui" : "Alertes : non";
        int bx = x + width - 6 - buttonWidth(alerts);
        button(graphics, mouseX, mouseY, bx, y + 4, alerts, Ui.ButtonStyle.SECONDARY, canEdit(),
          canEdit() ? "Les joueurs de ce rang reçoivent les messages de la colonie." : CANNOT, () -> {
              new PermissionsMessage.SetSubscriber(colony(), current, !current.isSubscriber()).sendToServer();
              current.setSubscriber(!current.isSubscriber());
          });
        final String remove = "Supprimer le rang";
        bx = x + width - 6 - buttonWidth(remove);
        button(graphics, mouseX, mouseY, bx, y + 21, remove, Ui.ButtonStyle.DANGER, canEdit() && !current.isInitial(),
          current.isInitial() ? "Les rangs de base ne se suppriment pas." : canEdit() ? null : CANNOT, () -> {
              new PermissionsMessage.RemoveRank(colony(), current).sendToServer();
              permissions().removeRank(current);
              rank = null;
          });
        y += 44;

        // Its rights, two per line
        Ui.label(graphics, font, "DROITS DE CE RANG", x, y);
        y += 11;
        final Rank mine = permissions().getRank(minecraft.player);
        final Action[] actions = Action.values();
        final int columnW = (width - 3) / 2;
        for (int i = 0; i < actions.length; i++)
        {
            final Action action = actions[i];
            final int ax = x + (i % 2) * (columnW + 3);
            final int ay = y + (i / 2) * 18;
            final boolean allowed = permissions().hasPermission(current, action);
            final boolean alterable = permissions().canAlterPermission(mine, current, action);
            final boolean over = Ui.inside(mouseX, mouseY, ax, ay, columnW, 16);
            final int fill = allowed ? (over && alterable ? Palette.GREEN_LIGHT : Palette.GREEN_SOFT) : (over && alterable ? Palette.CARD_HOVER : Palette.PAPER_DARK);
            Ui.plate(graphics, ax, ay, ax + columnW, ay + 16, fill, allowed ? Palette.GREEN : Palette.RULE, fill);
            graphics.drawString(font, allowed ? "✔" : "✕", ax + 4, ay + 4, allowed ? Palette.GREEN : Palette.MUTED, false);
            final String name = Component.translatable(KEY_TO_PERMISSIONS + action.toString().toLowerCase(Locale.US)).getString();
            graphics.drawString(font, fit(name, columnW - 18), ax + 14, ay + 4, alterable ? Palette.INK : Palette.MUTED, false);
            if (alterable)
            {
                addHit(ax, ay, columnW, 16, () -> {
                    if (permissions().alterPermission(mine, current, action, !allowed))
                    {
                        new PermissionsMessage.Permission(colony(), !allowed, current, action).sendToServer();
                    }
                });
            }
            else if (over)
            {
                hint = "Ton rang ne permet pas de changer ce droit pour ce rang.";
            }
        }
        return y + ((actions.length + 1) / 2) * 18;
    }

    // --- Free blocks ---

    private int freeBlocks(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<Block> blocks = colony().getFreeBlocks();
        final List<BlockPos> positions = colony().getFreePositions();
        if (blocks.isEmpty() && positions.isEmpty())
        {
            return Ui.wrapped(graphics, font, Component.literal("Aucun bloc libre : seuls les joueurs autorisés touchent aux blocs de la colonie."), x + 2, y + 2, width - (2), Palette.MUTED, 10) + 4;
        }
        for (final Block block : List.copyOf(blocks))
        {
            y = freeRow(graphics, mouseX, mouseY, x, y, width, new ItemStack(block), block.getName().getString(), BuiltInRegistries.BLOCK.getKey(block).toString(), () -> {
                new ChangeFreeToInteractBlockMessage(colony(), block, ChangeFreeToInteractBlockMessage.MessageType.REMOVE_BLOCK).sendToServer();
                colony().removeFreeBlock(block);
            });
        }
        for (final BlockPos pos : List.copyOf(positions))
        {
            final Block block = colony().getWorld().isLoaded(pos) ? colony().getWorld().getBlockState(pos).getBlock() : null;
            y = freeRow(graphics, mouseX, mouseY, x, y, width, block == null ? ItemStack.EMPTY : new ItemStack(block), block == null ? "Bloc hors de vue" : block.getName().getString(),
              "seulement en " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), () -> {
                  new ChangeFreeToInteractBlockMessage(colony(), pos, ChangeFreeToInteractBlockMessage.MessageType.REMOVE_BLOCK).sendToServer();
                  colony().removeFreePosition(pos);
              });
        }
        return y;
    }

    private int freeRow(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, final int y, final int width, final ItemStack icon, final String name,
      final String detail, final Runnable remove)
    {
        Ui.card(graphics, x, y, width, 22, false, false, false);
        if (!icon.isEmpty())
        {
            Icon.of(icon).draw(graphics, x + 3, y + 3, 16);
        }
        graphics.drawString(font, Component.literal(fit(name, width - 120)).withStyle(ChatFormatting.BOLD), x + 23, y + 3, Palette.INK, false);
        graphics.drawString(font, fit(detail, width - 120), x + 23, y + 12, Palette.MUTED, false);
        final String label = "Retirer";
        button(graphics, mouseX, mouseY, x + width - 4 - buttonWidth(label), y + 4, label, Ui.ButtonStyle.DANGER, canEdit(), canEdit() ? null : CANNOT, remove);
        return y + 24;
    }

    // --- Log ---

    private int log(final GuiGraphics graphics, final int mouseX, final int mouseY, final int x, int y, final int width)
    {
        final List<PermissionEvent> events = new ArrayList<>(((BuildingTownHall.View) building).getPermissionEvents());
        if (events.isEmpty())
        {
            return Ui.wrapped(graphics, font, Component.literal("Personne n'a rien tenté."), x + 2, y + 2, width - (2), Palette.MUTED, 10) + 4;
        }
        for (int i = events.size() - 1; i >= 0; i--)
        {
            final PermissionEvent event = events.get(i);
            Ui.card(graphics, x, y, width, 22, false, false, false);
            final String who = event.getName() + (event.getId() == null ? " (machine)" : "");
            graphics.drawString(font, Component.literal(fit(who, width - 130)).withStyle(ChatFormatting.BOLD), x + 6, y + 3, Palette.INK, false);
            final String what = Component.translatable(KEY_TO_PERMISSIONS + event.getAction().toString().toLowerCase(Locale.US)).getString();
            final BlockPos pos = event.getPosition();
            graphics.drawString(font, fit(what + " · en " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), width - 130), x + 6, y + 12, Palette.MUTED, false);
            if (event.getId() != null)
            {
                final String label = "Ajouter à la colonie";
                button(graphics, mouseX, mouseY, x + width - 4 - buttonWidth(label), y + 4, label, Ui.ButtonStyle.SECONDARY, canEdit(), canEdit() ? null : CANNOT,
                  () -> new PermissionsMessage.AddPlayerOrFakePlayer(colony(), event.getName(), event.getId()).sendToServer());
            }
            y += 24;
        }
        return y;
    }
}
