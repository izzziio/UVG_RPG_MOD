package com.postedsignage;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Colocación atómica y retirada completa para los objetos nuevos de varias celdas. */
public class InteriorAssemblyBlock extends FacilityMultiBlock {
    private record Assembly(Level level,BlockPos root){}
    private static final ThreadLocal<Set<Assembly>> REMOVING=ThreadLocal.withInitial(HashSet::new);
    public InteriorAssemblyBlock(Properties properties,int width,int height){super(properties,width,height);}
    protected Direction placementFacing(BlockPlaceContext context){return context.getHorizontalDirection().getOpposite();}
    // Comprueba cada destino, también permisos, líquidos y borde del mundo.
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var level=context.getLevel();var root=context.getClickedPos();var facing=placementFacing(context);
        if(!facing.getAxis().isHorizontal())return null;
        for(int x=0;x<width;x++)for(int y=0;y<height;y++){
            var pos=root.relative(facing.getClockWise(),x).above(y);
            if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.getFluidState(pos).isEmpty()
                    ||!level.getBlockState(pos).canBeReplaced(context))return null;
            var player=context.getPlayer();
            if(player!=null&&(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())))return null;
        }
        return defaultBlockState().setValue(FACING,facing);
    }
    // El guard evita que las retiradas compañeras generen botín recursivo.
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        var key=new Assembly(level,origin(pos,state));boolean entered=REMOVING.get().add(key);
        try{return super.playerWillDestroy(level,pos,state,player);}finally{if(entered)REMOVING.get().remove(key);}
    }
    // También limpia explosiones, reemplazos y pérdida de soportes.
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!level.isClientSide&&!next.is(this)){
            var root=origin(pos,state);var key=new Assembly(level,root);
            if(REMOVING.get().add(key))try{
                var originState=level.getBlockState(root);
                if(!root.equals(pos)&&originState.is(this))dropResources(originState,level,root);
                var removed=new java.util.ArrayList<BlockPos>();
                for(int x=0;x<width;x++)for(int y=0;y<height;y++){
                    var part=root.relative(widthDirection(state),x).above(y);var current=level.getBlockState(part);
                    if(!part.equals(pos)&&current.is(this)&&origin(part,current).equals(root)){
                        level.setBlock(part,Blocks.AIR.defaultBlockState(),UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE);
                        removed.add(part);
                    }
                }
                for(var part:removed)level.updateNeighborsAt(part,this);
            }finally{REMOVING.get().remove(key);}
        }
        super.onRemove(state,level,pos,next,moving);
    }
}
