package com.postedsignage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
// Permite renderizar el vidrio con conexiones dinámicas.
public final class CitGlassEntity extends BlockEntity {
    private final BlockPos abovePos,belowPos;
    private final net.minecraft.world.phys.AABB renderBounds;
    public net.minecraft.world.phys.AABB renderBounds(){return renderBounds;}
    private BlockState cachedState,cachedAbove,cachedBelow;
    private java.util.List<CitDoorGeometry.Piece> cachedGeometry=java.util.List.of();
    // Inicializa los datos de esta entidad.
    public CitGlassEntity(BlockPos p,BlockState s){super(PostedSignage.CIT_GLASS_ENTITY.get(),p,s);abovePos=p.above();belowPos=p.below();renderBounds=new net.minecraft.world.phys.AABB(p);}
    // Solo reconstruye el panel cuando cambian sus conexiones o vecinos verticales.
    public java.util.List<CitDoorGeometry.Piece> geometry() {
        if(level==null)return java.util.List.of();
        BlockState state=getBlockState(),above=level.getBlockState(abovePos),below=level.getBlockState(belowPos);
        if(state!=cachedState||above!=cachedAbove||below!=cachedBelow){
            cachedGeometry=java.util.List.copyOf(CitGlassBlock.pieces(state,level,worldPosition));
            cachedState=state;cachedAbove=above;cachedBelow=below;
        }
        return cachedGeometry;
    }

}
