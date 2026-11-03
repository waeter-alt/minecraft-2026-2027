package fr.kodistan.interregne.citadel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

/**
 * /interregne citadelle generer [demi-taille] | apercu [demi-taille] | annuler
 * Builds the imperial citadel around the operator, a few thousand blocks per tick so the server keeps running:
 * first the ground is levelled and cleared, then the plan is placed and its signs written.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Citadel
{
    private static final int DEFAULT_HALF   = 60;
    private static final int BLOCKS_PER_TICK = 6000;
    private static final int CLEAR_HEIGHT   = 40;
    private static final int FOUNDATION     = 4;

    @Nullable
    private static Job job;

    @Nullable
    private static Preview preview;

    private static int previewAge;

    private Citadel()
    {
    }

    /**
     * A generation in progress: terrain columns first, then the planned blocks.
     */
    private static final class Job
    {
        private final ServerLevel                          level;
        private final BlockPos                             origin;
        private final CitadelPlan                          plan;
        private final CommandSourceStack                   source;
        private final Iterator<Map.Entry<BlockPos, BlockState>> blocks;
        private int                                        column;

        private Job(final ServerLevel level, final BlockPos origin, final CitadelPlan plan, final CommandSourceStack source)
        {
            this.level = level;
            this.origin = origin;
            this.plan = plan;
            this.source = source;
            this.blocks = plan.blocks.entrySet().iterator();
        }

        /**
         * @return true when finished.
         */
        private boolean step()
        {
            final int side = plan.half() * 2 + 1;
            int budget = BLOCKS_PER_TICK;
            while (column < side * side && budget > 0)
            {
                final int x = column % side - plan.half();
                final int z = column / side - plan.half();
                column++;
                budget -= levelColumn(x, z);
            }
            if (column < side * side)
            {
                return false;
            }
            while (blocks.hasNext() && budget-- > 0)
            {
                final Map.Entry<BlockPos, BlockState> entry = blocks.next();
                level.setBlock(origin.offset(entry.getKey()), entry.getValue(), Block.UPDATE_CLIENTS);
            }
            if (blocks.hasNext())
            {
                return false;
            }
            writeSigns();
            source.sendSuccess(() -> CitadelPlan.describe(plan.landmarks, origin), true);
            return true;
        }

        /**
         * Grass on a stone foundation at ground level, air above.
         */
        private int levelColumn(final int x, final int z)
        {
            final BlockPos base = origin.offset(x, 0, z);
            int changes = 0;
            for (int y = -FOUNDATION; y < CLEAR_HEIGHT; y++)
            {
                final BlockPos pos = base.above(y);
                final BlockState wanted = y < -1 ? Blocks.STONE.defaultBlockState() : y == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                if (level.getBlockState(pos) != wanted)
                {
                    level.setBlock(pos, wanted, Block.UPDATE_CLIENTS);
                    changes++;
                }
            }
            return Math.max(1, changes);
        }

        private void writeSigns()
        {
            plan.signs.forEach((offset, lines) -> {
                if (level.getBlockEntity(origin.offset(offset)) instanceof SignBlockEntity sign)
                {
                    SignText text = new SignText();
                    for (int i = 0; i < Math.min(4, lines.length); i++)
                    {
                        text = text.setMessage(i, Component.literal(lines[i]));
                    }
                    sign.setText(text, true);
                    sign.setChanged();
                    level.sendBlockUpdated(sign.getBlockPos(), sign.getBlockState(), sign.getBlockState(), Block.UPDATE_CLIENTS);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        if (preview != null && preview.draw(previewAge++))
        {
            preview = null;
            previewAge = 0;
        }
        if (job != null)
        {
            try
            {
                if (job.step())
                {
                    job = null;
                }
            }
            catch (final RuntimeException e)
            {
                InterregneMod.LOGGER.error("Citadel generation failed", e);
                job.source.sendFailure(Component.literal("La génération de la citadelle a échoué : voir le journal."));
                job = null;
            }
        }
    }

    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("interregne")
          .then(Commands.literal("citadelle").requires(source -> source.hasPermission(2))
                  .then(Commands.literal("generer")
                          .executes(context -> generate(context.getSource(), DEFAULT_HALF))
                          .then(Commands.argument("demi-taille", IntegerArgumentType.integer(45, 120))
                                  .executes(context -> generate(context.getSource(), IntegerArgumentType.getInteger(context, "demi-taille")))))
                  .then(Commands.literal("apercu")
                          .executes(context -> preview(context.getSource(), DEFAULT_HALF))
                          .then(Commands.argument("demi-taille", IntegerArgumentType.integer(45, 120))
                                  .executes(context -> preview(context.getSource(), IntegerArgumentType.getInteger(context, "demi-taille")))))
                  .then(Commands.literal("annuler").executes(context -> {
                      job = null;
                      context.getSource().sendSuccess(() -> Component.literal("Génération de la citadelle arrêtée."), true);
                      return 1;
                  }))));
    }

    private static int generate(final CommandSourceStack source, final int half)
    {
        if (job != null)
        {
            source.sendFailure(Component.literal("Une citadelle est déjà en cours de génération."));
            return 0;
        }
        final BlockPos origin = BlockPos.containing(source.getPosition());
        final CitadelPlan plan = new CitadelPlan(half, origin.asLong()).build();
        job = new Job(source.getLevel(), origin, plan, source);
        source.sendSuccess(() -> Component.literal("⚜ Génération de la citadelle (" + (half * 2 + 1) + " × " + (half * 2 + 1)
                                                    + ") autour de toi : " + plan.blocks.size() + " blocs, quelques secondes…").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    /**
     * Outline the footprint with particles for a few seconds, to choose the spot before building.
     */
    private static int preview(final CommandSourceStack source, final int half)
    {
        final ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.literal("L'aperçu doit être lancé par un joueur."));
            return 0;
        }
        preview = new Preview(player, BlockPos.containing(source.getPosition()), half);
        previewAge = 0;
        source.sendSuccess(() -> Component.literal("Contour de la citadelle affiché pendant 15 secondes (la porte est au sud)."), false);
        return 1;
    }

    /**
     * The footprint outline shown to one player. Particles are forced so they show beyond the usual 32-block range.
     */
    private record Preview(ServerPlayer player, BlockPos origin, int half)
    {
        private static final int DURATION = 15 * 20;
        private static final int EVERY    = 10;

        /**
         * @return true when the preview is over.
         */
        private boolean draw(final int age)
        {
            if (age % EVERY == 0)
            {
                final ServerLevel level = player.serverLevel();
                for (int i = -half; i <= half; i += 2)
                {
                    for (final int[] spot : new int[][]{{i, -half}, {i, half}, {-half, i}, {half, i}})
                    {
                        level.sendParticles(player, spot[1] == half && Math.abs(spot[0]) <= 3 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, true,
                          origin.getX() + spot[0] + 0.5, origin.getY() + 1.0, origin.getZ() + spot[1] + 0.5, 4, 0, 0.8, 0, 0.0);
                    }
                }
            }
            return age >= DURATION || player.isRemoved();
        }
    }
}
