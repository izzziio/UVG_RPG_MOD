package com.postedsignage;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Comparte formas por estado; nunca conserva niveles ni entidades. */
public final class InteriorShapes {
    private record Key(InteriorGeometry.Kind kind,Direction facing,int x,int y,boolean active,boolean glass){}
    private static final java.util.Map<Key,VoxelShape> CACHE=new ConcurrentHashMap<>();
    private InteriorShapes(){}
    // Recorta las mismas cajas usadas por el generador de modelos.
    public static VoxelShape shape(InteriorGeometry.Kind kind,Direction facing,int x,int y,boolean active,boolean glass){
        return CACHE.computeIfAbsent(new Key(kind,facing,x,y,active,glass),key->{
            VoxelShape shape=Shapes.empty();
            for(var box:InteriorGeometry.boxes(kind,active)){
                double x0=Math.max(0,box[0]-x*16),y0=Math.max(0,box[1]-y*16);
                double x1=Math.min(16,box[3]-x*16),y1=Math.min(16,box[4]-y*16);
                if(x0>=x1||y0>=y1)continue;
                var bounds=CitDoorGeometry.transform(new AABB(x0/16,y0/16,box[2]/16,x1/16,y1/16,box[5]/16),BlockPos.ZERO,facing,1,false,false);
                shape=Shapes.or(shape,Shapes.create(bounds));
            }
            return glass?shape.move(-facing.getStepX()*GlassSignMount.INSET,0,-facing.getStepZ()*GlassSignMount.INSET):shape;
        });
    }
}
