// [claude-build] Mod Grappin 2.0 : grappin a balancier qui ne s'accroche qu'aux points d'ancrage (bloc grappin:ancrage, tag grappin:ancrages).
package fr.atelier.grappin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Grappin.MODID)
public class Grappin {
    public static final String MODID = "grappin";

    public static final DeferredRegister.Blocks BLOCS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> ONGLETS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Les blocs ou le crochet tient : le point d'ancrage, et tout ce qu'on ajoute au tag grappin:ancrages. */
    public static final TagKey<Block> ANCRAGES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MODID, "ancrages"));

    /** Point d'ancrage : anneau de fer scelle dans la pierre, faiblement lumineux pour qu'on le repere. */
    public static final DeferredBlock<Block> ANCRAGE = BLOCS.registerSimpleBlock("ancrage",
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(50.0F, 1200.0F).sound(SoundType.CHAIN).lightLevel(etat -> 7));
    public static final DeferredItem<BlockItem> ANCRAGE_ITEM = ITEMS.registerSimpleBlockItem("ancrage", ANCRAGE);

    /** Le grappin : pas d'usure, une seule par case. Il s'obtient par /give ou dans l'inventaire creatif. */
    public static final DeferredItem<GrappinItem> GRAPPIN = ITEMS.register("grappin", () -> new GrappinItem(new Item.Properties().stacksTo(1)));

    /** Le crochet lance par le grappin. */
    public static final DeferredHolder<EntityType<?>, EntityType<Crochet>> CROCHET = ENTITES.register("crochet",
            () -> EntityType.Builder.<Crochet>of(Crochet::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("crochet"));

    /** Onglet creatif dedie aux objets de donjon (le grappin et son point d'ancrage ; la place des futurs objets : voile, amulette...). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ONGLET = ONGLETS.register("donjon", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.grappin.donjon"))
            .icon(() -> GRAPPIN.get().getDefaultInstance())
            .displayItems((parametres, sortie) -> {
                sortie.accept(GRAPPIN.get());
                sortie.accept(ANCRAGE_ITEM.get());
            })
            .build());

    public Grappin(IEventBus modBus, ModContainer container) {
        BLOCS.register(modBus);
        ITEMS.register(modBus);
        ENTITES.register(modBus);
        ONGLETS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Reglages.SPEC);
        modBus.addListener(this::reseau);
    }

    private void reseau(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(LacherPayload.TYPE, LacherPayload.CODEC, LacherPayload::traiter);
    }
}
