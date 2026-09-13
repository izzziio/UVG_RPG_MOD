package com.postedsignage;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wall-mounted projecting sign based on the real CIT room signs. Its fixed
 * color and CIT mark come from the block model texture; both broad faces retain
 * independently editable vanilla sign text.
 */
public final class CustomizableHangingSignBlock extends SignBlock {
    public static final MapCodec<CustomizableHangingSignBlock> CODEC =
            simpleCodec(CustomizableHangingSignBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH_SHAPE = Block.box(7.15, 4.5, 3.75, 8.85, 11.5, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(7.15, 4.5, 0.0, 8.85, 11.5, 12.25);
    private static final VoxelShape WEST_SHAPE = Block.box(3.75, 4.5, 7.15, 16.0, 11.5, 8.85);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 4.5, 7.15, 12.25, 11.5, 8.85);

    public CustomizableHangingSignBlock(Properties properties) {
        super(WoodType.OAK, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends SignBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean waterlogged = level.getFluidState(pos).getType() == Fluids.WATER;

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                BlockState state = defaultBlockState()
                        .setValue(FACING, direction.getOpposite())
                        .setValue(WATERLOGGED, waterlogged);
                if (state.canSurvive(level, pos)) {
                    return state;
                }
            }
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // BaseEntityBlock defaults to invisible because vanilla signs render
        // their boards in a block-entity renderer. Our renderer draws only the
        // editable text, so the fixed CIT panel must use its JSON block model.
        return RenderShape.MODEL;
    }

    @Override
    public float getYRotationDegrees(BlockState state) {
        return state.getValue(FACING).getClockWise().toYRot();
    }

    @Override
    public Vec3 getSignHitboxCenterPosition(BlockState state) {
        Direction facing = state.getValue(FACING);
        return Vec3.atCenterOf(BlockPos.ZERO).add(
                facing.getStepX() * 0.12,
                0.0,
                facing.getStepZ() * 0.12);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CustomizableHangingSignBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(
                type,
                PostedSignage.CUSTOMIZABLE_HANGING_SIGN_BLOCK_ENTITY.get(),
                SignBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }
}
