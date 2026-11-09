package com.postedsignage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

/** Puente opcional: la navegación consulta la puerta abierta sin moverla. */
public final class CitGuideDoors {
    private CitGuideDoors() {}
    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof CitDoorBlock || state.getBlock() instanceof CitSwingBlock;
    }
    // Conserva el marco y la hoja abierta; nunca convierte toda la celda en aire.
    public static VoxelShape pathShape(BlockGetter level,BlockPos pos) {
        var door=nearby(level,pos);
        if(door==null)return null;
        var state=door.getBlockState();var block=(CitDoorBlock)state.getBlock();
        if(!state.getValue(CitDoorBlock.OPEN)&&!door.canOpenForNavigation())return null;
        var frame=state.getBlock() instanceof CitDoorBlock && level.getBlockState(pos).getBlock() instanceof CitDoorBlock
                ?CitDoorGeometry.frameShape(door.getBlockPos(),pos,state.getValue(CitDoorBlock.FACING),block.width,block.height):Shapes.empty();
        return Shapes.or(frame,door.leafCells(true).getOrDefault(pos,Shapes.empty()));
    }
    // Incluye las celdas todavía vacías que ocupará la hoja al abrirse.
    private static CitDoorEntity nearby(BlockGetter level,BlockPos pos){
        var direct=CitDoorEntity.find(level,pos);if(direct!=null)return direct;
        for(var direction:Direction.Plane.HORIZONTAL)for(int n=1;n<=2;n++){
            var door=CitDoorEntity.find(level,pos.relative(direction,n));
            if(door!=null&&door.leafCells(true).containsKey(pos))return door;
        }
        return null;
    }
    // Alinea también la aproximación: el cuerpo debe librar la manija antes del umbral.
    public static Vec3 waypoint(BlockGetter level,BlockPos pos,double floor,double width,double height){
        var base=new Vec3(pos.getX()+.5,floor,pos.getZ()+.5);
        CitDoorEntity door=CitDoorEntity.find(level,pos);
        if(door==null){
            search:for(int n=1;n<=2;n++)for(var direction:Direction.Plane.HORIZONTAL){
                var found=CitDoorEntity.find(level,pos.relative(direction,n));
                if(found!=null){door=found;break search;}
            }
        }
        if(door==null)return base;
        var state=door.getBlockState();var block=(CitDoorBlock)state.getBlock();var facing=state.getValue(CitDoorBlock.FACING);var tangent=facing.getClockWise();
        var origin=door.getBlockPos();
        // Convierte la referencia al borde local, también para ejes negativos.
        double lateral=CitDoorGeometry.world(new Vec3(0,0,0),origin,facing).vectorTo(base).dot(new Vec3(tangent.getStepX(),0,tangent.getStepZ()));
        if(lateral<0||lateral>block.width||floor<origin.getY()-.1||floor+height>origin.getY()+block.height-.1)return base;
        var boxes=new java.util.ArrayList<AABB>();
        for(var piece:CitDoorGeometry.frame(block.width,block.height))boxes.add(CitDoorGeometry.transform(piece.box(),origin,facing,block.width,false,false));
        for(var piece:CitDoorGeometry.leaf(block.width,block.height,state.getValue(CitDoorBlock.RIGHT)))
            boxes.add(CitDoorGeometry.transform(piece.box(),origin,facing,block.width,state.getValue(CitDoorBlock.RIGHT),true));
        double radius=width/2+.002;
        for(double shift:new double[]{0,.05,-.05,.1,-.1,.15,-.15,.2,-.2}){
            var point=base.add(tangent.getStepX()*shift,0,tangent.getStepZ()*shift);
            var a=point.add(facing.getStepX()*3,0,facing.getStepZ()*3);
            var b=point.add(-facing.getStepX()*3,0,-facing.getStepZ()*3);
            var sweep=new AABB(Math.min(a.x,b.x)-radius,floor+.0626,Math.min(a.z,b.z)-radius,
                    Math.max(a.x,b.x)+radius,floor+height-.001,Math.max(a.z,b.z)+radius);
            if(boxes.stream().noneMatch(sweep::intersects))return point;
        }
        return base;
    }
    public static boolean open(Mob guide,BlockPos pos) {
        var door=CitDoorEntity.find(guide.level(),pos);
        return door!=null&&door.openForGuide(guide);
    }
}
