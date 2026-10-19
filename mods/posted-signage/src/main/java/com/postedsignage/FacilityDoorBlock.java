package com.postedsignage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The wide door rotates one complete panel and its occupied footprint together. */
public final class FacilityDoorBlock extends FacilityMultiBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final MapCodec<FacilityDoorBlock> CODEC = simpleCodec(p -> new FacilityDoorBlock(p, 1, 2));

    // Configura las propiedades iniciales del bloque.
    public FacilityDoorBlock(Properties properties, int width, int height) {
        super(properties, width, height);
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }
    // Permite reconstruir el bloque desde su registro.
    @Override protected MapCodec<FacilityDoorBlock> codec() { return CODEC; }
    /** Legacy world blocks recover as the current text-free door instead of a retired item. */
    @Override public net.minecraft.world.item.Item asItem() {
        return width==2?PostedSignage.CLEAR_WIDE_DOOR_ITEM.get():PostedSignage.CLEAR_SHORT_DOOR_ITEM.get();
    }
    // Obtiene la dirección horizontal que ocupa el conjunto.
    @Override protected Direction widthDirection(BlockState state) {
        return width == 2 && state.getValue(OPEN) ? state.getValue(FACING) : super.widthDirection(state);
    }
    // Calcula el origen de la puerta después del giro.
    BlockPos toggledOrigin(BlockPos anchor, BlockState state) {
        if (width != 2) return anchor;
        int sign = state.getValue(OPEN) ? -1 : 1;
        return anchor.relative(state.getValue(FACING).getClockWise(), sign)
                .relative(state.getValue(FACING).getOpposite(), sign);
    }
    // Declara las propiedades guardadas en cada bloque.
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }
    // Comprueba obstáculos y gira todas las piezas antiguas.
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            BlockPos anchor = origin(pos, state);
            Direction right = widthDirection(state);
            BlockState toggled = state.cycle(OPEN);
            BlockPos targetOrigin = toggledOrigin(anchor, state);
            var oldParts = new java.util.LinkedHashMap<BlockPos, BlockState>();
            var newParts = new java.util.LinkedHashMap<BlockPos, BlockState>();
            // Check every part before making any change, including protection at other cells.
            for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
                BlockPos part = anchor.relative(right, x).above(y);
                if (!level.mayInteract(player, part) || !level.getBlockState(part).equals(
                        state.setValue(PART_X, x).setValue(PART_Y, y))) return InteractionResult.FAIL;
                oldParts.put(part, level.getBlockState(part));
                BlockState target = toggled.setValue(PART_X, x).setValue(PART_Y, y);
                newParts.put(targetOrigin.relative(widthDirection(toggled), x).above(y), target);
            }
            for (var entry : newParts.entrySet()) {
                BlockPos target = entry.getKey();
                if (!level.hasChunkAt(target) || !level.getWorldBorder().isWithinBounds(target)
                        || !level.mayInteract(player, target)
                        || (!oldParts.containsKey(target) && !level.isEmptyBlock(target))
                        || !level.isUnobstructed(entry.getValue(), target, CollisionContext.empty())) return InteractionResult.FAIL;
            }
            boolean open = !state.getValue(OPEN);
            // Install the full target before sending neighbor updates, so no temporary
            // missing anchor causes a multiblock collapse or loot during the swing.
            for (BlockPos oldPos : oldParts.keySet()) {
                if (!newParts.containsKey(oldPos)) level.setBlock(oldPos,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
            }
            for (var entry : newParts.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
            var changed = new java.util.HashSet<>(oldParts.keySet());
            changed.addAll(newParts.keySet());
            for (BlockPos changedPos : changed) {
                level.updateNeighborsAt(changedPos, this);
                level.getBlockState(changedPos).updateNeighbourShapes(level, changedPos, UPDATE_ALL);
            }
            level.playSound(null, anchor, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.BLOCKS, 1, 1);
            level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, anchor);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    // Devuelve los límites de selección y colisión.
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double x0 = 0, x1 = 16, z0 = 0, z1 = 3;
        Direction facing = state.getValue(FACING);
        if (width == 2) {
            z0 = 5;
            z1 = 11;
            if (state.getValue(OPEN)) facing = facing.getCounterClockWise();
        } else if (state.getValue(OPEN)) {
            boolean leftLeaf = width == 2 && state.getValue(PART_X) == 0;
            x0 = leftLeaf ? 0 : 12;
            x1 = leftLeaf ? 4 : 16;
            z1 = 16;
        }
        return switch (facing) {
            case SOUTH -> Block.box(16-x1, 0, 16-z1, 16-x0, 16, 16-z0);
            case EAST -> Block.box(16-z1, 0, x0, 16-z0, 16, x1);
            case WEST -> Block.box(z0, 0, 16-x1, z1, 16, 16-x0);
            default -> Block.box(x0, 0, z0, x1, 16, z1);
        };
    }
}
