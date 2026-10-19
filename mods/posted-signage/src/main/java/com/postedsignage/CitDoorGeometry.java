package com.postedsignage;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Geometry in block units. Local north is the front; only the leaf rotates. */
public final class CitDoorGeometry {
    // Evita crear instancias de esta clase de utilidades.
    private CitDoorGeometry() {}
    // Describe una caja y si su material es vidrio.
    public record Piece(AABB box, boolean glass) {}
    // Construye las piezas inmóviles del marco.
    public static List<Piece> frame(int w, int h) {
        return List.of(new Piece(new AABB(0,0,.40,.10,h,.60),false),
                new Piece(new AABB(w-.10,0,.40,w,h,.60),false),
                new Piece(new AABB(.10,h-.12,.40,w-.10,h,.60),false),
                new Piece(new AABB(.10,0,.40,w-.10,.04,.60),false));
    }
    // Construye el vidrio, los bordes y la manija.
    public static List<Piece> leaf(int w, int h, boolean right) {
        double handle = right ? .24 : w-.32;
        return List.of(new Piece(new AABB(.12,.08,.44,.20,h-.12,.56),false),
                new Piece(new AABB(w-.20,.08,.44,w-.12,h-.12,.56),false),
                new Piece(new AABB(.20,.08,.44,w-.20,.20,.56),false),
                new Piece(new AABB(.20,h-.22,.44,w-.20,h-.12,.56),false),
                new Piece(new AABB(.20,.20,.48,w-.20,h-.22,.52),true),
                new Piece(new AABB(handle,h*.43,.33,handle+.08,h*.43+.07,.67),false));
    }
    // Gira un punto alrededor de la bisagra elegida.
    public static Vec3 swing(Vec3 v, int w, boolean right) {
        double hinge = right ? w-.12 : .12;
        double sign = right ? -1 : 1;
        return new Vec3(hinge + sign*(v.z-.5), v.y, .5-sign*(v.x-hinge));
    }
    // Convierte coordenadas locales a coordenadas del mundo.
    public static Vec3 world(Vec3 v, BlockPos root, Direction facing) {
        Direction right = facing.getClockWise();
        return root.getCenter().add(right.getStepX()*(v.x-.5)-facing.getStepX()*(v.z-.5),
                v.y-.5, right.getStepZ()*(v.x-.5)-facing.getStepZ()*(v.z-.5));
    }
    // Orienta una caja y aplica el giro de apertura.
    public static AABB transform(AABB box, BlockPos root, Direction facing, int width, boolean right, boolean open) {
        Vec3 a = new Vec3(box.minX,box.minY,box.minZ), b = new Vec3(box.maxX,box.maxY,box.maxZ);
        if (open) { a=swing(a,width,right); b=swing(b,width,right); }
        a=world(a,root,facing); b=world(b,root,facing);
        return new AABB(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),
                Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z));
    }
    // Divide la hoja en colisiones por celda.
    public static Map<BlockPos,VoxelShape> leafCells(BlockPos root, Direction facing, int w, int h, boolean right, boolean open) {
        Map<BlockPos,VoxelShape> cells=new LinkedHashMap<>();
        // Full glass collision, even though the material itself is transparent.
        for (Piece piece: leaf(w,h,right)) {
            AABB box=transform(piece.box,root,facing,w,right,open);
            for(int x=(int)Math.floor(box.minX+1e-7);x<Math.ceil(box.maxX-1e-7);x++)
                for(int y=(int)Math.floor(box.minY+1e-7);y<Math.ceil(box.maxY-1e-7);y++)
                    for(int z=(int)Math.floor(box.minZ+1e-7);z<Math.ceil(box.maxZ-1e-7);z++) {
                        BlockPos pos=new BlockPos(x,y,z);
                        AABB clipped=box.intersect(new AABB(pos)).move(-x,-y,-z);
                        cells.merge(pos,Shapes.create(clipped),Shapes::or);
                    }
        }
        return cells;
    }
    // Recorta el marco a los límites de una celda.
    public static VoxelShape frameShape(BlockPos root, BlockPos pos, Direction facing, int w, int h) {
        VoxelShape result=Shapes.empty();
        for(Piece piece:frame(w,h)) {
            AABB box=transform(piece.box,root,facing,w,false,false);
            if(box.intersects(new AABB(pos))) result=Shapes.or(result,Shapes.create(box.intersect(new AABB(pos)).move(-pos.getX(),-pos.getY(),-pos.getZ())));
        }
        return result;
    }
}
