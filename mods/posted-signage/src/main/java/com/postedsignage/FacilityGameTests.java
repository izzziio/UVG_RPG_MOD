package com.postedsignage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side coverage for the generated multi-block facility objects. */
@GameTestHolder(PostedSignage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FacilityGameTests {
    private static final BlockPos START = new BlockPos(2, 2, 2);

    // Evita crear instancias de esta clase de utilidades.
    private FacilityGameTests() {
    }

    // Verifica depósitos y conservación del sobrante.
    @GameTest(template = "empty")
    public static void binRightClickStoresHeldStackAndPreservesOverflow(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(START);
        var block = PostedSignage.TRASH_BIN_BLOCK.get();
        var state = block.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var bin = (TrashBinBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        var hit = new BlockHitResult(pos.getCenter(), Direction.NORTH, pos, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 12));
        block.useItemOn(player.getMainHandItem(), state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(player.getMainHandItem().isEmpty() && bin.getItem(0).getCount() == 12, "Right-click stores entire held stack");
        for (int i = 0; i < 9; i++) bin.setItem(i, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 64));
        bin.getItem(0).setCount(60);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 12));
        block.useItemOn(player.getMainHandItem(), state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(bin.getItem(0).getCount() == 64 && player.getMainHandItem().getCount() == 8, "Partial deposit leaves overflow in hand");
        block.useItemOn(player.getMainHandItem(), state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(player.getMainHandItem().getCount() == 8, "Full bin never deletes held items");
        bin.clearContent();
        ItemStack named = new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keep my name"));
        bin.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 10));
        player.setItemInHand(InteractionHand.OFF_HAND, named);
        helper.assertTrue(bin.storeHeldItems(player, InteractionHand.OFF_HAND) == 3, "Offhand can deposit");
        helper.assertTrue(bin.getItem(0).getCount() == 10 && bin.getItem(1).getCount() == 3
                && bin.getItem(1).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME), "Different components stay separate and intact");
        bin.clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
        helper.assertTrue(bin.storeHeldItems(player, InteractionHand.MAIN_HAND) == 1 && bin.getItem(0).getCount() == 1, "Unstackable items stored safely");
        player.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STONE, 8));
        helper.assertTrue(bin.storeHeldItems(player, InteractionHand.MAIN_HAND) == 0 && player.getMainHandItem().getCount() == 8, "Distant deposit is rejected");
        helper.succeed();
    }

    // Verifica apertura completa y paso libre.
    @GameTest(template = "empty")
    public static void doorsToggleEveryPartAndClearThePassage(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(START);
        for (var block : new FacilityDoorBlock[] {PostedSignage.WIDE_GLASS_DOOR_BLOCK.get(), PostedSignage.SHORT_GLASS_DOOR_BLOCK.get()}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (int selectedX = 0; selectedX < block.width; selectedX++) for (int selectedY = 0; selectedY < block.height; selectedY++) {
                    BlockState closed = block.defaultBlockState().setValue(FacilityMultiBlock.FACING, facing);
                    level.setBlockAndUpdate(origin, closed);
                    block.setPlacedBy(level, origin, closed, null, ItemStack.EMPTY);
                    BlockPos selected = origin.relative(facing.getClockWise(), selectedX).above(selectedY);
                    var hit = new BlockHitResult(selected.getCenter(), facing, selected, false);
                    BlockPos currentOrigin = origin;
                    for (boolean open : new boolean[] {true, false}) {
                        BlockState before = level.getBlockState(selected);
                        helper.assertTrue(block.useWithoutItem(level.getBlockState(selected), level, selected, player, hit).consumesAction(), "Door interaction succeeds");
                        currentOrigin = block.toggledOrigin(currentOrigin, before);
                        selected = currentOrigin.relative(block.widthDirection(before.cycle(FacilityDoorBlock.OPEN)), selectedX).above(selectedY);
                        for (int x = 0; x < block.width; x++) for (int y = 0; y < block.height; y++) {
                            BlockPos partPos = currentOrigin.relative(block.widthDirection(before.cycle(FacilityDoorBlock.OPEN)), x).above(y);
                            var part = level.getBlockState(partPos);
                            helper.assertTrue(part.getValue(FacilityDoorBlock.OPEN) == open, "All parts share open state");
                            var corridor = facing.getAxis() == Direction.Axis.Z
                                    ? net.minecraft.world.level.block.Block.box(7, 0, 0, 9, 16, 16)
                                    : net.minecraft.world.level.block.Block.box(0, 0, 7, 16, 16, 9);
                            boolean blocked = net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                                    part.getCollisionShape(level, partPos), corridor, net.minecraft.world.phys.shapes.BooleanOp.AND);
                            if (block.width == 1 || !open) helper.assertTrue(blocked != open, "Opening clears the walk-through corridor");
                            if (block.width == 2 && open) helper.assertTrue(level.isEmptyBlock(origin.above(y)), "Single wide leaf vacates the original entrance");
                            var restored = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), net.minecraft.nbt.NbtUtils.writeBlockState(part));
                            helper.assertTrue(restored.equals(part), "Open state persists in saved palette");
                        }
                    }
                    BlockState before = level.getBlockState(selected);
                    block.useWithoutItem(before, level, selected, player, hit);
                    currentOrigin = block.toggledOrigin(currentOrigin, before);
                    selected = currentOrigin.relative(block.widthDirection(before.cycle(FacilityDoorBlock.OPEN)), selectedX).above(selectedY);
                    breakAndCount(helper, selected, currentOrigin, block.asItem(), GameType.SURVIVAL);
                }
            }
        }
        helper.succeed();
    }

    // Verifica transferencias y permisos del inventario.
    @GameTest(template = "empty")
    public static void binMenuTransfersAndRejectsUnauthorizedPlayers(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(START);
        level.setBlockAndUpdate(pos, PostedSignage.TRASH_BIN_BLOCK.get().defaultBlockState());
        var bin = (TrashBinBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        var menu = new TrashBinMenu(4, player.getInventory(), bin);
        player.containerMenu = menu;
        player.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.EMERALD, 5));
        helper.assertTrue(menu.quickMoveStack(player, 36).getCount() == 5
                && bin.getItem(0).getCount() == 5 && player.getInventory().getItem(0).isEmpty(), "Shift-click stores player items");
        helper.assertTrue(menu.quickMoveStack(player, 0).getCount() == 5 && bin.isEmpty()
                && player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 5, "Shift-click recovers bin items without loss or duplication");
        bin.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
        var visitor = helper.makeMockPlayer(GameType.ADVENTURE);
        // The mock overrides game-mode queries but does not initialize ability flags.
        GameType.ADVENTURE.updatePlayerAbilities(visitor.getAbilities());
        visitor.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        var deniedMenu = new TrashBinMenu(5, visitor.getInventory(), bin);
        visitor.containerMenu = deniedMenu;
        visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STONE, 4));
        helper.assertTrue(!deniedMenu.clickMenuButton(visitor, 0) && !bin.isEmpty(), "Adventure cannot empty bin");
        helper.assertTrue(bin.storeHeldItems(visitor, InteractionHand.MAIN_HAND) == 0
                && visitor.getMainHandItem().getCount() == 4, "Adventure cannot deposit");
        var other = helper.makeMockPlayer(GameType.SURVIVAL);
        other.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        helper.assertTrue(!menu.clickMenuButton(other, 0) && !bin.isEmpty(), "Another player's menu cannot be reused");
        player.containerMenu = player.inventoryMenu;
        visitor.containerMenu = visitor.inventoryMenu;
        helper.succeed();
    }

    // Verifica vaciado y recuperación al romper el basurero.
    @GameTest(template = "empty")
    public static void binMenuEmptiesOnlyItsStorageAndReturnsContentsOnBreak(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(START);
        var block = PostedSignage.TRASH_BIN_BLOCK.get();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        helper.assertTrue(level.isEmptyBlock(pos.above()), "Bin occupies only one block");
        var bin = (TrashBinBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2);
        bin.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
        var menu = bin.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu != null && menu.slots.size() == 45, "Nine storage slots and player inventory available");
        menu.removed(player);
        helper.assertTrue(bin.getItem(0).getCount() == 7, "Closing storage never destroys items");
        var restored = new TrashBinBlockEntity(pos, bin.getBlockState());
        restored.loadWithComponents(bin.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        helper.assertTrue(restored.getItem(0).getCount() == 7, "Stored items survive reload");
        helper.assertTrue(menu instanceof TrashBinMenu, "Bin uses dedicated menu");
        helper.assertTrue(!menu.clickMenuButton(player, 0) && !bin.isEmpty(), "Closed menu cannot empty bin");
        player.containerMenu = menu;
        helper.assertTrue(!menu.clickMenuButton(player, 99) && !bin.isEmpty(), "Unknown button cannot empty bin");
        player.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        helper.assertTrue(!menu.clickMenuButton(player, 0) && !bin.isEmpty(), "Distant button request rejected");
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.EMERALD, 5));
        menu.setCarried(new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 2));
        helper.assertTrue(menu.clickMenuButton(player, 0) && bin.isEmpty(), "Empty button deletes stored contents in one click");
        helper.assertTrue(player.getMainHandItem().getCount() == 5 && menu.getCarried().getCount() == 2, "Disposal preserves player inventory and cursor");
        helper.assertTrue(!menu.clickMenuButton(player, 0), "Empty bin button is harmless");
        menu.setCarried(ItemStack.EMPTY);
        player.containerMenu = player.inventoryMenu;
        bin.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
        breakAndCount(helper, pos, pos, block.asItem(), GameType.SURVIVAL);
        int diamonds = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2), e -> e.getItem().is(net.minecraft.world.item.Items.DIAMOND))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(diamonds == 7, "Breaking bin returns all undeleted contents");
        helper.succeed();
    }

    // Verifica la limpieza selectiva de piezas antiguas.
    @GameTest(template = "empty")
    public static void legacyFacilityCellsMigrateWithoutDeletingRetainedParts(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(START);
        var shortDoor = PostedSignage.SHORT_GLASS_DOOR_BLOCK.get();
        var oldName = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID, "glass_door");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(oldName) == shortDoor, "Retired block ID aliases short door");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(oldName) == shortDoor.asItem(), "Retired inventory item aliases short door");
        for (FacilityMultiBlock block : new FacilityMultiBlock[] {PostedSignage.TRASH_BIN_BLOCK.get(), shortDoor}) {
            var state = block.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, null, ItemStack.EMPTY);
            BlockPos obsolete = pos.above(block.height);
            level.setBlock(obsolete, state.setValue(FacilityMultiBlock.PART_Y, block.height), 18);
            FacilityMigration.removeObsoleteCell(level, obsolete);
            helper.assertTrue(level.isEmptyBlock(obsolete), "Obsolete upper cell removed");
            helper.assertTrue(level.getBlockState(pos).is(block), "Retained origin preserved");
            block.playerWillDestroy(level, pos, state, helper.makeMockPlayer(GameType.CREATIVE));
            level.removeBlock(pos, false);
        }
        helper.succeed();
    }

    // Verifica objetos recuperados según el modo de juego.
    @GameTest(template = "empty")
    public static void everyFacilityPartDropsExactlyOneInSurvivalAndNoneInCreative(GameTestHelper helper) {
        var blocks = new FacilityMultiBlock[] { PostedSignage.TRASH_BIN_BLOCK.get(),
                PostedSignage.WIDE_GLASS_DOOR_BLOCK.get(),
                PostedSignage.SHORT_GLASS_DOOR_BLOCK.get() };
        int[] widths = {1, 2, 1};
        int[] heights = {1, 3, 2};
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(START);
        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (int i = 0; i < blocks.length; i++) {
                    var block = blocks[i];
                    for (int x = 0; x < widths[i]; x++) for (int y = 0; y < heights[i]; y++) {
                        BlockState state = block.defaultBlockState().setValue(FacilityMultiBlock.FACING, facing);
                        level.setBlockAndUpdate(origin, state);
                        block.setPlacedBy(level, origin, state, null, ItemStack.EMPTY);
                        BlockPos selected = origin.relative(facing.getClockWise(), x).above(y);
                        breakAndCount(helper, selected, origin, block.asItem(), mode);
                        for (int px = 0; px < widths[i]; px++) for (int py = 0; py < heights[i]; py++) {
                            helper.assertTrue(level.getBlockState(origin.relative(facing.getClockWise(), px).above(py)).isAir(),
                                    "All facility parts removed: " + mode + " " + facing + " " + x + "," + y);
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    // Verifica un único mural recuperado en Supervivencia.
    @GameTest(template = "empty")
    public static void everyMuralPartDropsExactlyOneInSurvivalAndNoneInCreative(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = PostedSignage.STATUE_RELIEF_BLOCK.get();
        BlockPos origin = helper.absolutePos(new BlockPos(6, 2, 6));
        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (StatuePart selected : StatuePart.values()) {
                    // Assemble atomically so the test can focus on the real player-break path.
                    for (StatuePart part : StatuePart.values()) {
                        BlockPos pos = origin.relative(facing.getCounterClockWise(), part.column()).above(part.row());
                        level.setBlock(pos.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 18);
                        level.setBlock(pos, block.defaultBlockState().setValue(StatueReliefBlock.FACING, facing)
                                .setValue(StatueReliefBlock.PART, part), 18);
                    }
                    BlockPos selectedPos = origin.relative(facing.getCounterClockWise(), selected.column()).above(selected.row());
                    breakAndCount(helper, selectedPos, origin, block.asItem(), mode);
                    for (StatuePart part : StatuePart.values()) {
                        BlockPos pos = origin.relative(facing.getCounterClockWise(), part.column()).above(part.row());
                        helper.assertTrue(level.getBlockState(pos).isAir(), "All mural parts removed: " + mode + " " + selected);
                        level.setBlock(pos.relative(facing.getOpposite()), Blocks.AIR.defaultBlockState(), 18);
                    }
                }
            }
        }
        helper.succeed();
    }

    // Rompe una pieza y cuenta los objetos generados.
    private static void breakAndCount(GameTestHelper helper, BlockPos selected, BlockPos origin,
            net.minecraft.world.item.Item item, GameType mode) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var existingDrops = new java.util.HashSet<>(level.getEntitiesOfClass(ItemEntity.class,
                new AABB(origin).inflate(12)));
        helper.assertTrue(player.gameMode.destroyBlock(selected), "Server player successfully breaks selected part");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(12),
                entity -> entity.getItem().is(item) && !existingDrops.contains(entity));
        int count = drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(count == (mode == GameType.CREATIVE ? 0 : 1),
                "Exact drop count for " + item + " in " + mode + ": " + count);
        drops.forEach(ItemEntity::discard);
    }

    // Verifica montaje y retirada de cada estructura.
    @GameTest(template = "empty")
    public static void assemblesAndRemovesEveryFacility(GameTestHelper helper) {
        verifyLifecycle(helper, PostedSignage.TRASH_BIN_BLOCK.get(), 1, 1, START);
        verifyLifecycle(helper, PostedSignage.WIDE_GLASS_DOOR_BLOCK.get(), 2, 3, START.offset(4, 0, 0));
        verifyLifecycle(helper, PostedSignage.SHORT_GLASS_DOOR_BLOCK.get(), 1, 2, START.offset(7, 0, 0));
        helper.succeed();
    }

    /** Exercise the real BlockItem path so origin survival checks cannot silently disable placement. */
    @GameTest(template = "empty")
    public static void itemsPlaceEveryFacility(GameTestHelper helper) {
        verifyItemPlacement(helper, PostedSignage.TRASH_BIN.get().getDefaultInstance(),
                PostedSignage.TRASH_BIN_BLOCK.get(), 1, 1, START);
        helper.succeed();
    }

    // Comprueba la colocación usando el objeto real.
    private static void verifyItemPlacement(
            GameTestHelper helper,
            ItemStack stack,
            FacilityMultiBlock block,
            int width,
            int height,
            BlockPos relativeOrigin) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(relativeOrigin);
        BlockPos floor = origin.below();
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                floor.getCenter().add(0, 0.5, 0), Direction.UP, floor, false);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        BlockState preview = block.getStateForPlacement(context);
        helper.assertTrue(preview != null, "Facility has enough room for placement");
        helper.assertTrue(preview.canSurvive(level, origin), "Facility origin can enter the world");
        helper.assertTrue(stack.useOn(context).consumesAction(), "Facility BlockItem accepts placement");

        Direction widthDirection = preview.getValue(FacilityMultiBlock.FACING).getClockWise();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockState part = level.getBlockState(origin.relative(widthDirection, x).above(y));
                helper.assertTrue(part.is(block), "BlockItem creates facility part " + x + "," + y);
            }
        }

        // Reuse an isolated site for the next item so player-facing direction cannot
        // make one successfully placed facility obstruct another test case.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                level.setBlock(origin.relative(widthDirection, x).above(y), Blocks.AIR.defaultBlockState(),
                        FacilityMultiBlock.UPDATE_ALL);
            }
        }
    }

    // Comprueba montaje, soporte y retirada del conjunto.
    private static void verifyLifecycle(
            GameTestHelper helper,
            FacilityMultiBlock block,
            int width,
            int height,
            BlockPos relativeOrigin) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(relativeOrigin);
        BlockState state = block.defaultBlockState().setValue(FacilityMultiBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(origin, state);
        block.setPlacedBy(level, origin, state, null, ItemStack.EMPTY);

        Direction widthDirection = Direction.NORTH.getClockWise();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos partPos = origin.relative(widthDirection, x).above(y);
                BlockState part = level.getBlockState(partPos);
                helper.assertTrue(part.is(block), "Facility part exists at " + x + "," + y);
                helper.assertTrue(part.getValue(FacilityMultiBlock.PART_X) == x, "Facility part_x is correct");
                helper.assertTrue(part.getValue(FacilityMultiBlock.PART_Y) == y, "Facility part_y is correct");
                helper.assertTrue(part.getValue(FacilityMultiBlock.FACING) == Direction.NORTH, "Facility facing is preserved");
            }
        }

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos removedPart = origin.relative(widthDirection, width - 1).above(height - 1);
        BlockState removedState = level.getBlockState(removedPart);
        block.playerWillDestroy(level, removedPart, removedState, player);
        level.setBlock(removedPart, Blocks.AIR.defaultBlockState(), FacilityMultiBlock.UPDATE_ALL);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos partPos = origin.relative(widthDirection, x).above(y);
                helper.assertTrue(level.getBlockState(partPos).is(Blocks.AIR), "Breaking one part removes the whole facility");
            }
        }
    }
}
