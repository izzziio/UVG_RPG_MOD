package com.postedsignage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Mount the back of a sign on the actual pane face, 0.48 blocks inside its cell. */
public final class GlassSignMount {
    public static final BooleanProperty GLASS_MOUNTED=BooleanProperty.create("glass_mounted");
    public static final double INSET=.48;
    // Evita crear instancias de esta clase de utilidades.
    private GlassSignMount(){}
    // Acepta caras amplias sin brazos que atraviesen el letrero.
    public static boolean glassFace(BlockState support,Direction facing){
        if(!(support.getBlock() instanceof CitGlassBlock))return false;
        var arms=CitGlassBlock.arms(support);
        // Require a complete broad face; a projecting corner arm would intersect the sign.
        return arms.contains(facing.getClockWise())&&arms.contains(facing.getCounterClockWise())&&!arms.contains(facing);
    }
    // Comprueba soporte en vidrio compatible o pared sólida.
    public static boolean supported(LevelReader level,BlockPos pos,Direction facing){
        var backing=pos.relative(facing.getOpposite());var state=level.getBlockState(backing);
        return glassFace(state,facing)||state.isFaceSturdy(level,backing,facing);
    }
    // Actualiza si el letrero está montado sobre vidrio.
    public static BlockState state(BlockState state,LevelReader level,BlockPos pos,Direction facing){
        return state.setValue(GLASS_MOUNTED,glassFace(level.getBlockState(pos.relative(facing.getOpposite())),facing));
    }
    // Calcula el desplazamiento hacia la cara real del vidrio.
    public static Vec3 offset(BlockState state,Direction facing){
        return state.getValue(GLASS_MOUNTED)?new Vec3(-facing.getStepX()*INSET,0,-facing.getStepZ()*INSET):Vec3.ZERO;
    }
    // Desplaza la selección junto con el letrero.
    public static VoxelShape shape(VoxelShape shape,BlockState state,Direction facing){
        var delta=offset(state,facing);return shape.move(delta.x,delta.y,delta.z);
    }
}
