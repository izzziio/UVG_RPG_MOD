package com.postedsignage;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Jardinera que se une exactamente a una jardinera vecina, de forma similar a un cofre. */
public final class ConnectingPlanterBlock extends Block {
    public static final MapCodec<ConnectingPlanterBlock> CODEC = simpleCodec(ConnectingPlanterBlock::new);
    public static final EnumProperty<PlanterConnection> CONNECTION =
            EnumProperty.create("connection", PlanterConnection.class);

    private static final VoxelShape SINGLE_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 6.0, 14.0);
    private static final VoxelShape NORTH_SHAPE = Block.box(2.0, 0.0, 0.0, 14.0, 6.0, 14.0);
    private static final VoxelShape EAST_SHAPE = Block.box(2.0, 0.0, 2.0, 16.0, 6.0, 14.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 6.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0, 0.0, 2.0, 14.0, 6.0, 14.0);
    private static final VoxelShape SINGLE_COLLISION = Block.box(2.0, 0.0, 2.0, 14.0, 7.0, 14.0);
    private static final VoxelShape NORTH_COLLISION = Block.box(2.0, 0.0, 0.0, 14.0, 7.0, 14.0);
    private static final VoxelShape EAST_COLLISION = Block.box(2.0, 0.0, 2.0, 16.0, 7.0, 14.0);
    private static final VoxelShape SOUTH_COLLISION = Block.box(2.0, 0.0, 2.0, 14.0, 7.0, 16.0);
    private static final VoxelShape WEST_COLLISION = Block.box(0.0, 0.0, 2.0, 14.0, 7.0, 14.0);

    // Configura las propiedades iniciales del bloque.
    public ConnectingPlanterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CONNECTION, PlanterConnection.SINGLE));
    }

    /** Minecraft reconstruye el bloque mediante este códec. */
    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Elige una vecina sin pareja en un orden estable; los pares existentes no aceptan una tercera jardinera. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction forward = context.getHorizontalDirection();
        Direction[] preferredDirections = {
            forward.getOpposite(),
            forward.getClockWise(),
            forward.getCounterClockWise(),
            forward
        };
        for (Direction direction : preferredDirections) {
            BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            if (neighbor.is(this) && neighbor.getValue(CONNECTION) == PlanterConnection.SINGLE) {
                return defaultBlockState().setValue(CONNECTION, PlanterConnection.fromDirection(direction));
            }
        }
        return defaultBlockState();
    }

    /** Completa la conexión recíproca o restaura SINGLE si deja de ser válida. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }

        Direction connectionDirection = state.getValue(CONNECTION).direction();
        if (connectionDirection == null) {
            return;
        }
        BlockPos neighborPos = pos.relative(connectionDirection);
        BlockState neighbor = level.getBlockState(neighborPos);
        if (neighbor.is(this) && neighbor.getValue(CONNECTION) == PlanterConnection.SINGLE) {
            level.setBlock(neighborPos, neighbor.setValue(
                    CONNECTION, PlanterConnection.fromDirection(connectionDirection.getOpposite())), 3);
        }
        else {
            level.setBlock(pos, state.setValue(CONNECTION, PlanterConnection.SINGLE), 3);
        }
    }

    /** La conexión exige que la otra maceta apunte hacia esta. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction connectionDirection = state.getValue(CONNECTION).direction();
        if (connectionDirection == direction) {
            boolean reciprocalConnection = neighborState.is(this)
                    && neighborState.getValue(CONNECTION).direction() == direction.getOpposite();
            if (!reciprocalConnection) {
                return state.setValue(CONNECTION, PlanterConnection.SINGLE);
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** Devuelve límites de selección y colisión según la orientación. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(CONNECTION), false);
    }

    /** Usa colisión más alta para la maceta sin incluir follaje. */
    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(CONNECTION), true);
    }

    /** Los límites unidos cubren la junta; SINGLE conserva los cuatro bordes. */
    private static VoxelShape shapeFor(PlanterConnection connection, boolean collision) {
        return switch (connection) {
            case NORTH -> collision ? NORTH_COLLISION : NORTH_SHAPE;
            case EAST -> collision ? EAST_COLLISION : EAST_SHAPE;
            case SOUTH -> collision ? SOUTH_COLLISION : SOUTH_SHAPE;
            case WEST -> collision ? WEST_COLLISION : WEST_SHAPE;
            default -> collision ? SINGLE_COLLISION : SINGLE_SHAPE;
        };
    }

    /** Transforma la dirección del compañero sin cambiar macetas independientes. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction direction = state.getValue(CONNECTION).direction();
        return direction == null
                ? state
                : state.setValue(CONNECTION, PlanterConnection.fromDirection(rotation.rotate(direction)));
    }

    /** Refleja la conexión con la misma transformación de los giros. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction direction = state.getValue(CONNECTION).direction();
        return direction == null ? state : rotate(state, mirror.getRotation(direction));
    }

    /** Declara propiedades guardadas y usadas para elegir modelos. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTION);
    }
}
