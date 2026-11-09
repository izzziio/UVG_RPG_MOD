package com.postedsignage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Dos hojas retráctiles: solo cambian estados, sin ocupar celdas vecinas. */
public final class ElevatorDoorBlock extends InteriorAssemblyBlock {
    public static final BooleanProperty OPEN=BooleanProperty.create("open");
    public ElevatorDoorBlock(Properties p){super(p,2,3);registerDefaultState(defaultBlockState().setValue(OPEN,false));}
    @Override protected MapCodec<? extends ElevatorDoorBlock> codec(){return simpleCodec(ElevatorDoorBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){super.createBlockStateDefinition(b);b.add(OPEN);}
    // Comprueba la estructura completa antes de cambiar alguna parte.
    public boolean setOpen(Level level,BlockPos part,boolean open,Player player){
        var state=level.getBlockState(part);if(!state.is(this))return false;var root=origin(part,state);
        var facing=state.getValue(FACING);
        for(int x=0;x<2;x++)for(int y=0;y<3;y++){
            var pos=root.relative(facing.getClockWise(),x).above(y);var s=level.getBlockState(pos);
            if(!s.is(this)||!origin(pos,s).equals(root)||s.getValue(FACING)!=facing||(player!=null&&!level.mayInteract(player,pos)))return false;
            if(!open){
                for(var box:InteriorShapes.shape(InteriorGeometry.Kind.ELEVATOR,facing,x,y,false,false).toAabbs())
                    if(!level.getEntitiesOfClass(LivingEntity.class,box.move(pos),e->e.isAlive()&&!e.isSpectator()).isEmpty())return false;
            }
        }
        if(state.getValue(OPEN)!=open){
            for(int x=0;x<2;x++)for(int y=0;y<3;y++){
                var pos=root.relative(facing.getClockWise(),x).above(y);level.setBlock(pos,level.getBlockState(pos).setValue(OPEN,open),UPDATE_ALL);
            }
            level.playSound(null,root,open?net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN:net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.6F,.8F);
        }
        if(open&&!level.isClientSide)level.scheduleTick(root,this,80);
        return true;
    }
    // La botonera abre y el temporizador cierra solo cuando el paso está libre.
    @Override protected void tick(BlockState s,ServerLevel level,BlockPos pos,RandomSource random){
        if(s.getValue(PART_X)==0&&s.getValue(PART_Y)==0&&s.getValue(OPEN)&&!setOpen(level,pos,false,null))level.scheduleTick(pos,this,10);
    }
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(!l.mayInteract(player,p))return InteractionResult.FAIL;
        if(!l.isClientSide&&!setOpen(l,p,!s.getValue(OPEN),player))player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.posted_signage.elevator_blocked"),true);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){useWithoutItem(s,l,p,player,hit);return ItemInteractionResult.sidedSuccess(l.isClientSide);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return InteriorShapes.shape(InteriorGeometry.Kind.ELEVATOR,s.getValue(FACING),s.getValue(PART_X),s.getValue(PART_Y),s.getValue(OPEN),false);}
}
