package com.postedsignage;

import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thin decorative object whose item places a complete width-by-height structure. */
public class FacilityMultiBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FacilityMultiBlock> CODEC = simpleCodec(p -> new FacilityMultiBlock(p, 1, 1));
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 2);
    protected final int width;
    protected final int height;

    // Configura las propiedades iniciales del bloque.
    public FacilityMultiBlock(Properties properties, int width, int height) {
        super(properties);
        this.width = width;
        this.height = height;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART_X, 0).setValue(PART_Y, 0));
    }
    // Permite reconstruir el bloque desde su registro.
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    // Declara las propiedades guardadas en cada bloque.
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART_X, PART_Y); }

    // Calcula la orientación y comprueba la colocación.
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos();
        if (origin.getY() + height > context.getLevel().getMaxBuildHeight()) return null;
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            BlockPos p = origin.relative(facing.getClockWise(), x).above(y);
            if (!context.getLevel().getBlockState(p).canBeReplaced(context) || !context.getLevel().getFluidState(p).isEmpty()) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    // Coloca las piezas restantes del conjunto.
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            if (x == 0 && y == 0) continue;
            level.setBlock(pos.relative(widthDirection(state), x).above(y), state.setValue(PART_X, x).setValue(PART_Y, y), UPDATE_ALL);
        }
    }

    // Localiza el origen a partir de cualquier pieza.
    protected BlockPos origin(BlockPos pos, BlockState state) {
        return pos.relative(widthDirection(state), -state.getValue(PART_X)).below(state.getValue(PART_Y));
    }
    // Obtiene la dirección horizontal que ocupa el conjunto.
    protected Direction widthDirection(BlockState state) { return state.getValue(FACING).getClockWise(); }
    // Comprueba que el bloque conserva su soporte.
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (isObsoletePart(state)) return false;
        // The origin must be allowed to enter the world before it can anchor the
        // remaining parts. Requiring it to find itself here makes BlockItem reject
        // every initial placement before setPlacedBy has a chance to assemble it.
        if (state.getValue(PART_X) == 0 && state.getValue(PART_Y) == 0) return true;
        BlockPos o = origin(pos, state);
        return level.getBlockState(o).is(this) && level.getBlockState(o).getValue(FACING) == state.getValue(FACING);
    }
    // Detecta piezas fuera de las dimensiones actuales.
    public boolean isObsoletePart(BlockState state) {
        return state.getValue(PART_X) >= width || state.getValue(PART_Y) >= height;
    }
    // Recalcula el estado al cambiar un vecino.
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }
    // Retira las piezas compañeras sin duplicar el objeto.
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos o = origin(pos, state);
            Direction f = state.getValue(FACING);
            var parts = new LinkedHashMap<BlockPos, BlockState>();
            for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
                BlockPos p = o.relative(widthDirection(state), x).above(y);
                BlockState expected = state.setValue(PART_X, x).setValue(PART_Y, y);
                if (level.getBlockState(p).equals(expected)) parts.put(p, expected);
            }
            MultiblockRemoval.beforePlayerBreak(level, pos, o, parts, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    // Gira la orientación guardada.
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    // Refleja la orientación del bloque.
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    // Devuelve los límites de selección y colisión.
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Block.box(0, 0, 2, 16, 16, 14); }
}
