package com.postedsignage;

import com.mojang.serialization.MapCodec;
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

/** Letrero fino cuya orientación define pared de soporte y selección. */
public final class WallSignBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WallSignBlock> CODEC = simpleCodec(WallSignBlock::new);

    // FACING apunta fuera del soporte; el panel queda
    // en el borde opuesto de la celda, junto a ese soporte.
    private static final VoxelShape NORTH_SHAPE = Block.box(3.0, 3.0, 15.5, 13.0, 13.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(3.0, 3.0, 0.0, 13.0, 13.0, 0.5);
    private static final VoxelShape WEST_SHAPE = Block.box(15.5, 3.0, 3.0, 16.0, 13.0, 13.0);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 3.0, 3.0, 0.5, 13.0, 13.0);

    // Configura las propiedades iniciales del bloque.
    public WallSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GlassSignMount.GLASS_MOUNTED, false));
    }

    /** Minecraft reconstruye el bloque mediante este códec. */
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    // Calcula la orientación y comprueba la colocación.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (!clickedFace.getAxis().isHorizontal()) {
            return null;
        }

        BlockState state = defaultBlockState().setValue(FACING, clickedFace);
        return state.canSurvive(context.getLevel(), context.getClickedPos())
                ? GlassSignMount.state(state,context.getLevel(),context.getClickedPos(),clickedFace) : null;
    }

    /** Comprueba el soporte sólido detrás del bloque. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return GlassSignMount.supported(level,pos,facing);
    }

    /** Revalida el soporte al cambiar vecinos y retira el bloque si se pierde. */
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

    /** Devuelve límites de selección y colisión según la orientación. */
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

    /** Gira la orientación guardada conservando el soporte. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Convierte el reflejo en un giro de orientación. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    /** Declara propiedades guardadas y usadas para elegir modelos. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GlassSignMount.GLASS_MOUNTED);
    }
}
