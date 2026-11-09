package com.postedsignage;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Retira las celdas asociadas sin que las actualizaciones recursivas de forma evalúen su botín. */
final class MultiblockRemoval {
    // Evita crear instancias de esta clase de utilidades.
    private MultiblockRemoval() {}

    // Retira las piezas compañeras sin duplicar objetos.
    static void beforePlayerBreak(Level level, BlockPos selected, BlockPos origin,
            Map<BlockPos, BlockState> parts, Player player) {
        if (level.isClientSide()) return;
        // Minecraft procesa el objeto de la celda seleccionada; solo el origen tiene botín,
        // por eso transfiere el objeto si se rompe una pieza acompañante.
        BlockState originState = parts.get(origin);
        if (!player.isCreative() && !selected.equals(origin) && originState != null
                && originState.canHarvestBlock(level, origin, player)) {
            Block.dropResources(originState, level, origin, level.getBlockEntity(origin),
                    player, player.getMainHandItem());
        }
        for (BlockPos pos : parts.keySet()) {
            if (!pos.equals(selected)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        for (var part : parts.entrySet()) {
            if (!part.getKey().equals(selected)) {
                level.updateNeighborsAt(part.getKey(), part.getValue().getBlock());
            }
        }
    }
}
