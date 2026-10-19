package com.postedsignage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Central content registry and creative-tab ordering for signage, decorative blocks and the drinking fountain. */
@Mod(PostedSignage.MOD_ID)
public final class PostedSignage {
    public static final String MOD_ID = "posted_signage";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<TrashBinMenu>> TRASH_BIN_MENU =
            MENUS.register("trash_bin", () -> new net.minecraft.world.inventory.MenuType<>(
                    TrashBinMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredBlock<CitDoorBlock> CIT_SHORT_DOOR = BLOCKS.register("cit_short_door_test", () -> new CitDoorBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),1,2));
    public static final DeferredBlock<CitDoorBlock> CIT_WIDE_DOOR = BLOCKS.register("cit_wide_door_test", () -> new CitDoorBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),2,3));
    public static final DeferredBlock<CitDoorBlock> CLEAR_SHORT_DOOR = BLOCKS.register("clear_short_door_test", () -> new CitDoorBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),1,2,false));
    public static final DeferredBlock<CitDoorBlock> CLEAR_WIDE_DOOR = BLOCKS.register("clear_wide_door_test", () -> new CitDoorBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),2,3,false));
    public static final DeferredBlock<CitSwingBlock> CIT_DOOR_SWING = BLOCKS.register("cit_door_swing", () -> new CitSwingBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final DeferredBlock<CitGlassBlock> CIT_GLASS = BLOCKS.register("cit_glass_panel", () -> new CitGlassBlock(BlockBehaviour.Properties.of().strength(.6F).sound(SoundType.GLASS).noOcclusion()));
    public static final DeferredItem<BlockItem> CIT_SHORT_DOOR_ITEM=ITEMS.registerSimpleBlockItem("cit_short_door_test",CIT_SHORT_DOOR);
    public static final DeferredItem<BlockItem> CIT_WIDE_DOOR_ITEM=ITEMS.registerSimpleBlockItem("cit_wide_door_test",CIT_WIDE_DOOR);
    public static final DeferredItem<BlockItem> CLEAR_SHORT_DOOR_ITEM=ITEMS.registerSimpleBlockItem("clear_short_door_test",CLEAR_SHORT_DOOR);
    public static final DeferredItem<BlockItem> CLEAR_WIDE_DOOR_ITEM=ITEMS.registerSimpleBlockItem("clear_wide_door_test",CLEAR_WIDE_DOOR);
    public static final DeferredItem<BlockItem> CIT_GLASS_ITEM=ITEMS.registerSimpleBlockItem("cit_glass_panel",CIT_GLASS);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CitDoorEntity>> CIT_DOOR_ENTITY=BLOCK_ENTITY_TYPES.register("cit_door",()->BlockEntityType.Builder.of(CitDoorEntity::new,CIT_SHORT_DOOR.get(),CIT_WIDE_DOOR.get(),CLEAR_SHORT_DOOR.get(),CLEAR_WIDE_DOOR.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CitSwingEntity>> CIT_SWING_ENTITY=BLOCK_ENTITY_TYPES.register("cit_swing",()->BlockEntityType.Builder.of(CitSwingEntity::new,CIT_DOOR_SWING.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CitGlassEntity>> CIT_GLASS_ENTITY=BLOCK_ENTITY_TYPES.register("cit_glass",()->BlockEntityType.Builder.of(CitGlassEntity::new,CIT_GLASS.get()).build(null));

    public static final DeferredBlock<VerticalSlabBlock> BRICK_VERTICAL_SLAB_BLOCK = BLOCKS.register(
            "brick_vertical_slab", () -> new VerticalSlabBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BRICKS)));
    public static final DeferredBlock<VerticalSlabBlock> SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK = BLOCKS.register(
            "smooth_quartz_vertical_slab", () -> new VerticalSlabBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ)));
    public static final DeferredItem<BlockItem> BRICK_VERTICAL_SLAB = ITEMS.registerSimpleBlockItem("brick_vertical_slab", BRICK_VERTICAL_SLAB_BLOCK);
    public static final DeferredItem<BlockItem> SMOOTH_QUARTZ_VERTICAL_SLAB = ITEMS.registerSimpleBlockItem("smooth_quartz_vertical_slab", SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK);
    public static final DeferredBlock<TrashBinBlock> TRASH_BIN_BLOCK = BLOCKS.register("trash_bin", () -> new TrashBinBlock(BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<FacilityDoorBlock> WIDE_GLASS_DOOR_BLOCK = BLOCKS.register("wide_glass_door", () -> new FacilityDoorBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion(), 2, 3));
    public static final DeferredBlock<FacilityDoorBlock> SHORT_GLASS_DOOR_BLOCK = BLOCKS.register("short_glass_door", () -> new FacilityDoorBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion(), 1, 2));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrashBinBlockEntity>> TRASH_BIN_ENTITY = BLOCK_ENTITY_TYPES.register(
            "trash_bin", () -> BlockEntityType.Builder.of(TrashBinBlockEntity::new, TRASH_BIN_BLOCK.get()).build(null));
    public static final DeferredItem<BlockItem> TRASH_BIN = ITEMS.registerSimpleBlockItem("trash_bin", TRASH_BIN_BLOCK);
    public static final DeferredBlock<TextureCoverBlock> TEXTURE_COVER_BLOCK = BLOCKS.register(
            "texture_cover", () -> new TextureCoverBlock(BlockBehaviour.Properties.of().strength(0).noCollission().noOcclusion().noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TextureCoverBlockEntity>> TEXTURE_COVER_ENTITY = BLOCK_ENTITY_TYPES.register(
            "texture_cover", () -> BlockEntityType.Builder.of(TextureCoverBlockEntity::new, TEXTURE_COVER_BLOCK.get()).build(null));
    public static final DeferredItem<TextureApplicatorItem> TEXTURE_APPLICATOR = ITEMS.register("texture_applicator", () -> new TextureApplicatorItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));

    public static final DeferredBlock<DrinkingFountainBlock> DRINKING_FOUNTAIN_BLOCK = BLOCKS.register(
            "drinking_fountain", () -> new DrinkingFountainBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> DRINKING_FOUNTAIN =
            ITEMS.registerSimpleBlockItem("drinking_fountain", DRINKING_FOUNTAIN_BLOCK);

    public static final DeferredBlock<WallSignBlock> RIGHT_ARROW_SIGN_BLOCK = signBlock("right_arrow_sign");
    public static final DeferredBlock<WallSignBlock> FIRE_EXTINGUISHER_SIGN_BLOCK = signBlock("fire_extinguisher_sign");
    public static final DeferredBlock<WallSignBlock> EMERGENCY_EXIT_SIGN_BLOCK = signBlock("emergency_exit_sign");
    public static final DeferredBlock<WallSignBlock> DO_NOT_USE_ELEVATOR_SIGN_BLOCK = signBlock("do_not_use_elevator_sign");
    public static final DeferredBlock<WallSignBlock> EMERGENCY_STAIRS_SIGN_BLOCK = signBlock("emergency_stairs_sign");
    public static final DeferredBlock<ConnectingPlanterBlock> SANSEVIERIA_PLANTER_BLOCK = BLOCKS.register(
            "sansevieria_planter",
            () -> new ConnectingPlanterBlock(BlockBehaviour.Properties.of()
                    .strength(0.8F)
                    .sound(SoundType.STONE)
                    .noOcclusion()));
    public static final DeferredBlock<StatueReliefBlock> STATUE_RELIEF_BLOCK = BLOCKS.register(
            "statue_relief",
            () -> new StatueReliefBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    private static final List<String> RESTROOM_SIGN_NAMES = List.of(
            "mens_restroom_sign",
            "womens_restroom_sign",
            "mixed_restroom_sign",
            "mens_restroom_sign_green",
            "mens_restroom_sign_red",
            "mens_restroom_sign_orange",
            "mens_restroom_sign_yellow",
            "mens_restroom_sign_light_blue",
            "womens_restroom_sign_green",
            "womens_restroom_sign_red",
            "womens_restroom_sign_orange",
            "womens_restroom_sign_yellow",
            "womens_restroom_sign_light_blue",
            "mixed_restroom_sign_green",
            "mixed_restroom_sign_red",
            "mixed_restroom_sign_orange",
            "mixed_restroom_sign_yellow",
            "mixed_restroom_sign_light_blue");

    public static final Map<String, DeferredBlock<HangingSignBlock>> RESTROOM_SIGN_BLOCKS =
            registerRestroomBlocks();

    private static final List<String> CUSTOMIZABLE_HANGING_SIGN_NAMES = List.of(
            "customizable_hanging_sign",
            "customizable_hanging_sign_green",
            "customizable_hanging_sign_red",
            "customizable_hanging_sign_yellow",
            "customizable_hanging_sign_light_blue",
            "customizable_hanging_sign_purple",
            "customizable_hanging_sign_orange");

    public static final Map<String, DeferredBlock<CustomizableHangingSignBlock>>
            CUSTOMIZABLE_HANGING_SIGN_BLOCKS = registerCustomizableHangingSignBlocks();

    public static final DeferredBlock<CustomizableHangingSignBlock> CUSTOMIZABLE_HANGING_SIGN_BLOCK =
            CUSTOMIZABLE_HANGING_SIGN_BLOCKS.get("customizable_hanging_sign");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CustomizableHangingSignBlockEntity>>
            CUSTOMIZABLE_HANGING_SIGN_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
                    "customizable_hanging_sign",
                    () -> BlockEntityType.Builder.of(
                                    CustomizableHangingSignBlockEntity::new,
                                    CUSTOMIZABLE_HANGING_SIGN_BLOCKS.values().stream()
                                            .map(block -> (Block) block.get())
                                            .toArray(Block[]::new))
                            .build(null));

    public static final DeferredItem<BlockItem> RIGHT_ARROW_SIGN = ITEMS.registerSimpleBlockItem("right_arrow_sign", RIGHT_ARROW_SIGN_BLOCK);
    public static final DeferredItem<BlockItem> FIRE_EXTINGUISHER_SIGN = ITEMS.registerSimpleBlockItem("fire_extinguisher_sign", FIRE_EXTINGUISHER_SIGN_BLOCK);
    public static final DeferredItem<BlockItem> EMERGENCY_EXIT_SIGN = ITEMS.registerSimpleBlockItem("emergency_exit_sign", EMERGENCY_EXIT_SIGN_BLOCK);
    public static final DeferredItem<BlockItem> DO_NOT_USE_ELEVATOR_SIGN = ITEMS.registerSimpleBlockItem("do_not_use_elevator_sign", DO_NOT_USE_ELEVATOR_SIGN_BLOCK);
    public static final DeferredItem<BlockItem> EMERGENCY_STAIRS_SIGN = ITEMS.registerSimpleBlockItem("emergency_stairs_sign", EMERGENCY_STAIRS_SIGN_BLOCK);
    public static final DeferredItem<BlockItem> SANSEVIERIA_PLANTER =
            ITEMS.registerSimpleBlockItem("sansevieria_planter", SANSEVIERIA_PLANTER_BLOCK);
    public static final DeferredItem<BlockItem> STATUE_RELIEF =
            ITEMS.registerSimpleBlockItem("statue_relief", STATUE_RELIEF_BLOCK);
    public static final Map<String, DeferredItem<BlockItem>> RESTROOM_SIGNS = registerRestroomItems();
    public static final Map<String, DeferredItem<BlockItem>> CUSTOMIZABLE_HANGING_SIGNS =
            registerCustomizableHangingSignItems();
    public static final DeferredItem<BlockItem> CUSTOMIZABLE_HANGING_SIGN =
            CUSTOMIZABLE_HANGING_SIGNS.get("customizable_hanging_sign");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POSTED_SIGNAGE_TAB =
            CREATIVE_TABS.register("posted_signage", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.posted_signage"))
                    .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                    .icon(() -> EMERGENCY_EXIT_SIGN.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(BRICK_VERTICAL_SLAB.get());
                        output.accept(SMOOTH_QUARTZ_VERTICAL_SLAB.get());
                        output.accept(TEXTURE_APPLICATOR.get());
                        output.accept(TRASH_BIN.get());
                        output.accept(CIT_SHORT_DOOR_ITEM.get());
                        output.accept(CIT_WIDE_DOOR_ITEM.get());
                        output.accept(CLEAR_SHORT_DOOR_ITEM.get());
                        output.accept(CLEAR_WIDE_DOOR_ITEM.get());
                        output.accept(CIT_GLASS_ITEM.get());
                        output.accept(RIGHT_ARROW_SIGN.get());
                        output.accept(DRINKING_FOUNTAIN.get());
                        output.accept(SANSEVIERIA_PLANTER.get());
                        output.accept(STATUE_RELIEF.get());
                        CUSTOMIZABLE_HANGING_SIGNS.values().forEach(item -> output.accept(item.get()));
                        RESTROOM_SIGNS.values().forEach(item -> output.accept(item.get()));
                        output.accept(FIRE_EXTINGUISHER_SIGN.get());
                        output.accept(EMERGENCY_EXIT_SIGN.get());
                        output.accept(DO_NOT_USE_ELEVATOR_SIGN.get());
                        output.accept(EMERGENCY_STAIRS_SIGN.get());
                    })
                    .build());

    // Conecta los registros y eventos del mod.
    public PostedSignage(IEventBus modEventBus, ModContainer modContainer) {
        // Legacy placed blocks stay compatible; retired inventory items resolve to the new clear doors.
        var oldDoor = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "glass_door");
        var shortDoor = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "short_glass_door");
        BLOCKS.addAlias(oldDoor, shortDoor);
        var clearShort = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "clear_short_door_test");
        var clearWide = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "clear_wide_door_test");
        ITEMS.addAlias(oldDoor, clearShort);
        ITEMS.addAlias(shortDoor, clearShort);
        ITEMS.addAlias(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "wide_glass_door"), clearWide);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        Hydration.EFFECTS.register(modEventBus);
        modEventBus.addListener(HydrationPayload::register);
        modEventBus.addListener(CitDoorPayloads::register);
    }

    /** Keep all thin wall signs consistent in hardness, sound and non-occluding rendering. */
    private static DeferredBlock<WallSignBlock> signBlock(String name) {
        return BLOCKS.register(name, () -> new WallSignBlock(BlockBehaviour.Properties.of()
                .strength(0.2F)
                .sound(SoundType.WOOD)
                .noOcclusion()));
    }

    /** Register every fixed restroom variant from the shared ID list. */
    private static Map<String, DeferredBlock<HangingSignBlock>> registerRestroomBlocks() {
        Map<String, DeferredBlock<HangingSignBlock>> blocks = new LinkedHashMap<>();
        for (String name : RESTROOM_SIGN_NAMES) {
            blocks.put(name, BLOCKS.register(name, () -> new HangingSignBlock(BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())));
        }
        return Collections.unmodifiableMap(blocks);
    }

    /** Pair each registered restroom block with its inventory item using the same ID. */
    private static Map<String, DeferredItem<BlockItem>> registerRestroomItems() {
        Map<String, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        RESTROOM_SIGN_BLOCKS.forEach((name, block) ->
                items.put(name, ITEMS.registerSimpleBlockItem(name, block)));
        return Collections.unmodifiableMap(items);
    }

    private static Map<String, DeferredBlock<CustomizableHangingSignBlock>>
            registerCustomizableHangingSignBlocks() {
        Map<String, DeferredBlock<CustomizableHangingSignBlock>> blocks = new LinkedHashMap<>();
        for (String name : CUSTOMIZABLE_HANGING_SIGN_NAMES) {
            blocks.put(name, BLOCKS.register(name, () -> new CustomizableHangingSignBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion())));
        }
        return Collections.unmodifiableMap(blocks);
    }

    /** Reuse the block map so item IDs and creative-tab iteration cannot drift apart. */
    private static Map<String, DeferredItem<BlockItem>> registerCustomizableHangingSignItems() {
        Map<String, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        CUSTOMIZABLE_HANGING_SIGN_BLOCKS.forEach((name, block) ->
                items.put(name, ITEMS.registerSimpleBlockItem(name, block)));
        return Collections.unmodifiableMap(items);
    }
}
