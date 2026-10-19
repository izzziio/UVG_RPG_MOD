package com.postedsignage;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A double-sided projecting sign attached to a vertical wall by its short edge.
 * FACING points away from the supporting wall and controls the projection axis.
 */
public final class HangingSignBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<HangingSignBlock> CODEC = simpleCodec(HangingSignBlock::new);

    // Overall bounds include the panel, raised frame, wall plate, and supports.
    private static final VoxelShape NORTH_SHAPE = Block.box(6.25, 2.5, 5.25, 9.75, 13.5, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(6.25, 2.5, 0.0, 9.75, 13.5, 10.75);
    private static final VoxelShape WEST_SHAPE = Block.box(5.25, 2.5, 6.25, 16.0, 13.5, 9.75);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 2.5, 6.25, 10.75, 13.5, 9.75);

    // Configura las propiedades iniciales del bloque.
    public HangingSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GlassSignMount.GLASS_MOUNTED, false));
    }

    /** Minecraft uses this codec when reconstructing the block from registry data. */
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    // Calcula la orientación y comprueba la colocación.
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // Match vanilla wall-mounted blocks: try the player's nearest horizontal
        // directions and select the first orientation with a sturdy wall behind it.
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                state = state.setValue(FACING, direction.getOpposite());
                if (state.canSurvive(level, pos)) {
                    return GlassSignMount.state(state,level,pos,state.getValue(FACING));
                }
            }
        }

        return null;
    }

    /** Check the sturdy wall face behind the outward-facing block. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return GlassSignMount.supported(level,pos,facing);
    }

    /** Recheck support after neighbor changes and remove the block when its attachment is lost. */
    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return GlassSignMount.state(super.updateShape(state, direction, neighborState, level, pos, neighborPos),level,pos,state.getValue(FACING));
    }

    /** Return bounds matching the placed orientation for outline selection and default collision handling. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
        return GlassSignMount.shape(shape,state,state.getValue(FACING));
    }

    /** Rotate the stored facing so structure rotations preserve the attachment orientation. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Convert mirroring into the appropriate facing rotation. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    /** Declare the properties Minecraft serializes and uses to select blockstate model variants. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GlassSignMount.GLASS_MOUNTED);
    }
}
