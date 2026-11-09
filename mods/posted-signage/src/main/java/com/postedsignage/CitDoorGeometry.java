package com.postedsignage;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Geometría en unidades de bloque; solo gira la hoja. */
public final class CitDoorGeometry {
    // Evita crear instancias de esta clase de utilidades.
    private CitDoorGeometry() {}
    // Describe una caja y si su material es vidrio.
    public record Piece(AABB box, boolean glass) {}
    // Une solo dos mitades coplanares; las esquinas mantienen sus piezas.
    public static List<Piece> mergeStraightGlass(List<Piece> pieces){
        var glass=pieces.stream().filter(Piece::glass).toList();
        if(glass.size()!=2)return pieces;
        AABB a=glass.get(0).box(),b=glass.get(1).box();
        boolean x=a.minY==b.minY&&a.maxY==b.maxY&&a.minZ==b.minZ&&a.maxZ==b.maxZ
                &&(a.maxX==b.minX||b.maxX==a.minX);
        boolean z=a.minY==b.minY&&a.maxY==b.maxY&&a.minX==b.minX&&a.maxX==b.maxX
                &&(a.maxZ==b.minZ||b.maxZ==a.minZ);
        if(!x&&!z)return pieces;
        List<Piece> result=new ArrayList<>();boolean added=false;
        for(var piece:pieces){
            if(!piece.glass())result.add(piece);
            else if(!added){result.add(new Piece(a.minmax(b),true));added=true;}
        }
        return List.copyOf(result);
    }
    // Recupera la coordenada de profundidad respecto a la hoja girada.
    public static double leafDepth(Vec3 camera,BlockPos root,Direction facing,int width,boolean right,boolean open){
        double dx=camera.x-root.getX()-.5,dz=camera.z-root.getZ()-.5;
        var tangent=facing.getClockWise();
        double x=.5+dx*tangent.getStepX()+dz*tangent.getStepZ();
        double z=.5-dx*facing.getStepX()-dz*facing.getStepZ();
        return open?.5+(right?-1:1)*(x-(right?width-.12:.12)):z;
    }
    // Incluye marco y hoja sin extenderse a puertas vecinas.
    public static AABB renderBounds(BlockPos root,Direction facing,int width,int height,boolean right,boolean open){
        AABB bounds=null;
        for(var piece:frame(width,height)){
            var box=transform(piece.box(),root,facing,width,right,false);
            bounds=bounds==null?box:bounds.minmax(box);
        }
        for(var piece:leaf(width,height,right))bounds=bounds.minmax(transform(piece.box(),root,facing,width,right,open));
        return bounds.inflate(.002);
    }
    // Construye las piezas inmóviles del marco.
    public static List<Piece> frame(int w, int h) {
        return List.of(new Piece(new AABB(0,0,.40,.10,h,.60),false),
                new Piece(new AABB(w-.10,0,.40,w,h,.60),false),
                new Piece(new AABB(.10,h-.12,.40,w-.10,h,.60),false),
                new Piece(new AABB(.10,0,.40,w-.10,.04,.60),false));
    }
    // Construye el vidrio, los bordes y la manija.
    public static List<Piece> leaf(int w, int h, boolean right) {
        // La manija abierta queda dentro del ancho de su propia puerta.
        double handle = right ? .24 : w-.32;
        return List.of(new Piece(new AABB(.12,.08,.44,.20,h-.12,.56),false),
                new Piece(new AABB(w-.20,.08,.44,w-.12,h-.12,.56),false),
                new Piece(new AABB(.20,.08,.44,w-.20,.20,.56),false),
                new Piece(new AABB(.20,h-.22,.44,w-.20,h-.12,.56),false),
                new Piece(new AABB(.20,.20,.48,w-.20,h-.22,.52),true),
                new Piece(new AABB(handle,h*.43,.39,handle+.08,h*.43+.07,.61),false));
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
        // Colisión completa del vidrio aunque sea transparente.
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
