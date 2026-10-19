package com.postedsignage;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

// Prueba losas y herramientas de textura en un mundo real.
@GameTestHolder(PostedSignage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildingToolsGameTests {
    private static final BlockPos CENTER = new BlockPos(3,2,3);

    // Construye el contexto de colocación de la prueba.
    private static UseOnContext context(Player player, BlockPos pos, Direction face) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, pos, false));
    }

    // Comprueba orientación y límites de las losas.
    @GameTest(template="empty") public static void slabPlacementAndBounds(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var pos = helper.absolutePos(CENTER);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        for (var item : new net.minecraft.world.item.Item[]{PostedSignage.BRICK_VERTICAL_SLAB.get(), PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB.get()}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            var block = ((net.minecraft.world.item.BlockItem)item).getBlock();
            for (Direction face : Direction.Plane.HORIZONTAL) {
                var placement = new BlockPlaceContext(context(player, pos, face));
                var state = block.getStateForPlacement(placement);
                helper.assertTrue(state.getValue(VerticalSlabBlock.FACING) == face.getOpposite(), "Slab hugs clicked face");
                var shape = state.getShape(helper.getLevel(), placement.getClickedPos());
                var bounds = shape.bounds();
                helper.assertTrue(Math.abs(bounds.getXsize()*bounds.getYsize()*bounds.getZsize()-0.5) < 0.0001, "Slab occupies half a block");
                helper.assertTrue(bounds.getYsize() == 1, "Vertical slabs are full height");
                helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(VerticalSlabBlock.FACING) == face.getOpposite().getClockWise(), "Rotation follows collision geometry");
            }
        }
        helper.succeed();
    }

    // Verifica las seis caras y la pérdida de soporte.
    @GameTest(template="empty") public static void coversAllSixFacesAndLoseSupport(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        for (Direction face : Direction.values()) {
            var dest = pos.relative(face);
            BlockState cover = PostedSignage.TEXTURE_COVER_BLOCK.get().defaultBlockState().setValue(TextureCoverBlock.FACING,face);
            helper.assertTrue(cover.canSurvive(level,dest), "Solid support accepted on " + face);
            level.setBlockAndUpdate(dest,cover);
            helper.assertTrue(cover.getCollisionShape(level,dest).isEmpty(), "Cover must not change movement collision");
        }
        level.destroyBlock(pos,false);
        for (Direction face : Direction.values()) helper.assertTrue(level.isEmptyBlock(pos.relative(face)), "Unsupported cover removed on " + face);
        helper.succeed();
    }

    // Verifica muestreo, repintado y guardado de texturas.
    @GameTest(template="empty") public static void sampleApplyRepaintAndPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos, Blocks.BRICKS.defaultBlockState());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
        player.setShiftKeyDown(true);
        helper.assertTrue(tool.useOn(context(player,pos,Direction.UP)).consumesAction(), "Sampling succeeds");
        helper.assertTrue(player.getMainHandItem().has(DataComponents.CUSTOM_DATA), "Selection stored on tool");
        // Copying a stack preserves selection, as does item component serialization.
        player.setItemInHand(InteractionHand.MAIN_HAND,player.getMainHandItem().copy());
        player.setShiftKeyDown(false);
        level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        helper.assertTrue(tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(), "Applying succeeds");
        var coverPos = pos.north();
        var cover = (TextureCoverBlockEntity) level.getBlockEntity(coverPos);
        helper.assertTrue(cover != null && cover.sample().is(Blocks.BRICKS) && cover.sampleFace() == Direction.UP, "Correct selected material and face applied");
        helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Original block preserved");
        var restored = new TextureCoverBlockEntity(coverPos,cover.getBlockState());
        restored.loadWithComponents(cover.saveWithFullMetadata(level.registryAccess()),level.registryAccess());
        helper.assertTrue(restored.sample().equals(cover.sample()) && restored.sampleFace() == Direction.UP, "Disk persistence round trip");
        restored.loadWithComponents(cover.getUpdateTag(level.registryAccess()),level.registryAccess());
        helper.assertTrue(restored.sample().is(Blocks.BRICKS), "Chunk synchronization includes selected texture");
        helper.assertTrue(cover.getUpdatePacket() != null, "Repaint sends block entity update packet");
        player.setShiftKeyDown(true);
        tool.useOn(context(player,pos,Direction.EAST));
        player.setShiftKeyDown(false);
        tool.useOn(context(player,coverPos,Direction.NORTH));
        helper.assertTrue(cover.sample().is(Blocks.STONE) && cover.sampleFace() == Direction.EAST, "Existing cover repainted in place");
        level.destroyBlock(coverPos,true);
        helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "Removing cover restores original face");
        helper.succeed();
    }

    // Rechaza aplicaciones inválidas o sin permiso.
    @GameTest(template="empty") public static void rejectsMissingSelectionOccupiedSpaceAndAdventure(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
        helper.assertTrue(!tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(), "Empty applicator cannot apply");
        player.setShiftKeyDown(true);
        tool.useOn(context(player,pos,Direction.UP));
        player.setShiftKeyDown(false);
        level.setBlockAndUpdate(pos.north(),Blocks.TORCH.defaultBlockState());
        helper.assertTrue(!tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(), "Cannot overwrite existing blocks");
        helper.assertTrue(level.getBlockState(pos.north()).is(Blocks.TORCH), "Existing block preserved");
        var adventure = helper.makeMockPlayer(GameType.ADVENTURE);
        // GameTest's mock only overrides isCreative/isSpectator, not the ability flags.
        GameType.ADVENTURE.updatePlayerAbilities(adventure.getAbilities());
        adventure.setItemInHand(InteractionHand.MAIN_HAND,player.getMainHandItem().copy());
        helper.assertTrue(!tool.useOn(context(adventure,pos,Direction.SOUTH)).consumesAction(), "Adventure cannot paint");
        helper.succeed();
    }

    // Verifica orientación y persistencia de la textura.
    @GameTest(template="empty") public static void textureOrientationFollowsPlayerAndSurvivesReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        player.setShiftKeyDown(true);
        tool.useOn(context(player, pos, Direction.NORTH));
        player.setShiftKeyDown(false);
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        int[] floorRotations = {2, 3, 0, 1};
        int[] ceilingRotations = {0, 3, 2, 1};
        for (Direction surface : Direction.values()) {
            var destination = pos.relative(surface);
            for (int i = 0; i < sides.length; i++) {
                Direction side = sides[i];
                player.setPos(destination.getX() + 0.5 + side.getStepX() * 3,
                        destination.getY() + 1, destination.getZ() + 0.5 + side.getStepZ() * 3);
                // Position, not a stale viewing direction, determines the nearest edge.
                player.setYRot(Direction.NORTH.toYRot());
                int expected = surface == Direction.UP ? floorRotations[i]
                        : surface == Direction.DOWN ? ceilingRotations[i] : 0;
                helper.assertTrue(tool.useOn(context(player, pos, surface)).consumesAction(), "Apply on " + surface);
                var cover = (TextureCoverBlockEntity) level.getBlockEntity(destination);
                helper.assertTrue(cover != null && cover.rotation() == expected,
                        "Rotation on " + surface + " from " + side + " should be " + expected);
                helper.assertTrue(cover.sample().is(Blocks.FURNACE) && cover.sampleFace() == Direction.NORTH,
                        "Rotation preserves the selected source face");
                var restored = new TextureCoverBlockEntity(destination, cover.getBlockState());
                restored.loadWithComponents(cover.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                helper.assertTrue(restored.rotation() == expected, "Rotation survives disk reload");
                restored.loadWithComponents(cover.getUpdateTag(level.registryAccess()), level.registryAccess());
                helper.assertTrue(restored.rotation() == expected, "Rotation synchronized to clients");
                var legacy = cover.getUpdateTag(level.registryAccess());
                legacy.remove("Rotation");
                restored.loadWithComponents(legacy, level.registryAccess());
                helper.assertTrue(restored.rotation() == 0, "Old saves retain the unrotated layout");

                Direction opposite = side.getOpposite();
                player.setPos(destination.getX() + 0.5 + opposite.getStepX() * 3,
                        destination.getY() + 1, destination.getZ() + 0.5 + opposite.getStepZ() * 3);
                // Hitting a thin edge must still use the cover's attached surface.
                helper.assertTrue(tool.useOn(context(player, destination, Direction.EAST)).consumesAction(), "Repaint succeeds");
                helper.assertTrue(cover.rotation() == (surface.getAxis() == Direction.Axis.Y ? (expected + 2) % 4 : 0),
                        "Repaint updates rotation using the existing surface");
                helper.assertTrue(cover.getUpdatePacket() != null, "Repaint includes a synchronization packet");
                level.destroyBlock(destination, false);
            }
        }
        for (Direction surface : new Direction[] {Direction.UP, Direction.DOWN}) {
            var destination = pos.relative(surface);
            player.setPos(destination.getX() + 0.5, destination.getY() + 2, destination.getZ() + 0.5);
            for (int i = 0; i < sides.length; i++) {
                player.setYRot(sides[i].getOpposite().toYRot());
                helper.assertTrue(TextureApplicatorItem.placementRotation(surface, destination, player)
                        == (surface == Direction.UP ? floorRotations[i] : ceilingRotations[i]),
                        "Centered placement uses player heading");
            }
        }
        helper.succeed();
    }
}
