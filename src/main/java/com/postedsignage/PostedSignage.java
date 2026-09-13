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

@Mod(PostedSignage.MOD_ID)
public final class PostedSignage {
    public static final String MOD_ID = "posted_signage";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

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
                        output.accept(RIGHT_ARROW_SIGN.get());
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

    public PostedSignage(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }

    private static DeferredBlock<WallSignBlock> signBlock(String name) {
        return BLOCKS.register(name, () -> new WallSignBlock(BlockBehaviour.Properties.of()
                .strength(0.2F)
                .sound(SoundType.WOOD)
                .noOcclusion()));
    }

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

    private static Map<String, DeferredItem<BlockItem>> registerCustomizableHangingSignItems() {
        Map<String, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        CUSTOMIZABLE_HANGING_SIGN_BLOCKS.forEach((name, block) ->
                items.put(name, ITEMS.registerSimpleBlockItem(name, block)));
        return Collections.unmodifiableMap(items);
    }
}
