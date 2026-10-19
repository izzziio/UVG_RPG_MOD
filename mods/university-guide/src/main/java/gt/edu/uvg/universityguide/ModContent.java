package gt.edu.uvg.universityguide;

import gt.edu.uvg.universityguide.block.TourStopBlock;
import gt.edu.uvg.universityguide.block.entity.TourStopBlockEntity;
import gt.edu.uvg.universityguide.entity.GuideEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the guide, invisible stop marker, items and block entity before gameplay begins. */
public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UniversityGuideMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UniversityGuideMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, UniversityGuideMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UniversityGuideMod.MOD_ID);

    public static final DeferredBlock<TourStopBlock> TOUR_STOP = BLOCKS.registerBlock(
            "tour_stop",
            TourStopBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3_600_000.0F)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .noTerrainParticles()
                    .pushReaction(PushReaction.BLOCK)
    );

    public static final DeferredItem<BlockItem> TOUR_STOP_ITEM =
            ITEMS.registerSimpleBlockItem("tour_stop", TOUR_STOP, new Item.Properties());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TourStopBlockEntity>> TOUR_STOP_ENTITY =
            BLOCK_ENTITY_TYPES.register("tour_stop", () ->
                    BlockEntityType.Builder.of(TourStopBlockEntity::new, TOUR_STOP.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<GuideEntity>> GUIDE =
            ENTITY_TYPES.register("guide", () -> EntityType.Builder
                    .of(GuideEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(UniversityGuideMod.MOD_ID + ":guide"));

    // Evita crear instancias de esta clase de utilidades.
    private ModContent() {
    }

    // Conecta los registros y manejadores correspondientes.
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
    }
}
