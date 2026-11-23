// [claude-build] Mod Grappin 2.0 : grappin a balancier qui ne s'accroche qu'aux points d'ancrage (bloc donjon:ancrage, tag donjon:ancrages).
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
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Grappin.MODID)
public class Grappin {
    public static final String MODID = "donjon";

    public static final DeferredRegister.Blocks BLOCS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> ONGLETS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Les blocs ou le crochet tient : le point d'ancrage, et tout ce qu'on ajoute au tag donjon:ancrages. */
    public static final TagKey<Block> ANCRAGES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MODID, "ancrages"));

    /** Point d'ancrage : anneau de fer scelle dans la pierre, faiblement lumineux pour qu'on le repere. */
    public static final DeferredBlock<Block> ANCRAGE = BLOCS.registerSimpleBlock("ancrage",
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(50.0F, 1200.0F).sound(SoundType.CHAIN).lightLevel(etat -> 7));
    public static final DeferredItem<BlockItem> ANCRAGE_ITEM = ITEMS.registerSimpleBlockItem("ancrage", ANCRAGE);

    /** Le grappin : pas d'usure, une seule par case. Il s'obtient par /give ou dans l'inventaire creatif. */
    public static final DeferredItem<GrappinItem> GRAPPIN = ITEMS.register("grappin", () -> new GrappinItem(new Item.Properties().stacksTo(1)));

    /** La Cape lestee : se porte dans l'emplacement cape d'Accessories ; respiration, nage rapide, mode leste et attaque tourbillon sous l'eau. */
    public static final DeferredItem<CapeLesteeItem> CAPE_LESTEE = ITEMS.register("cape_lestee", () -> new CapeLesteeItem(new Item.Properties().stacksTo(1)));

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
    /** Le Rodeur des Racines, mini-boss du sous-sol 1 du Donjon Suspendu (bete a quatre pattes de 4 blocs de long), et son oeuf d'apparition. */
    public static final DeferredHolder<EntityType<?>, EntityType<Rodeur>> RODEUR = ENTITES.register("rodeur",
            () -> EntityType.Builder.<Rodeur>of(Rodeur::new, MobCategory.MONSTER)
                    .sized(1.6F, 2.4F)
                    .clientTrackingRange(10)
                    .build("rodeur"));
    /** Le Scaphandrier rouille, mini-boss du Donjon de la Station (armure de plongee de 2,6 blocs), et son harpon a chaine. */
    public static final DeferredHolder<EntityType<?>, EntityType<Scaphandrier>> SCAPHANDRIER = ENTITES.register("scaphandrier",
            () -> EntityType.Builder.<Scaphandrier>of(Scaphandrier::new, MobCategory.MONSTER)
                    .sized(1.3F, 2.6F)
                    .clientTrackingRange(10)
                    .build("scaphandrier"));
    public static final DeferredHolder<EntityType<?>, EntityType<HarponScaphandrier>> HARPON = ENTITES.register("harpon_scaphandrier",
            () -> EntityType.Builder.<HarponScaphandrier>of(HarponScaphandrier::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .build("harpon_scaphandrier"));
    public static final DeferredItem<DeferredSpawnEggItem> SCAPHANDRIER_OEUF = ITEMS.register("scaphandrier_spawn_egg",
            () -> new DeferredSpawnEggItem(SCAPHANDRIER, 0xA0762F, 0x3E7A66, new Item.Properties()));
    /** Le Leviathan, serpent de mer du Donjon 2 (boite de la tete de 5 x 5 blocs), et son oeuf d'apparition. */
    public static final DeferredHolder<EntityType<?>, EntityType<Leviathan>> LEVIATHAN = ENTITES.register("leviathan",
            () -> EntityType.Builder.<Leviathan>of(Leviathan::new, MobCategory.MONSTER)
                    .sized(5.0F, 5.0F)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)                        // le corps suit le chemin de la tete : des mises a jour a chaque tick evitent un retard visible
                    .build("leviathan"));
    public static final DeferredItem<DeferredSpawnEggItem> LEVIATHAN_OEUF = ITEMS.register("leviathan_spawn_egg",
            () -> new DeferredSpawnEggItem(LEVIATHAN, 0x1B6B6B, 0xC9B458, new Item.Properties()));

    /** Une graine explosive lancee par le Gardien des Racines. */
    public static final DeferredHolder<EntityType<?>, EntityType<Graine>> GRAINE = ENTITES.register("graine",
            () -> EntityType.Builder.<Graine>of(Graine::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSave()
                    .build("graine"));
    public static final DeferredItem<DeferredSpawnEggItem> RODEUR_OEUF = ITEMS.register("rodeur_spawn_egg",
            () -> new DeferredSpawnEggItem(RODEUR, 0x5A3A1E, 0x7A9A3A, new Item.Properties()));
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

    /** Onglet creatif dedie aux objets de donjon . */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ONGLET = ONGLETS.register("donjon", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.donjon.donjon"))
            .icon(() -> GRAPPIN.get().getDefaultInstance())
            .displayItems((parametres, sortie) -> {
                sortie.accept(GRAPPIN.get());
                sortie.accept(ANCRAGE_ITEM.get());
                DonjonBlocs.BLOCS_OBJETS.forEach(o -> sortie.accept(o.get()));
                StationBlocs.BLOCS_OBJETS.forEach(o -> sortie.accept(o.get()));
                sortie.accept(DonjonBlocs.CLE_BLEUE.get());
                sortie.accept(DonjonBlocs.CLE_JAUNE.get());
                sortie.accept(DonjonBlocs.CLE_ROUGE.get());
                sortie.accept(CAPE_LESTEE.get());
                sortie.accept(PARAVOILE.get());
                sortie.accept(SERAPHIN_OEUF.get());
                sortie.accept(GOLEM_OEUF.get());
                sortie.accept(RODEUR_OEUF.get());
                sortie.accept(LEVIATHAN_OEUF.get());
                sortie.accept(SCAPHANDRIER_OEUF.get());
            })
            .build());

    public Grappin(IEventBus modBus, ModContainer container) {
        DonjonBlocs.charger(modBus);
        BLOCS.register(modBus);
        ITEMS.register(modBus);
        ENTITES.register(modBus);
        ONGLETS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Reglages.SPEC);
        modBus.addListener(this::reseau);
        modBus.addListener(this::attributs);
    }

    private void attributs(EntityAttributeCreationEvent event) {
        event.put(SERAPHIN.get(), Seraphin.attributs().build());
        event.put(GOLEM.get(), Golem.attributs().build());
        event.put(RODEUR.get(), Rodeur.attributs().build());
        event.put(LEVIATHAN.get(), Leviathan.attributs().build());
        event.put(SCAPHANDRIER.get(), Scaphandrier.attributs().build());
    }

    private void reseau(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(LacherPayload.TYPE, LacherPayload.CODEC, LacherPayload::traiter);
        registrar.playBidirectional(PlaneurPayload.TYPE, PlaneurPayload.CODEC, PlaneurPayload::traiter);
        registrar.playBidirectional(LestePayload.TYPE, LestePayload.CODEC, LestePayload::traiter);
        registrar.playBidirectional(TourbillonPayload.TYPE, TourbillonPayload.CODEC, TourbillonPayload::traiter);
    }
}
