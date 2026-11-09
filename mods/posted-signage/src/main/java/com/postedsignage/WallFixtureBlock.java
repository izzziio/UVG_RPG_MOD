package com.postedsignage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** TV, gabinete y cartel de pared: modelos estáticos y soporte en pared o vidrio. */
public final class WallFixtureBlock extends InteriorAssemblyBlock {
    public final InteriorGeometry.Kind kind;
    public WallFixtureBlock(Properties p,InteriorGeometry.Kind kind,int w,int h){super(p,w,h);this.kind=kind;registerDefaultState(defaultBlockState().setValue(GlassSignMount.GLASS_MOUNTED,false));}
    @Override protected MapCodec<? extends WallFixtureBlock> codec(){return simpleCodec(p->new WallFixtureBlock(p,kind,width,height));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(GlassSignMount.GLASS_MOUNTED);}
    @Override protected Direction placementFacing(BlockPlaceContext context){return context.getClickedFace();}
    // Todos los apoyos deben tener el mismo plano de montaje.
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var state=super.getStateForPlacement(context);if(state==null)return null;
        var facing=state.getValue(FACING);var level=context.getLevel();var root=context.getClickedPos();
        boolean glass=GlassSignMount.glassFace(level.getBlockState(root.relative(facing.getOpposite())),facing);
        for(int x=0;x<width;x++)for(int y=0;y<height;y++){
            var pos=root.relative(facing.getClockWise(),x).above(y);
            if(!GlassSignMount.supported(level,pos,facing)||GlassSignMount.glassFace(level.getBlockState(pos.relative(facing.getOpposite())),facing)!=glass)return null;
        }
        return state.setValue(GlassSignMount.GLASS_MOUNTED,glass);
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        var facing=state.getValue(FACING);
        return super.canSurvive(state,level,pos)&&GlassSignMount.supported(level,pos,facing)
                &&state.getValue(GlassSignMount.GLASS_MOUNTED)==GlassSignMount.glassFace(level.getBlockState(pos.relative(facing.getOpposite())),facing);
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        return InteriorShapes.shape(kind,s.getValue(FACING),s.getValue(PART_X),s.getValue(PART_Y),false,s.getValue(GlassSignMount.GLASS_MOUNTED));
    }
}
