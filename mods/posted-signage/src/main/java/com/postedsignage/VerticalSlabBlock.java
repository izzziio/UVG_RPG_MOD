package com.postedsignage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** FACING identifica la mitad ocupada de la celda. */
public final class VerticalSlabBlock extends HorizontalDirectionalBlock {
    // La segunda mitad guarda su material dentro del mismo estado persistente.
    public enum Fill implements StringRepresentable {
        NONE("none"), BRICK("brick"), QUARTZ("quartz");
        private final String name;
        Fill(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }
    public static final EnumProperty<Fill> FILL = EnumProperty.create("fill", Fill.class);
    public static final MapCodec<VerticalSlabBlock> CODEC = simpleCodec(VerticalSlabBlock::new);
    // Configura las propiedades iniciales del bloque.
    public VerticalSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FILL, Fill.NONE));
    }
    // Permite reconstruir el bloque desde su registro.
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    // Calcula la orientación y comprueba la colocación.
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.getBlock() instanceof VerticalSlabBlock && existing.getValue(FILL) == Fill.NONE)
            return existing.setValue(FILL, this == PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get() ? Fill.BRICK : Fill.QUARTZ);
        Direction face = context.getClickedFace();
        // La colocación lateral se pega al bloque; suelo y techo siguen al jugador.
        Direction half = face.getAxis().isHorizontal() ? face.getOpposite() : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, half);
    }
    // El clic en la mitad libre une losas iguales o de distinto material.
    @Override protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (state.getValue(FILL) != Fill.NONE || !(context.getItemInHand().getItem() instanceof BlockItem item)
                || !(item.getBlock() instanceof VerticalSlabBlock)) return false;
        if (!context.replacingClickedOnBlock()) return true;
        Direction half = state.getValue(FACING), face = context.getClickedFace();
        if (face == half.getOpposite()) return true;
        if (face == half) return false;
        var hit = context.getClickLocation().subtract(context.getClickedPos().getX() + .5, 0, context.getClickedPos().getZ() + .5);
        return hit.x * half.getStepX() + hit.z * half.getStepZ() < 0;
    }
    // Devuelve los límites de selección y colisión.
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(FILL) != Fill.NONE) return Shapes.block();
        return switch (state.getValue(FACING)) {
            case NORTH -> box(0, 0, 0, 16, 16, 8);
            case SOUTH -> box(0, 0, 8, 16, 16, 16);
            case WEST -> box(0, 0, 0, 8, 16, 16);
            default -> box(8, 0, 0, 16, 16, 16);
        };
    }
    // Gira la orientación guardada.
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    // Refleja la orientación del bloque.
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    // Declara las propiedades guardadas en cada bloque.
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, FILL); }
}
