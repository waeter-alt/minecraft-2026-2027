// [claude-build] Mod Grappin 2.0 : grappin a balancier qui ne s'accroche qu'aux points d'ancrage (bloc grappin:ancrage, tag grappin:ancrages).
package fr.atelier.grappin;

import java.util.EnumMap;
import java.util.List;

import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
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
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
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
    public static final DeferredRegister<ArmorMaterial> MATERIAUX = DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);
    public static final DeferredRegister<CreativeModeTab> ONGLETS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Les blocs ou le crochet tient : le point d'ancrage, et tout ce qu'on ajoute au tag grappin:ancrages. */
    public static final TagKey<Block> ANCRAGES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MODID, "ancrages"));

    /** Point d'ancrage : anneau de fer scelle dans la pierre, faiblement lumineux pour qu'on le repere. */
    public static final DeferredBlock<Block> ANCRAGE = BLOCS.registerSimpleBlock("ancrage",
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(50.0F, 1200.0F).sound(SoundType.CHAIN).lightLevel(etat -> 7));
    public static final DeferredItem<BlockItem> ANCRAGE_ITEM = ITEMS.registerSimpleBlockItem("ancrage", ANCRAGE);

    /** Le grappin : pas d'usure, une seule par case. Il s'obtient par /give ou dans l'inventaire creatif. */
    public static final DeferredItem<GrappinItem> GRAPPIN = ITEMS.register("grappin", () -> new GrappinItem(new Item.Properties().stacksTo(1)));

    /** Materiau du voile : aucune protection ; il ne sert qu'a l'habillage (texture portee). */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAU_VOILE = MATERIAUX.register("voile", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                for (ArmorItem.Type t : ArmorItem.Type.values()) {
                    m.put(t, 0);
                }
            }), 0, SoundEvents.ARMOR_EQUIP_TURTLE, () -> Ingredient.EMPTY,
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(MODID, "voile"))), 0.0F, 0.0F));

    /** Le Voile des Profondeurs : un casque qui fait respirer sous l'eau, a reserve limitee qui se recharge en surface. */
    public static final DeferredItem<VoileItem> VOILE = ITEMS.register("voile", () -> new VoileItem(MATERIAU_VOILE, new Item.Properties()));

    /** La Paravoile : voile de planeur (comme dans Breath of the Wild). Tenue en main, ESPACE en l'air la deploie ; la jauge d'endurance se vide en vol. */
    public static final DeferredItem<ParavoileItem> PARAVOILE = ITEMS.register("paravoile", () -> new ParavoileItem(new Item.Properties()));

    /** Le crochet lance par le grappin. */
    public static final DeferredHolder<EntityType<?>, EntityType<Crochet>> CROCHET = ENTITES.register("crochet",
            () -> EntityType.Builder.<Crochet>of(Crochet::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("crochet"));

    /** Le Seraphin, boss volant du donjon n°3 (3 x 3 blocs), et son oeuf d'apparition. */
    public static final DeferredHolder<EntityType<?>, EntityType<Seraphin>> SERAPHIN = ENTITES.register("seraphin",
            () -> EntityType.Builder.<Seraphin>of(Seraphin::new, MobCategory.MONSTER)
                    .sized(3.0F, 3.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("seraphin"));
    /** Le Gardien des Racines, boss du Donjon 1 (boite du corps de 6 x 40 blocs), et son oeuf d'apparition. */
    public static final DeferredHolder<EntityType<?>, EntityType<Golem>> GOLEM = ENTITES.register("golem",
            () -> EntityType.Builder.<Golem>of(Golem::new, MobCategory.MONSTER)
                    .sized(6.0F, 40.0F)          // etroit : les pieces faibles (autour de 6,5 blocs de l'axe) depassent du corps et arretent les fleches avant lui
                    .fireImmune()
                    .clientTrackingRange(16)
                    .build("golem"));
    /** Une graine explosive lancee par le Gardien des Racines. */
    public static final DeferredHolder<EntityType<?>, EntityType<Graine>> GRAINE = ENTITES.register("graine",
            () -> EntityType.Builder.<Graine>of(Graine::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .build("graine"));
    public static final DeferredItem<DeferredSpawnEggItem> GOLEM_OEUF = ITEMS.register("golem_spawn_egg",
            () -> new DeferredSpawnEggItem(GOLEM, 0x6B4423, 0x9C1B1B, new Item.Properties()));

    /** Une plume lancee par le Seraphin. */
    public static final DeferredHolder<EntityType<?>, EntityType<Plume>> PLUME = ENTITES.register("plume",
            () -> EntityType.Builder.<Plume>of(Plume::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build("plume"));
    public static final DeferredItem<DeferredSpawnEggItem> SERAPHIN_OEUF = ITEMS.register("seraphin_spawn_egg",
            () -> new DeferredSpawnEggItem(SERAPHIN, 0xE8E8E8, 0x202020, new Item.Properties()));

    /** Onglet creatif dedie aux objets de donjon (le grappin et son point d'ancrage ; la place des futurs objets : voile, amulette...). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ONGLET = ONGLETS.register("donjon", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.grappin.donjon"))
            .icon(() -> GRAPPIN.get().getDefaultInstance())
            .displayItems((parametres, sortie) -> {
                sortie.accept(GRAPPIN.get());
                sortie.accept(ANCRAGE_ITEM.get());
                sortie.accept(VOILE.get());
                sortie.accept(PARAVOILE.get());
                sortie.accept(SERAPHIN_OEUF.get());
                sortie.accept(GOLEM_OEUF.get());
            })
            .build());

    public Grappin(IEventBus modBus, ModContainer container) {
        BLOCS.register(modBus);
        ITEMS.register(modBus);
        ENTITES.register(modBus);
        MATERIAUX.register(modBus);
        ONGLETS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Reglages.SPEC);
        modBus.addListener(this::reseau);
        modBus.addListener(this::attributs);
    }

    private void attributs(EntityAttributeCreationEvent event) {
        event.put(SERAPHIN.get(), Seraphin.attributs().build());
        event.put(GOLEM.get(), Golem.attributs().build());
    }

    private void reseau(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(LacherPayload.TYPE, LacherPayload.CODEC, LacherPayload::traiter);
        registrar.playBidirectional(PlaneurPayload.TYPE, PlaneurPayload.CODEC, PlaneurPayload::traiter);
    }
}
