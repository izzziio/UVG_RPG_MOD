package com.postedsignage;

import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A flat wall mural occupying a ten-wide by four-high T-shaped grid. */
public final class StatueReliefBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<StatueReliefBlock> CODEC = simpleCodec(StatueReliefBlock::new);
    public static final EnumProperty<StatuePart> PART = EnumProperty.create("part", StatuePart.class);

    private static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 15.5, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 0.5);
    private static final VoxelShape WEST_SHAPE = Block.box(15.5, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 0.0, 0.0, 0.5, 16.0, 16.0);

    // Configura las propiedades iniciales del bloque.
    public StatueReliefBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, StatuePart.STEM_BOTTOM_0));
    }

    /** Minecraft uses this codec when reconstructing the block from registry data. */
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    /** Use the clicked cell as the lower-left stem anchor and validate the entire mural footprint first. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        if (!facing.getAxis().isHorizontal()) {
            return null;
        }

        BlockPos anchor = context.getClickedPos();
        if (!canPlaceStructure(context, anchor, facing)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, StatuePart.STEM_BOTTOM_0);
    }

    /** Every occupied cell must be replaceable, unobstructed, inside the border and supported by the wall. */
    private boolean canPlaceStructure(BlockPlaceContext context, BlockPos anchor, Direction facing) {
        Level level = context.getLevel();
        CollisionContext collisionContext = context.getPlayer() == null
                ? CollisionContext.empty()
                : CollisionContext.of(context.getPlayer());
        for (StatuePart part : StatuePart.values()) {
            BlockPos partPos = partPosition(anchor, facing, part);
            if (!level.getWorldBorder().isWithinBounds(partPos)) {
                return false;
            }
            if (!partPos.equals(anchor) && !level.getBlockState(partPos).canBeReplaced()) {
                return false;
            }
            BlockState partState = defaultBlockState().setValue(FACING, facing).setValue(PART, part);
            if (!level.isUnobstructed(partState, partPos, collisionContext)) {
                return false;
            }
            BlockPos supportPos = partPos.relative(facing.getOpposite());
            if (!level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing)) {
                return false;
            }
        }
        return true;
    }

    /** Assemble the remaining 27 parts on the server after the anchor has been placed. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }

        Direction facing = state.getValue(FACING);
        for (StatuePart part : StatuePart.values()) {
            if (part == StatuePart.STEM_BOTTOM_0) {
                continue;
            }
            BlockPos partPos = partPosition(pos, facing, part);
            level.setBlock(partPos, state.setValue(PART, part), 3);
        }
        level.blockUpdated(pos, Blocks.AIR);
    }

    // Calcula la posición de una pieza del mural.
    private static BlockPos partPosition(BlockPos anchor, Direction facing, StatuePart part) {
        // Increasing columns move toward the viewer's right while looking at
        // the outward-facing relief. The viewer looks opposite FACING.
        return anchor.relative(facing.getCounterClockWise(), part.column()).above(part.row());
    }

    /** Check the sturdy wall face behind the outward-facing block. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    /** Missing wall support or an expected adjacent part removes this cell and propagates structural collapse. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing.getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        StatuePart expectedPart = expectedNeighbor(state.getValue(PART), facing, direction);
        if (expectedPart != null && (!neighborState.is(this)
                || neighborState.getValue(FACING) != facing
                || neighborState.getValue(PART) != expectedPart)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // Retira el mural completo con un único objeto recuperable.
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            Direction facing = state.getValue(FACING);
            StatuePart selected = state.getValue(PART);
            BlockPos anchor = pos.relative(facing.getCounterClockWise(), -selected.column()).below(selected.row());
            var parts = new LinkedHashMap<BlockPos, BlockState>();
            for (StatuePart part : StatuePart.values()) {
                BlockPos partPos = partPosition(anchor, facing, part);
                BlockState expected = state.setValue(PART, part);
                if (level.getBlockState(partPos).equals(expected)) parts.put(partPos, expected);
            }
            MultiblockRemoval.beforePlayerBreak(level, pos, anchor, parts, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Translate a world-space neighbor direction into the mural's local row/column grid. */
    @Nullable
    private static StatuePart expectedNeighbor(StatuePart part, Direction facing, Direction direction) {
        int column = part.column();
        int row = part.row();
        if (direction == Direction.UP) {
            return StatuePart.findAt(column, row + 1);
        }
        if (direction == Direction.DOWN) {
            return StatuePart.findAt(column, row - 1);
        }

        Direction right = facing.getCounterClockWise();
        if (direction == right) {
            return StatuePart.findAt(column + 1, row);
        }
        if (direction == right.getOpposite()) {
            return StatuePart.findAt(column - 1, row);
        }
        return null;
    }

    /** Return bounds matching the placed orientation for outline selection and default collision handling. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    /** Rotate the stored facing so structure rotations preserve the attachment orientation. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirror both wall orientation and the local part index so the T-shaped layout stays coherent. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)))
                .setValue(PART, state.getValue(PART).mirrored());
    }

    /** Declare the properties Minecraft serializes and uses to select blockstate model variants. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
}
