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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
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
    // Abre la edición solo para operadores en Creativo.
    @Override public void openTextEdit(net.minecraft.world.entity.player.Player player,
            SignBlockEntity sign, boolean front) {
        if (CitAccess.canEditAt(player, sign.getBlockPos())) super.openTextEdit(player, sign, front);
    }
    // Impide editar el texto sin permisos.
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!CitAccess.canEditAt(player, pos)) return net.minecraft.world.InteractionResult.SUCCESS;
        return super.useWithoutItem(state, level, pos, player, hit);
    }
    // Impide tintes, brillo y cera sin permisos.
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!CitAccess.canEditAt(player, pos)) return net.minecraft.world.ItemInteractionResult.SUCCESS;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
    // Programa la apertura del editor tras colocar el letrero.
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof net.minecraft.server.level.ServerPlayer player) {
            player.server.tell(new net.minecraft.server.TickTask(player.server.getTickCount() + 1, () -> {
                if (level.getBlockEntity(pos) instanceof CustomizableHangingSignBlockEntity sign)
                    openTextEdit(player, sign, true);
            }));
        }
    }
    public static final MapCodec<CustomizableHangingSignBlock> CODEC =
            simpleCodec(CustomizableHangingSignBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH_SHAPE = Block.box(7.15, 4.5, 3.75, 8.85, 11.5, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(7.15, 4.5, 0.0, 8.85, 11.5, 12.25);
    private static final VoxelShape WEST_SHAPE = Block.box(3.75, 4.5, 7.15, 16.0, 11.5, 8.85);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 4.5, 7.15, 12.25, 11.5, 8.85);

    // Configura las propiedades iniciales del bloque.
    public CustomizableHangingSignBlock(Properties properties) {
        super(WoodType.OAK, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(GlassSignMount.GLASS_MOUNTED, false)
                .setValue(WATERLOGGED, false));
    }

    /** Minecraft uses this codec when reconstructing the block from registry data. */
    @Override
    protected MapCodec<? extends SignBlock> codec() {
        return CODEC;
    }

    /** Preserve waterlogging and try the player's nearest horizontal wall directions. */
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
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
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

    // Dibuja el panel JSON además del texto dinámico.
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // BaseEntityBlock defaults to invisible because vanilla signs render
        // their boards in a block-entity renderer. Our renderer draws only the
        // editable text, so the fixed CIT panel must use its JSON block model.
        return RenderShape.MODEL;
    }

    /** The sign projects perpendicular to the wall, so text orientation is offset by a quarter turn. */
    @Override
    public float getYRotationDegrees(BlockState state) {
        return state.getValue(FACING).getClockWise().toYRot();
    }

    /** Offset the interaction center toward the projecting board for vanilla front/back editing. */
    @Override
    public Vec3 getSignHitboxCenterPosition(BlockState state) {
        Direction facing = state.getValue(FACING);
        return Vec3.atCenterOf(BlockPos.ZERO).add(
                facing.getStepX() * 0.12,
                0.0,
                facing.getStepZ() * 0.12).add(GlassSignMount.offset(state,facing));
    }

    // Crea los datos asociados al bloque colocado.
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CustomizableHangingSignBlockEntity(pos, state);
    }

    /** Reuse vanilla sign ticking to manage the editor session for this custom block-entity type. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(
                type,
                PostedSignage.CUSTOMIZABLE_HANGING_SIGN_BLOCK_ENTITY.get(),
                SignBlockEntity::tick);
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
        builder.add(FACING, WATERLOGGED, GlassSignMount.GLASS_MOUNTED);
    }
}
