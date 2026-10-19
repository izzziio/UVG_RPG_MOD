package com.postedsignage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Remove only obsolete upper cells after legacy IDs have resolved through registry aliases. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID)
public final class FacilityMigration {
    // Evita crear instancias de esta clase de utilidades.
    private FacilityMigration() {}
    // Elimina únicamente las piezas antiguas obsoletas.
    public static void removeObsoleteCell(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof FacilityMultiBlock block && block.isObsoletePart(state)) {
            // Old decorative upper cells had no inventory or independent item ownership.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }
    // Programa la limpieza de piezas antiguas al cargar.
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        // Queue after loading finishes, even when ChunkEvent runs on the server thread.
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
            if (!level.hasChunk(chunk.getPos().x, chunk.getPos().z)) return;
            var sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                var section = sections[index];
                if (!section.maybeHas(state -> state.getBlock() instanceof FacilityMultiBlock block && block.isObsoletePart(state))) continue;
                int baseY = chunk.getMinBuildHeight() + index * 16;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z);
                    if (state.getBlock() instanceof FacilityMultiBlock block && block.isObsoletePart(state)) {
                        removeObsoleteCell(level, new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z));
                    }
                }
            }
        }));
    }
}
