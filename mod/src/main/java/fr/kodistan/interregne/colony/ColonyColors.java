package fr.kodistan.interregne.colony;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import fr.kodistan.interregne.InterregneMod;
import fr.kodistan.interregne.economy.ImperialBank;
import fr.kodistan.interregne.network.ColonyColorsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One color per colony, drawn at random from {@link #PALETTE} when it is founded and kept for good. Vanilla teams
 * and MineColonies only know the 16 chat colors, too few for a server's worth of colonies, so the color lives here
 * as RGB and is sent to every client: it tints the members' names (above the head, tab list, chat), their glowing
 * outline and their citizens' (see {@code PlayerTeamColorMixin}, {@code CitizenColorMixin}), and the assault troops
 * they send. MineColonies' own colony color is set to the closest chat color, for what still reads it (border, map).
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class ColonyColors extends SavedData
{
    /** 20 colors far enough apart to tell at a glance, all readable on the dark chat and tab backgrounds. */
    public static final int[] PALETTE = {
      0xE6194B, 0x3CB44B, 0xFFE119, 0x4363D8, 0xF58231,
      0x911EB4, 0x42D4F4, 0xF032E6, 0xBFEF45, 0xFABED4,
      0x469990, 0xDCBEFF, 0x9A6324, 0xFFFAC8, 0x800000,
      0xAAFFC3, 0x808000, 0xFFD8B1, 0xD4AF37, 0xA9A9A9};

    private static final String NAME = "interregne_colony_colors";
    private static final int SYNC_EVERY = 100;
    private static final ChatFormatting[] CHAT_COLORS = {
      ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA, ChatFormatting.DARK_RED,
      ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY, ChatFormatting.DARK_GRAY,
      ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.RED,
      ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW};

    /** Client side: what the server last sent. Kept apart from the saved data, which an integrated server shares the JVM with. */
    private static Map<Integer, Integer> clientColonies = Map.of();
    private static Map<UUID, Integer> clientPlayers = Map.of();

    /** Server side: each online member's color, as last sent. */
    private static Map<UUID, Integer> players = Map.of();
    private static boolean subscribed;

    private final Map<Integer, Integer> colonies = new HashMap<>();

    public static ColonyColors get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ColonyColors::new, ColonyColors::load, null), NAME);
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
                sync(colony.getWorld().getServer());
            }
        });
    }

    /** The colony's color as RGB (white until it is drawn, a few seconds at most). */
    public static int rgb(final IColony colony)
    {
        if (colony.getWorld() == null || colony.getWorld().getServer() == null)
        {
            return 0xFFFFFF;
        }
        return get(colony.getWorld().getServer()).colonies.getOrDefault(colony.getID(), 0xFFFFFF);
    }

    /** The color of the colony a player belongs to, or null when they have none — e.g. for arena effects. Either side. */
    @Nullable
    public static Integer of(final Player player)
    {
        return player.level().isClientSide ? clientPlayers.get(player.getUUID()) : players.get(player.getUUID());
    }

    /** A colony's color by id, or null when unknown — for its citizens, on either side. */
    @Nullable
    public static Integer ofColony(final int colonyId, final boolean clientSide)
    {
        if (clientSide)
        {
            return clientColonies.get(colonyId);
        }
        final MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : get(server).colonies.get(colonyId);
    }

    /** Client side, from {@link ColonyColorsPayload}. */
    public static void receive(final ColonyColorsPayload payload)
    {
        clientColonies = payload.colonies();
        clientPlayers = payload.players();
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
            PacketDistributor.sendToPlayer(player, payload(player.getServer()));
        }
    }

    @SubscribeEvent
    public static void onNameFormat(final PlayerEvent.NameFormat event)
    {
        final Integer color = of(event.getEntity());
        if (color != null)
        {
            event.setDisplayname(event.getDisplayname().copy().withStyle(style -> style.withColor(color)));
        }
    }

    @SubscribeEvent
    public static void onTabListNameFormat(final PlayerEvent.TabListNameFormat event)
    {
        final Integer color = of(event.getEntity());
        if (color != null)
        {
            event.setDisplayName(Component.literal(event.getEntity().getGameProfile().getName()).withStyle(style -> style.withColor(color)));
        }
    }

    /**
     * Draws a color for any colony without one, then works out each online member's color; only a change is sent
     * (membership changes through MineColonies' permissions with no event of ours, hence the periodic pass).
     */
    private static void sync(final MinecraftServer server)
    {
        final ColonyColors data = get(server);
        boolean changed = false;
        for (final IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            changed |= data.assign(colony);
        }
        final Map<UUID, Integer> now = new HashMap<>();
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            final IColony colony = ImperialBank.colonyOf(player);
            if (colony != null && data.colonies.containsKey(colony.getID()))
            {
                now.put(player.getUUID(), data.colonies.get(colony.getID()));
            }
        }
        if (!changed && now.equals(players))
        {
            return;
        }
        final Map<UUID, Integer> before = players;
        players = now;
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (!java.util.Objects.equals(before.get(player.getUUID()), now.get(player.getUUID())))
            {
                player.refreshDisplayName();
                player.refreshTabListName();
            }
        }
        PacketDistributor.sendToAllPlayers(payload(server));
    }

    private static ColonyColorsPayload payload(final MinecraftServer server)
    {
        return new ColonyColorsPayload(new HashMap<>(get(server).colonies), new HashMap<>(players));
    }

    /** Draws a color not yet taken by another colony; past 20 colonies, repeats are unavoidable. */
    private boolean assign(final IColony colony)
    {
        if (colonies.containsKey(colony.getID()))
        {
            return false;
        }
        final Set<Integer> taken = new HashSet<>(colonies.values());
        final List<Integer> free = new ArrayList<>();
        for (final int color : PALETTE)
        {
            if (!taken.contains(color))
            {
                free.add(color);
            }
        }
        final RandomSource random = RandomSource.create();
        final int color = free.isEmpty() ? PALETTE[random.nextInt(PALETTE.length)] : free.get(random.nextInt(free.size()));
        colonies.put(colony.getID(), color);
        colony.setColonyColor(closestChatColor(color));
        setDirty();
        return true;
    }

    private static ChatFormatting closestChatColor(final int rgb)
    {
        ChatFormatting best = ChatFormatting.RED;
        long bestDistance = Long.MAX_VALUE;
        for (final ChatFormatting chat : CHAT_COLORS)
        {
            final int c = chat.getColor();
            final long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            final long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            final long db = (c & 0xFF) - (rgb & 0xFF);
            final long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = chat;
            }
        }
        return best;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final CompoundTag list = new CompoundTag();
        colonies.forEach((id, color) -> list.putInt(String.valueOf(id), color));
        tag.put("colonies", list);
        return tag;
    }

    private static ColonyColors load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final ColonyColors data = new ColonyColors();
        final CompoundTag list = tag.getCompound("colonies");
        for (final String key : list.getAllKeys())
        {
            data.colonies.put(Integer.parseInt(key), list.getInt(key));
        }
        return data;
    }
}
