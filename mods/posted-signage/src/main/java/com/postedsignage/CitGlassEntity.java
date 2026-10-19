package com.postedsignage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
// Permite renderizar el vidrio con conexiones dinámicas.
public final class CitGlassEntity extends BlockEntity {
    // Inicializa los datos de esta entidad.
    public CitGlassEntity(BlockPos p,BlockState s){super(PostedSignage.CIT_GLASS_ENTITY.get(),p,s);}
}
