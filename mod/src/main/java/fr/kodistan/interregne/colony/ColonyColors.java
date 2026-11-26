package fr.kodistan.interregne.colony;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.ImperialBank;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One color per colony, drawn at random when it is founded. It is MineColonies' own colony color (saved with the
 * colony, already used for its citizens' names and glow), so everything that already reads it follows; on top of
 * that each colony gets a vanilla scoreboard team of that color holding its online players, which tints their name
 * (above the head, tab list, chat) and their glowing outline.
 * <p>
 * WHITE is MineColonies' default and means "not drawn yet": it is left out of the palette, and any colony still
 * white (founded before this existed, or missed) gets its color at the next sync.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class ColonyColors
{
    /** Readable on the dark HUD and distinct from each other; white is reserved as "unassigned", grays read as neutral. */
    private static final List<ChatFormatting> PALETTE = List.of(
      ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
      ChatFormatting.GOLD, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_RED,
      ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA, ChatFormatting.DARK_PURPLE, ChatFormatting.DARK_BLUE);

    /** Team names are ours alone, so players put in an admin's own team are never touched. */
    private static final String TEAM_PREFIX = "kod_colonie_";
    private static final int SYNC_EVERY = 100;

    private static boolean subscribed;

    private ColonyColors()
    {
    }

    /** Called once at common setup: MineColonies events go through its own bus, not NeoForge's. */
    public static void subscribe()
    {
        if (subscribed)
        {
            return;
        }
        subscribed = true;
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyCreatedModEvent.class, event -> {
            final IColony colony = event.getColony();
            if (colony.getWorld() != null && colony.getWorld().getServer() != null)
            {
                assign(colony, colony.getWorld().getServer());
                sync(colony.getWorld().getServer());
            }
        });
    }

    /** The colony's color, as RGB. */
    public static int rgb(final IColony colony)
    {
        final Integer color = colony.getTeamColonyColor().getColor();
        return color == null ? 0xFFFFFF : color;
    }

    /** The color of the colony a player belongs to, or null when they have none — e.g. for arena effects. */
    @Nullable
    public static ChatFormatting of(final ServerPlayer player)
    {
        final IColony colony = ImperialBank.colonyOf(player);
        return colony == null ? null : colony.getTeamColonyColor();
    }

    private static void assign(final IColony colony, final MinecraftServer server)
    {
        if (colony.getTeamColonyColor() != ChatFormatting.WHITE)
        {
            return;
        }
        final Set<ChatFormatting> taken = EnumSet.noneOf(ChatFormatting.class);
        for (final IColony other : IColonyManager.getInstance().getAllColonies())
        {
            if (other != colony)
            {
                taken.add(other.getTeamColonyColor());
            }
        }
        final List<ChatFormatting> free = new ArrayList<>(PALETTE);
        free.removeAll(taken);
        // More colonies than colors: repeats are unavoidable, any color will do.
        final List<ChatFormatting> pool = free.isEmpty() ? PALETTE : free;
        colony.setColonyColor(pool.get(RandomSource.create().nextInt(pool.size())));
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (event.getServer().getTickCount() % SYNC_EVERY == 0)
        {
            sync(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null)
        {
            sync(player.getServer());
        }
    }

    /**
     * Colors any colony still unassigned, then puts each online player in their colony's team (membership changes
     * through MineColonies' permissions without an event of ours, hence the periodic pass; it only writes on change).
     */
    private static void sync(final MinecraftServer server)
    {
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            assign(colony, server);
        }
        final Scoreboard board = server.getScoreboard();
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            final IColony colony = ImperialBank.colonyOf(player);
            final PlayerTeam current = board.getPlayersTeam(player.getScoreboardName());
            if (colony == null)
            {
                if (current != null && current.getName().startsWith(TEAM_PREFIX))
                {
                    board.removePlayerFromTeam(player.getScoreboardName(), current);
                }
                continue;
            }
            final PlayerTeam team = teamFor(board, colony);
            if (current != team)
            {
                board.addPlayerToTeam(player.getScoreboardName(), team);
            }
        }
    }

    private static PlayerTeam teamFor(final Scoreboard board, final IColony colony)
    {
        final String name = TEAM_PREFIX + colony.getID();
        PlayerTeam team = board.getPlayerTeam(name);
        if (team == null)
        {
            team = board.addPlayerTeam(name);
            // Only the color is wanted: keep PvP between members and hide invisible members, as without a team.
            team.setAllowFriendlyFire(true);
            team.setSeeFriendlyInvisibles(false);
            team.setNameTagVisibility(Team.Visibility.ALWAYS);
            team.setCollisionRule(Team.CollisionRule.ALWAYS);
        }
        if (team.getColor() != colony.getTeamColonyColor())
        {
            team.setColor(colony.getTeamColonyColor());
        }
        return team;
    }
}
