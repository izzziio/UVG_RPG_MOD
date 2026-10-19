package com.postedsignage;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Remove companion cells without recursive shape updates evaluating their loot. */
final class MultiblockRemoval {
    // Evita crear instancias de esta clase de utilidades.
    private MultiblockRemoval() {}

    // Retira las piezas compañeras sin duplicar objetos.
    static void beforePlayerBreak(Level level, BlockPos selected, BlockPos origin,
            Map<BlockPos, BlockState> parts, Player player) {
        if (level.isClientSide()) return;
        // Vanilla handles the selected cell's loot. Only the origin has a loot entry,
        // so transfer its drop when the player instead selected a companion cell.
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
