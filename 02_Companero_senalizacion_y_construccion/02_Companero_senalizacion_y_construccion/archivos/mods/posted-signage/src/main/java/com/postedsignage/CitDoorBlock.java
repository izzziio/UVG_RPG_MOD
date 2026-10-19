package com.postedsignage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** A stationary frame/controller footprint; separate internal cells hold the open leaf. */
public final class CitDoorBlock extends BaseEntityBlock {
    // Orientación, posición de cada pieza y estado de la bisagra.
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty X=IntegerProperty.create("part_x",0,1), Y=IntegerProperty.create("part_y",0,2);
    public static final BooleanProperty RIGHT=BooleanProperty.create("right_hinge"), OPEN=BlockStateProperties.OPEN;
    public final int width,height;
    public final boolean hasLabel;
    // Configura las propiedades iniciales del bloque.
    public CitDoorBlock(Properties p,int w,int h) {
        this(p,w,h,true);
    }
    // Configura las propiedades iniciales del bloque.
    public CitDoorBlock(Properties p,int w,int h,boolean hasLabel) {
        super(p.dynamicShape());width=w;height=h;this.hasLabel=hasLabel;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(X,0).setValue(Y,0).setValue(RIGHT,false).setValue(OPEN,false));
    }
    // Permite reconstruir el bloque desde su registro.
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new CitDoorBlock(p,1,2));}
    // Declara las propiedades guardadas en cada bloque.
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,X,Y,RIGHT,OPEN);}
    // Crea los datos asociados al bloque colocado.
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new CitDoorEntity(p,s);}
    // Encuentra el controlador desde cualquier celda del marco.
    public static BlockPos rootPos(BlockPos p,BlockState s){return p.relative(s.getValue(FACING).getClockWise(),-s.getValue(X)).below(s.getValue(Y));}
    // Calcula la orientación y comprueba la colocación.
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        Direction f=c.getHorizontalDirection().getOpposite(),r=f.getClockWise();
        BlockPos p=c.getClickedPos();
        // La mitad pulsada determina el lado de la bisagra.
        double hit=(c.getClickLocation().x-p.getX()-.5)*r.getStepX()+(c.getClickLocation().z-p.getZ()-.5)*r.getStepZ();
        for(int x=0;x<width;x++)for(int y=0;y<height;y++){
            BlockPos q=p.relative(r,x).above(y);
            if(!c.getLevel().isInWorldBounds(q)||!c.getLevel().getWorldBorder().isWithinBounds(q)
                    ||!c.getLevel().getBlockState(q).canBeReplaced(c)||!c.getLevel().getFluidState(q).isEmpty())return null;
        }
        return defaultBlockState().setValue(FACING,f).setValue(RIGHT,hit>=0);
    }
    // Monta el marco y recupera la etiqueta del objeto.
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity placer,ItemStack stack){
        if(l.isClientSide)return;
        for(int x=0;x<width;x++)for(int y=0;y<height;y++)if(x!=0||y!=0)
            l.setBlock(p.relative(s.getValue(FACING).getClockWise(),x).above(y),s.setValue(X,x).setValue(Y,y),UPDATE_ALL);
        if(l.getBlockEntity(p) instanceof CitDoorEntity e){
            e.readItem(stack);e.sync();
            if(placer instanceof net.minecraft.server.level.ServerPlayer player)e.openEditor(player);
        }
    }
    // Abre la puerta o solicita editar al agacharse.
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(!l.mayInteract(player,p))return InteractionResult.FAIL;
        CitDoorEntity e=CitDoorEntity.find(l,p);
        if(!l.isClientSide&&e!=null){
            if(hasLabel&&player.isShiftKeyDown()) { if(player instanceof net.minecraft.server.level.ServerPlayer sp)e.openEditor(sp); }
            else e.toggle(player);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    // Reutiliza la interacción aunque la mano tenga un objeto.
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        useWithoutItem(s,l,p,player,hit);return ItemInteractionResult.sidedSuccess(l.isClientSide);
    }
    // Devuelve los límites de selección y colisión.
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        BlockPos root=rootPos(p,s);
        VoxelShape shape=CitDoorGeometry.frameShape(root,p,s.getValue(FACING),width,height);
        return Shapes.or(shape,CitDoorGeometry.leafCells(root,s.getValue(FACING),width,height,s.getValue(RIGHT),s.getValue(OPEN)).getOrDefault(p,Shapes.empty()));
    }
    // Retira toda la puerta respetando el modo de juego.
    @Override public BlockState playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){
        CitDoorEntity e=CitDoorEntity.find(l,p);if(!l.isClientSide&&e!=null)e.dismantle(!player.isCreative());
        return super.playerWillDestroy(l,p,s,player);
    }
    // Limpia el conjunto si se reemplaza una pieza.
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
        if(!s.is(next.getBlock())&&!l.isClientSide&&l.getBlockEntity(rootPos(p,s)) instanceof CitDoorEntity e)e.dismantle(true);
        super.onRemove(s,l,p,next,moving);
    }
    // Elige cómo se dibuja el bloque.
    @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.ENTITYBLOCK_ANIMATED;}
}
