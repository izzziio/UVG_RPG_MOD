package com.postedsignage;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
// Representa las celdas ocupadas por la hoja móvil.
public final class CitSwingBlock extends BaseEntityBlock {
    // La hoja es transparente; calcular luz no accede a su controlador.
    @Override protected boolean propagatesSkylightDown(BlockState s,BlockGetter level,BlockPos pos){return true;}
    @Override protected int getLightBlock(BlockState s,BlockGetter level,BlockPos pos){return 0;}
    // Configura las propiedades iniciales del bloque.
    public CitSwingBlock(Properties p){super(p.dynamicShape());}
    // Permite reconstruir el bloque desde su registro.
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(CitSwingBlock::new);}
    // Crea los datos asociados al bloque colocado.
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new CitSwingEntity(p,s);}
    // Elige cómo se dibuja el bloque.
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.INVISIBLE;}
    // Libera las celdas sobrantes de puertas abiertas con geometría antigua.
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level,BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type){
        if(level.isClientSide)return null;
        return (world,pos,current,entity)->{
            if(!(entity instanceof CitSwingEntity swing)||swing.geometryChecked)return;
            if(swing.owner()!=null&&!world.hasChunkAt(swing.owner()))return;
            swing.geometryChecked=true;
            CitDoorEntity owner=CitDoorEntity.find(world,pos);
            if(owner==null){world.removeBlock(pos,false);return;}
            if(owner.getBlockState().getValue(CitDoorBlock.OPEN)&&owner.leafCells(true).containsKey(pos))return;
            boolean changing=owner.changing;owner.changing=true;
            try{world.removeBlock(pos,false);}finally{owner.changing=changing;}
        };
    }
    // Devuelve los límites de selección y colisión.
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        CitDoorEntity e=CitDoorEntity.find(l,p);return e==null?Shapes.empty():e.leafCells(true).getOrDefault(p,Shapes.empty());
    }
    // Redirige la interacción al controlador de la puerta.
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        CitDoorEntity e=CitDoorEntity.find(l,p);
        if(e!=null)return ((CitDoorBlock)e.getBlockState().getBlock()).useWithoutItem(e.getBlockState(),l,e.getBlockPos(),player,hit);
        return InteractionResult.PASS;
    }
    // Redirige el clic con objeto al controlador.
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){useWithoutItem(s,l,p,player,hit);return ItemInteractionResult.sidedSuccess(l.isClientSide);}
    // Rompe el conjunto desde una celda de la hoja.
    @Override public BlockState playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){CitDoorEntity e=CitDoorEntity.find(l,p);if(!l.isClientSide&&e!=null)e.dismantle(!player.isCreative());return super.playerWillDestroy(l,p,s,player);}
    // Retira la puerta si desaparece una celda móvil.
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState n,boolean moving){if(!s.is(n.getBlock())&&!l.isClientSide){CitDoorEntity e=CitDoorEntity.find(l,p);if(e!=null)e.dismantle(true);}super.onRemove(s,l,p,n,moving);}
}
