package fr.kodistan.interregne.client.dev;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.AbstractTextElement;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.View;
import com.mojang.blaze3d.platform.NativeImage;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Dev-only remote hands for a test client, so one person (or Claude) can play two clients at once against a real
 * server: every action goes through the same path a player's mouse and keyboard would take (packets, screens, the
 * mod's own buttons), nothing is set server side.
 * <p>
 * Active only in a dev run started with {@code -Dinterregne.playdriver=true} (runPilote1/runPilote2), never in a plain runClient. Write actions, one per line,
 * into {@code playdriver/inbox.txt} (write another file and rename it, so the client never reads half a file); they
 * run one after the other and each one's result is appended to {@code playdriver/outbox.txt}, with every chat line,
 * action bar message and screen change the client sees in between.
 * <pre>
 * connect localhost:25565      disconnect      quit
 * say /cmd args | say text     (a command or chat message, sent as the player)
 * wait 40                      waitchat 600 text      waitscreen 200 ScreenName
 * screen                       (open screen, its title, the buttons it drew last frame)
 * click Label                  (the first enabled button whose label contains Label, clicked in its middle)
 * clickat x y                  (GUI coordinates)     type text     key 256 (GLFW key on the open screen)
 * press key.map                (a key binding whose name contains the text, as if tapped)
 * use x y z [face]             (right-click that block's face, default up: opens huts, places the held block)
 * useitem      hit [type]      (attack the nearest entity in reach, optionally whose type id contains [type])
 * slot 0-8     look yaw pitch     walk ticks     sneak on|off
 * shot name                    (playdriver/out/name.png)      state      inv      close      log text
 * </pre>
 */
@EventBusSubscriber(modid = InterregneMod.MODID, value = Dist.CLIENT)
public final class PlayDriver
{
    private record Seen(String label, int x, int y, int w, int h, boolean enabled) {}

    private static final Deque<String> QUEUE    = new ArrayDeque<>();
    private static final List<String>  OUT      = new ArrayList<>();
    private static final List<String>  CHAT     = new ArrayList<>();
    private static final List<Seen>    DRAWING  = new ArrayList<>();
    private static List<Seen>          lastFrame = List.of();

    private static Boolean active;
    private static String  current;
    private static int     waited;
    private static int     seq;
    private static int     chatMark;
    private static long    ticks;

    private PlayDriver()
    {
    }

    public static boolean active()
    {
        if (active == null)
        {
            active = !FMLEnvironment.production && Boolean.getBoolean("interregne.playdriver");
            if (active)
            {
                try
                {
                    Files.createDirectories(dir());
                }
                catch (final IOException e)
                {
                    InterregneMod.LOGGER.warn("[playdriver] dossier", e);
                }
                InterregneMod.LOGGER.info("[playdriver] actif ({})", dir());
            }
        }
        return active;
    }

    private static Path dir()
    {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("playdriver");
    }

    /** Called by the mod's button painters ({@code Ui}) so a button can be found by its label. */
    public static void seen(final String label, final int x, final int y, final int w, final int h, final boolean enabled)
    {
        if (active())
        {
            DRAWING.add(new Seen(ChatFormatting.stripFormatting(label), x, y, w, h, enabled));
        }
    }

    @SubscribeEvent
    public static void onRenderPre(final ScreenEvent.Render.Pre event)
    {
        if (active())
        {
            DRAWING.clear();
        }
    }

    @SubscribeEvent
    public static void onRenderPost(final ScreenEvent.Render.Post event)
    {
        if (active())
        {
            lastFrame = List.copyOf(DRAWING);
        }
    }

    @SubscribeEvent
    public static void onScreen(final ScreenEvent.Opening event)
    {
        if (active())
        {
            log("[écran] " + describe(event.getNewScreen()));
        }
    }

    @SubscribeEvent
    public static void onChat(final ClientChatReceivedEvent event)
    {
        if (!active())
        {
            return;
        }
        final String text = event.getMessage().getString();
        final boolean overlay = event instanceof ClientChatReceivedEvent.System system && system.isOverlay();
        if (!overlay)
        {
            CHAT.add(text);
        }
        log((overlay ? "[barre] " : "[chat] ") + text);
    }

    @SubscribeEvent
    public static void onTick(final ClientTickEvent.Post event)
    {
        if (!active())
        {
            return;
        }
        ticks++;
        final Minecraft mc = Minecraft.getInstance();
        // Two clients side by side: the one without focus must not open its pause menu
        mc.options.pauseOnLostFocus = false;
        readInbox();
        if (current == null && !QUEUE.isEmpty())
        {
            current = QUEUE.poll();
            waited = 0;
            seq++;
            chatMark = CHAT.size();
        }
        if (current != null)
        {
            String result;
            try
            {
                result = step(mc, current.trim());
            }
            catch (final Exception e)
            {
                result = "ERREUR " + e;
                InterregneMod.LOGGER.warn("[playdriver] {} a échoué", current, e);
            }
            if (result == null)
            {
                waited++;
            }
            else
            {
                log("#" + seq + " " + current + " -> " + result);
                current = null;
            }
        }
        flush();
    }

    /** One tick of the current action: its result once done, null while it is still at work. */
    private static String step(final Minecraft mc, final String line)
    {
        final int space = line.indexOf(' ');
        final String verb = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        final String arg = space < 0 ? "" : line.substring(space + 1).trim();
        final String[] a = arg.isEmpty() ? new String[0] : arg.split("\\s+");
        final LocalPlayer player = mc.player;
        switch (verb)
        {
            case "log":
                return "ok";
            case "wait":
                return waited >= Integer.parseInt(a[0]) ? "ok" : null;
            case "connect":
                if (waited == 0)
                {
                    if (mc.level != null)
                    {
                        return "déjà connecté";
                    }
                    ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(arg), new ServerData("pré-playtest", arg, ServerData.Type.OTHER),
                      false, null);
                }
                if (mc.player != null && mc.level != null)
                {
                    return "ok, connecté en " + waited + " ticks";
                }
                return waited > 2400 ? "ERREUR connexion trop longue, écran " + describe(mc.screen) : null;
            case "disconnect":
                if (waited == 0 && mc.level != null)
                {
                    mc.level.disconnect();
                    mc.disconnect();
                    mc.setScreen(new TitleScreen());
                }
                return mc.level == null ? "ok" : null;
            case "quit":
                mc.stop();
                return "ok";
            case "say":
                if (player == null)
                {
                    return "ERREUR pas en jeu";
                }
                if (arg.startsWith("/"))
                {
                    player.connection.sendCommand(arg.substring(1));
                }
                else
                {
                    player.connection.sendChat(arg);
                }
                return "ok";
            case "waitchat":
            {
                final String needle = arg.substring(arg.indexOf(' ') + 1).toLowerCase(Locale.ROOT);
                for (int i = chatMark; i < CHAT.size(); i++)
                {
                    if (CHAT.get(i).toLowerCase(Locale.ROOT).contains(needle))
                    {
                        return "ok : " + CHAT.get(i);
                    }
                }
                return waited >= Integer.parseInt(a[0]) ? "ERREUR rien reçu contenant « " + needle + " »" : null;
            }
            case "waitscreen":
            {
                final String needle = arg.substring(arg.indexOf(' ') + 1).toLowerCase(Locale.ROOT);
                if (describe(mc.screen).toLowerCase(Locale.ROOT).contains(needle))
                {
                    return "ok : " + describe(mc.screen);
                }
                return waited >= Integer.parseInt(a[0]) ? "ERREUR écran : " + describe(mc.screen) : null;
            }
            case "screen":
                return describe(mc.screen) + " | gui x" + (int) mc.getWindow().getGuiScale() + " " + mc.getWindow().getGuiScaledWidth() + "x"
                         + mc.getWindow().getGuiScaledHeight() + " | " + buttons(mc.screen);
            case "click":
            {
                final Screen screen = mc.screen;
                if (screen instanceof BOScreen bo)
                {
                    final Pane pane = findPane(bo.getWindow(), arg.toLowerCase(Locale.ROOT), Button.class);
                    if (pane != null)
                    {
                        pane.handleClick(pane.getX() + pane.getWidth() / 2.0, pane.getY() + pane.getHeight() / 2.0);
                        return "ok (BlockUI " + pane.getID() + ")";
                    }
                }
                if (screen != null)
                {
                    final int[] at = find(screen, arg);
                    if (at != null)
                    {
                        click(screen, at[0], at[1]);
                        return "ok (" + at[0] + "," + at[1] + ")";
                    }
                }
                return waited >= 40 ? "ERREUR pas de bouton « " + arg + " » sur " + describe(screen) + " | " + buttons(screen) : null;
            }
            case "clickat":
                if (mc.screen == null)
                {
                    return "ERREUR aucun écran";
                }
                click(mc.screen, Integer.parseInt(a[0]), Integer.parseInt(a[1]));
                return "ok";
            case "type":
                if (mc.screen == null)
                {
                    return "ERREUR aucun écran";
                }
                for (final char c : arg.toCharArray())
                {
                    mc.screen.charTyped(c, 0);
                }
                return "ok";
            case "settext":
            {
                if (!(mc.screen instanceof BOScreen bo))
                {
                    return "ERREUR pas une fenêtre BlockUI";
                }
                final Pane field = findPane(bo.getWindow(), a[0].toLowerCase(Locale.ROOT), TextField.class);
                if (field == null)
                {
                    return "ERREUR pas de champ " + a[0];
                }
                ((TextField) field).setText(arg.substring(a[0].length()).trim());
                return "ok";
            }
            case "key":
                if (mc.screen == null)
                {
                    return "ERREUR aucun écran";
                }
                mc.screen.keyPressed(Integer.parseInt(a[0]), 0, 0);
                mc.screen.keyReleased(Integer.parseInt(a[0]), 0, 0);
                return "ok";
            case "press":
                for (final KeyMapping mapping : mc.options.keyMappings)
                {
                    if (mapping.getName().contains(arg))
                    {
                        KeyMapping.click(mapping.getKey());
                        return "ok " + mapping.getName();
                    }
                }
                return "ERREUR touche inconnue";
            case "close":
                if (mc.screen != null)
                {
                    mc.screen.onClose();
                }
                return "ok";
            case "use":
            {
                if (player == null || mc.gameMode == null)
                {
                    return "ERREUR pas en jeu";
                }
                final BlockPos pos = new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                final Direction face = a.length > 3 ? Direction.byName(a[3]) : Direction.UP;
                final Vec3 hit = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
                return "ok " + mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
            }
            case "useitem":
                if (player == null || mc.gameMode == null)
                {
                    return "ERREUR pas en jeu";
                }
                return "ok " + mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            case "hit":
            {
                if (player == null || mc.gameMode == null || mc.level == null)
                {
                    return "ERREUR pas en jeu";
                }
                final Entity target = mc.level.getEntities(player, player.getBoundingBox().inflate(4.5), e -> e.isAlive() && e.isPickable()
                                                     && (arg.isEmpty() || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().contains(arg)))
                                        .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                if (target == null)
                {
                    return "ERREUR aucune cible";
                }
                mc.gameMode.attack(player, target);
                player.swing(InteractionHand.MAIN_HAND);
                return "ok " + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + " #" + target.getId();
            }
            case "slot":
                if (player == null)
                {
                    return "ERREUR pas en jeu";
                }
                player.getInventory().selected = Integer.parseInt(a[0]);
                player.connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().selected));
                return "ok " + player.getMainHandItem();
            case "look":
                if (player == null)
                {
                    return "ERREUR pas en jeu";
                }
                player.setYRot(Float.parseFloat(a[0]));
                player.setXRot(Float.parseFloat(a[1]));
                return "ok";
            case "sneak":
                mc.options.keyShift.setDown(arg.equals("on"));
                return "ok";
            case "walk":
                mc.options.keyUp.setDown(waited < Integer.parseInt(a[0]));
                return waited >= Integer.parseInt(a[0]) ? "ok" : null;
            case "shot":
            {
                final Path file = dir().resolve("out").resolve(arg + ".png");
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget()))
                {
                    Files.createDirectories(file.getParent());
                    image.writeToFile(file);
                }
                catch (final IOException e)
                {
                    return "ERREUR " + e.getMessage();
                }
                return "ok " + file;
            }
            case "state":
                if (player == null)
                {
                    return "hors jeu, écran " + describe(mc.screen);
                }
                return String.format(Locale.ROOT, "%s pos %.1f %.1f %.1f, vie %.1f%s, %s, %s, main %s, écran %s", player.getName().getString(), player.getX(),
                  player.getY(), player.getZ(), player.getHealth(), player.isDeadOrDying() ? " (MORT)" : "", mc.gameMode == null ? "?" : mc.gameMode.getPlayerMode(),
                  player.level().dimension().location(), player.getMainHandItem(), describe(mc.screen));
            case "inv":
            {
                if (player == null)
                {
                    return "ERREUR pas en jeu";
                }
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                {
                    final ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty())
                    {
                        sb.append(i).append('=').append(stack.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append("; ");
                    }
                }
                return sb.toString();
            }
            default:
                return "ERREUR action inconnue : " + verb;
        }
    }

    private static int[] find(final Screen screen, final String label)
    {
        final String needle = label.toLowerCase(Locale.ROOT);
        for (final Seen seen : lastFrame)
        {
            if (seen.enabled() && seen.label() != null && seen.label().toLowerCase(Locale.ROOT).contains(needle))
            {
                return new int[] {seen.x() + seen.w() / 2, seen.y() + seen.h() / 2};
            }
        }
        for (final GuiEventListener child : screen.children())
        {
            if (child instanceof AbstractWidget widget && widget.active && widget.visible
                  && widget.getMessage().getString().toLowerCase(Locale.ROOT).contains(needle))
            {
                return new int[] {widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2};
            }
        }
        return null;
    }

    /** The first visible, enabled pane of that type (and its sub-panes) whose id or text contains the needle. */
    private static Pane findPane(final Pane pane, final String needle, final Class<? extends Pane> type)
    {
        if (!pane.isVisible())
        {
            return null;
        }
        if (type.isInstance(pane) && pane.isEnabled() && (pane.getID().toLowerCase(Locale.ROOT).contains(needle)
              || pane instanceof AbstractTextElement text && text.getTextAsString().toLowerCase(Locale.ROOT).contains(needle)))
        {
            return pane;
        }
        if (pane instanceof View view)
        {
            for (final Pane child : view.getChildren())
            {
                final Pane found = findPane(child, needle, type);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private static void describePanes(final Pane pane, final StringBuilder sb)
    {
        if (!pane.isVisible())
        {
            return;
        }
        if (pane instanceof Button || pane instanceof TextField)
        {
            final String text = pane instanceof AbstractTextElement t ? t.getTextAsString() : "";
            sb.append(pane.isEnabled() ? "{" : "(").append(pane.getID()).append(text.isEmpty() ? "" : ":" + text).append(pane.isEnabled() ? "} " : ") ");
        }
        if (pane instanceof View view)
        {
            for (final Pane child : view.getChildren())
            {
                describePanes(child, sb);
            }
        }
    }

    private static void click(final Screen screen, final int x, final int y)
    {
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static String buttons(final Screen screen)
    {
        final StringBuilder sb = new StringBuilder();
        for (final Seen seen : lastFrame)
        {
            sb.append(seen.enabled() ? "[" : "(").append(seen.label()).append(seen.enabled() ? "]" : ")").append('@').append(seen.x() + seen.w() / 2)
              .append(',').append(seen.y() + seen.h() / 2).append(' ');
        }
        if (screen instanceof BOScreen bo)
        {
            describePanes(bo.getWindow(), sb);
        }
        if (screen != null)
        {
            for (final GuiEventListener child : screen.children())
            {
                if (child instanceof AbstractWidget widget && widget.visible)
                {
                    sb.append(widget.active ? "<" : "(").append(widget.getMessage().getString()).append(widget.active ? ">" : ")").append('@')
                      .append(widget.getX() + widget.getWidth() / 2).append(',').append(widget.getY() + widget.getHeight() / 2).append(' ');
                }
            }
        }
        return sb.toString().trim();
    }

    private static String describe(final Screen screen)
    {
        return screen == null ? "aucun" : screen.getClass().getSimpleName() + " « " + screen.getTitle().getString() + " »";
    }

    private static void log(final String line)
    {
        OUT.add(ticks + " " + line);
    }

    private static void readInbox()
    {
        final Path inbox = dir().resolve("inbox.txt");
        if (!Files.exists(inbox))
        {
            return;
        }
        try
        {
            for (final String line : Files.readAllLines(inbox, StandardCharsets.UTF_8))
            {
                if (!line.isBlank() && !line.trim().startsWith("#"))
                {
                    QUEUE.add(line);
                }
            }
            Files.delete(inbox);
        }
        catch (final IOException e)
        {
            InterregneMod.LOGGER.warn("[playdriver] inbox illisible", e);
        }
    }

    private static void flush()
    {
        if (OUT.isEmpty())
        {
            return;
        }
        try
        {
            Files.writeString(dir().resolve("outbox.txt"), String.join("\n", OUT) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
              StandardOpenOption.APPEND);
            OUT.clear();
        }
        catch (final IOException e)
        {
            InterregneMod.LOGGER.warn("[playdriver] outbox", e);
        }
    }
}
