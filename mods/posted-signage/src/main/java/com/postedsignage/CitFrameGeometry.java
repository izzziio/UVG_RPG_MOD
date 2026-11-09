package com.postedsignage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Superficies del marco fijo, recortadas por celda sin añadir tapas interiores. */
public final class CitFrameGeometry {
    private CitFrameGeometry(){}
    public static float[] mesh(BlockState state,BlockGetter level,BlockPos pos){
        if(state.getBlock() instanceof CitGlassBlock)return CitGeometryMesh.compile(CitGlassBlock.pieces(state,level,pos),false);
        var door=(CitDoorBlock)state.getBlock();var facing=state.getValue(CitDoorBlock.FACING);
        var relativeRoot=CitDoorBlock.rootPos(pos,state).subtract(pos);
        var source=CitGeometryMesh.compile(CitDoorGeometry.frame(door.width,door.height),false);
        List<float[]> vertices=new ArrayList<>();
        for(int start=0;start<source.length;start+=32){
            List<float[]> polygon=new ArrayList<>();
            for(int i=start;i<start+32;i+=8){
                var point=CitDoorGeometry.world(new Vec3(source[i],source[i+1],source[i+2]),relativeRoot,facing);
                var right=facing.getClockWise();
                polygon.add(new float[]{(float)point.x,(float)point.y,(float)point.z,source[i+3],source[i+4],
                        right.getStepX()*source[i+5]-facing.getStepX()*source[i+7],source[i+6],
                        right.getStepZ()*source[i+5]-facing.getStepZ()*source[i+7]});
            }
            for(int axis=0;axis<3;axis++){polygon=clip(polygon,axis,0,true);polygon=clip(polygon,axis,1,false);}
            // Un rectángulo alineado conserva cuatro esquinas; descarta contactos sin área.
            polygon=distinct(polygon);
            if(polygon.size()==4&&area(polygon)>1e-9)vertices.addAll(polygon);
        }
        float[] result=new float[vertices.size()*8];int offset=0;
        for(var vertex:vertices)for(float value:vertex)result[offset++]=value;
        return result;
    }
    private static List<float[]> clip(List<float[]> input,int axis,float plane,boolean minimum){
        List<float[]> out=new ArrayList<>();if(input.isEmpty())return out;
        var previous=input.getLast();boolean prevInside=minimum?previous[axis]>=plane:previous[axis]<=plane;
        for(var current:input){
            boolean inside=minimum?current[axis]>=plane:current[axis]<=plane;
            if(inside!=prevInside){
                float t=(plane-previous[axis])/(current[axis]-previous[axis]);float[] intersection=new float[8];
                for(int i=0;i<8;i++)intersection[i]=previous[i]+t*(current[i]-previous[i]);
                intersection[axis]=plane;out.add(intersection);
            }
            if(inside)out.add(current);previous=current;prevInside=inside;
        }
        return out;
    }
    // Los cruces exactos por una esquina pueden repetir un vértice.
    private static List<float[]> distinct(List<float[]> polygon){
        List<float[]> out=new ArrayList<>();
        for(var vertex:polygon)if(out.isEmpty()||!samePoint(out.getLast(),vertex))out.add(vertex);
        if(out.size()>1&&samePoint(out.getFirst(),out.getLast()))out.removeLast();
        return out;
    }
    private static boolean samePoint(float[] a,float[] b){return a[0]==b[0]&&a[1]==b[1]&&a[2]==b[2];}
    private static double area(List<float[]> quad){
        var a=quad.get(0);var b=quad.get(1);var c=quad.get(2);
        return new Vec3(b[0]-a[0],b[1]-a[1],b[2]-a[2]).cross(new Vec3(c[0]-a[0],c[1]-a[1],c[2]-a[2])).length();
    }
}
