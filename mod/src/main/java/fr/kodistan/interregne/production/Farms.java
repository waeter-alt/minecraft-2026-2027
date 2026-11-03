package fr.kodistan.interregne.production;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.kodistan.interregne.Config;
import fr.kodistan.interregne.InterregneMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A farm is one crop: the farmer hut alone yields it from the start (wheat until the player picks another), so a
 * colony wanting several crops builds several farms. Scarecrow fields are optional decoration that boosts the farms of
 * their crop: they can only be sown with a crop one of the colony's farms grows, are planted ripe at once, are never
 * harvested and cannot be broken or trampled.
 */
@EventBusSubscriber(modid = InterregneMod.MODID)
public final class Farms
{
    public enum Crop
    {
        BLE("ble", "Blé", Items.WHEAT_SEEDS, Blocks.WHEAT, Items.WHEAT),
        CAROTTE("carotte", "Carotte", Items.CARROT, Blocks.CARROTS, Items.CARROT),
        PATATE("patate", "Pomme de terre", Items.POTATO, Blocks.POTATOES, Items.POTATO),
        BETTERAVE("betterave", "Betterave", Items.BEETROOT_SEEDS, Blocks.BEETROOTS, Items.BEETROOT);

        private final String id;
        private final String label;
        private final Item   seed;
        private final Block  plant;
        private final Item   produce;

        Crop(final String id, final String label, final Item seed, final Block plant, final Item produce)
        {
            this.id = id;
            this.label = label;
            this.seed = seed;
            this.plant = plant;
            this.produce = produce;
        }

        public String id()
        {
            return id;
        }

        public String label()
        {
            return label;
        }

        @Nullable
        public static Crop byId(final String id)
        {
            for (final Crop crop : values())
            {
                if (crop.id.equals(id.toLowerCase(Locale.ROOT)))
                {
                    return crop;
                }
            }
            return null;
        }

        @Nullable
        public static Crop bySeed(final ItemStack seed)
        {
            for (final Crop crop : values())
            {
                if (seed.is(crop.seed))
                {
                    return crop;
                }
            }
            return null;
        }
    }

    private Farms()
    {
    }

    /**
     * One production cycle of a staffed farm: its crop by level, plus its share of the fields sown with that crop.
     */
    public static void produce(final IColony colony, final IBuilding farm)
    {
        final MinecraftServer server = colony.getWorld().getServer();
        final Crop crop = cropOf(server, farm);
        int amount = farm.getBuildingLevel() * Config.PRODUCTION_PER_LEVEL.get();

        final int fields = fieldsOf(colony, crop).size();
        if (fields > 0)
        {
            final long farms = farmsOf(colony).stream().filter(other -> Huts.staffed(other) && cropOf(server, other) == crop).count();
            amount += (int) Math.ceil(fields * Config.FIELD_BONUS.get() / (double) Math.max(1, farms));
        }
        Huts.give(farm, new ItemStack(crop.produce, amount));
        if (crop == Crop.BLE)
        {
            Huts.give(farm, new ItemStack(Items.WHEAT_SEEDS, Math.max(1, amount / 2)));
        }
    }

    /**
     * Ask the player which crop the farm grows, as clickable choices.
     */
    public static void offerChoice(final ServerPlayer player, final IBuilding farm)
    {
        final Crop current = cropOf(player.server, farm);
        final BlockPos pos = farm.getPosition();
        final MutableComponent line = Component.literal("🌾 Culture de cette ferme (" + current.label() + ") : ").withStyle(ChatFormatting.GOLD);
        for (final Crop crop : Crop.values())
        {
            final String command = "/interregne ferme " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + crop.id();
            line.append(Component.literal("[" + crop.label() + "] ").withStyle(style -> style
              .withColor(crop == current ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
              .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
              .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Cette ferme produira : " + crop.label())))));
        }
        player.sendSystemMessage(line);
    }

    /**
     * A field may only be sown with the crop of one of the colony's farms.
     *
     * @return true if the seed is allowed.
     */
    public static boolean allowSeed(final ServerPlayer player, final IColony colony, final ItemStack seed)
    {
        final Crop crop = Crop.bySeed(seed);
        final List<Crop> grown = cropsGrown(colony);
        if (crop != null && grown.contains(crop))
        {
            return true;
        }
        player.sendSystemMessage(Component.literal(grown.isEmpty()
                                                     ? "Construis d'abord une ferme : un champ renforce la ferme qui produit la même culture."
                                                     : "Ce champ ne peut porter que la culture d'une de tes fermes : "
                                                         + String.join(", ", grown.stream().map(Crop::label).toList()) + ".")
                                   .withStyle(ChatFormatting.RED));
        return false;
    }

    /**
     * Turn a freshly sown field into ripe crops on farmland, for show.
     */
    public static void plantField(final ServerLevel level, final FarmField field)
    {
        final Crop crop = Crop.bySeed(field.getSeed());
        if (crop == null || !(crop.plant instanceof CropBlock plant))
        {
            return;
        }
        final BlockPos center = field.getPosition();
        for (int x = center.getX() - field.getRadius(Direction.WEST); x <= center.getX() + field.getRadius(Direction.EAST); x++)
        {
            for (int z = center.getZ() - field.getRadius(Direction.NORTH); z <= center.getZ() + field.getRadius(Direction.SOUTH); z++)
            {
                final BlockPos ground = new BlockPos(x, center.getY() - 1, z);
                if ((x == center.getX() && z == center.getZ()) || !level.getBlockState(ground).is(BlockTags.DIRT) && !level.getBlockState(ground).is(Blocks.FARMLAND))
                {
                    continue;
                }
                final BlockPos above = ground.above();
                if (!level.getBlockState(above).isAir() && !(level.getBlockState(above).getBlock() instanceof CropBlock))
                {
                    continue;
                }
                level.setBlockAndUpdate(ground, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
                level.setBlockAndUpdate(above, plant.getStateForAge(plant.getMaxAge()));
            }
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 30, 2.0, 0.5, 2.0, 0.0);
    }

    /**
     * Crops and farmland of a sown field cannot be broken (except in creative).
     */
    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event)
    {
        if (event.getPlayer().isCreative())
        {
            return;
        }
        if ((event.getState().getBlock() instanceof CropBlock || event.getState().is(Blocks.FARMLAND)) && inSownField(event.getLevel(), event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTrample(final BlockEvent.FarmlandTrampleEvent event)
    {
        if (inSownField(event.getLevel(), event.getPos()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRegister(final RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("interregne")
          .then(Commands.literal("ferme")
                  .then(Commands.argument("hutte", BlockPosArgument.blockPos())
                          .then(Commands.argument("culture", StringArgumentType.word())
                                  .executes(context -> choose(context.getSource(), BlockPosArgument.getBlockPos(context, "hutte"),
                                    StringArgumentType.getString(context, "culture")))))));
    }

    private static int choose(final CommandSourceStack source, final BlockPos pos, final String id)
    {
        final ServerPlayer player = source.getPlayer();
        final Crop crop = Crop.byId(id);
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(source.getLevel(), pos);
        final IBuilding farm = colony == null ? null : colony.getBuildingManager().getBuilding(pos);
        if (player == null || crop == null || farm == null || !Huts.FARMER.equals(Huts.id(farm)))
        {
            source.sendFailure(Component.literal("Ferme ou culture introuvable."));
            return 0;
        }
        if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            source.sendFailure(Component.literal("Seuls les responsables de la colonie choisissent ses cultures."));
            return 0;
        }
        Data.get(player.server).crops.put(key(farm), crop.id());
        Data.get(player.server).setDirty();
        source.sendSuccess(() -> Component.literal("Cette ferme produit désormais : " + crop.label() + ".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static Crop cropOf(final MinecraftServer server, final IBuilding farm)
    {
        final Crop crop = Crop.byId(Data.get(server).crops.getOrDefault(key(farm), Crop.BLE.id()));
        return crop == null ? Crop.BLE : crop;
    }

    private static List<Crop> cropsGrown(final IColony colony)
    {
        final List<Crop> crops = new ArrayList<>();
        for (final IBuilding farm : farmsOf(colony))
        {
            final Crop crop = cropOf(colony.getWorld().getServer(), farm);
            if (farm.getBuildingLevel() > 0 && !crops.contains(crop))
            {
                crops.add(crop);
            }
        }
        return crops;
    }

    private static List<IBuilding> farmsOf(final IColony colony)
    {
        return colony.getBuildingManager().getBuildings().values().stream().filter(building -> Huts.FARMER.equals(Huts.id(building))).toList();
    }

    private static List<IBuildingExtension> fieldsOf(final IColony colony, final Crop crop)
    {
        return colony.getBuildingManager().getBuildingExtensions(extension -> extension instanceof FarmField field && field.getSeed().is(crop.seed));
    }

    private static boolean inSownField(final LevelAccessor accessor, final BlockPos pos)
    {
        if (!(accessor instanceof Level level))
        {
            return false;
        }
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null)
        {
            return false;
        }
        for (final IBuildingExtension extension : colony.getBuildingManager().getBuildingExtensions(extension -> extension instanceof FarmField))
        {
            final FarmField field = (FarmField) extension;
            final BlockPos center = field.getPosition();
            if (Crop.bySeed(field.getSeed()) != null
                  && pos.getX() >= center.getX() - field.getRadius(Direction.WEST) && pos.getX() <= center.getX() + field.getRadius(Direction.EAST)
                  && pos.getZ() >= center.getZ() - field.getRadius(Direction.NORTH) && pos.getZ() <= center.getZ() + field.getRadius(Direction.SOUTH)
                  && pos.getY() >= center.getY() - 1 && pos.getY() <= center.getY())
            {
                return true;
            }
        }
        return false;
    }

    private static String key(final IBuilding farm)
    {
        return farm.getColony().getID() + ":" + farm.getPosition().asLong();
    }

    /**
     * The crop chosen for each farm, saved with the world.
     */
    private static final class Data extends SavedData
    {
        private static final String NAME = "interregne_farms";

        private final Map<String, String> crops = new HashMap<>();

        private static Data get(final MinecraftServer server)
        {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Data::new, Data::load, null), NAME);
        }

        @Override
        public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
        {
            final CompoundTag crops = new CompoundTag();
            this.crops.forEach(crops::putString);
            tag.put("crops", crops);
            return tag;
        }

        private static Data load(final CompoundTag tag, final HolderLookup.Provider provider)
        {
            final Data data = new Data();
            final CompoundTag crops = tag.getCompound("crops");
            for (final String key : crops.getAllKeys())
            {
                data.crops.put(key, crops.getString(key));
            }
            return data;
        }
    }
}
