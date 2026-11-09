package com.postedsignage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Botonera de una celda: llama a la puerta más cercana del mismo piso y frente. */
public final class ElevatorCallButtonBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty POWERED=BooleanProperty.create("powered");
    public ElevatorCallButtonBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(GlassSignMount.GLASS_MOUNTED,false));}
    @Override protected MapCodec<? extends ElevatorCallButtonBlock> codec(){return simpleCodec(ElevatorCallButtonBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,POWERED,GlassSignMount.GLASS_MOUNTED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){var f=c.getClickedFace();if(!f.getAxis().isHorizontal())return null;var s=defaultBlockState().setValue(FACING,f);return s.canSurvive(c.getLevel(),c.getClickedPos())?GlassSignMount.state(s,c.getLevel(),c.getClickedPos(),f):null;}
    @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return GlassSignMount.supported(l,p,s.getValue(FACING));}
    @Override protected BlockState updateShape(BlockState s,Direction d,BlockState n,LevelAccessor l,BlockPos p,BlockPos q){return canSurvive(s,l,p)?GlassSignMount.state(s,l,p,s.getValue(FACING)):Blocks.AIR.defaultBlockState();}
    // La búsqueda solo ocurre al pulsar; no existe un tick permanente de búsqueda.
    public BlockPos target(Level level,BlockPos pos,Direction facing){
        BlockPos best=null;double distance=Double.MAX_VALUE;
        for(var p:BlockPos.betweenClosed(pos.offset(-4,-2,-4),pos.offset(4,0,4))){
            if(!level.hasChunkAt(p))continue;var s=level.getBlockState(p);
            if(!(s.getBlock() instanceof ElevatorDoorBlock)||s.getValue(FACING)!=facing||s.getValue(FacilityMultiBlock.PART_X)!=0||s.getValue(FacilityMultiBlock.PART_Y)!=0)continue;
            double dx=p.getX()+.5+facing.getClockWise().getStepX()*.5-(pos.getX()+.5),dz=p.getZ()+.5+facing.getClockWise().getStepZ()*.5-(pos.getZ()+.5);
            if(Math.abs(dx*facing.getStepX()+dz*facing.getStepZ())>1.5)continue;
            double d=dx*dx+dz*dz;if(d<distance&&d<=16){distance=d;best=p.immutable();}
        }
        return best;
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(!l.mayInteract(player,p))return InteractionResult.FAIL;
        if(!l.isClientSide){
            var root=target(l,p,s.getValue(FACING));
            if(root!=null&&((ElevatorDoorBlock)l.getBlockState(root).getBlock()).setOpen(l,root,true,player)){
                l.setBlock(p,s.setValue(POWERED,true),UPDATE_ALL);l.scheduleTick(p,this,10);
                l.playSound(null,p,net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON,net.minecraft.sounds.SoundSource.BLOCKS,.35F,.8F);
            }else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.posted_signage.elevator_missing"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){useWithoutItem(s,l,p,player,hit);return ItemInteractionResult.sidedSuccess(l.isClientSide);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){l.setBlock(p,s.setValue(POWERED,false),UPDATE_ALL);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return InteriorShapes.shape(InteriorGeometry.Kind.BUTTONS,s.getValue(FACING),0,0,s.getValue(POWERED),s.getValue(GlassSignMount.GLASS_MOUNTED));}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
}
