package gt.edu.uvg.universityguide.entity.navigation;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Integración opcional; funciona también sin Posted Signage instalado. */
public final class GuideDoors {
    private record Access(MethodHandle isDoor,MethodHandle shape,MethodHandle open,MethodHandle waypoint) {}
    private static final Access ACCESS=load();
    private GuideDoors() {}
    // Resuelve el puente una vez, sin dependencia obligatoria entre los mods.
    private static Access load(){
        try{
            var api=Class.forName("com.postedsignage.CitGuideDoors");var lookup=MethodHandles.publicLookup();
            return new Access(lookup.findStatic(api,"isDoor",MethodType.methodType(boolean.class,BlockState.class)),
                    lookup.findStatic(api,"pathShape",MethodType.methodType(VoxelShape.class,BlockGetter.class,BlockPos.class)),
                    lookup.findStatic(api,"open",MethodType.methodType(boolean.class,Mob.class,BlockPos.class)),
                    lookup.findStatic(api,"waypoint",MethodType.methodType(Vec3.class,BlockGetter.class,BlockPos.class,double.class,double.class,double.class)));
        }catch(ReflectiveOperationException absent){return null;}
    }
    public static boolean isDoor(BlockState state){
        if(ACCESS==null)return false;
        try{return (boolean)ACCESS.isDoor.invokeExact(state);}catch(Throwable failure){throw new IllegalStateException("Integración de puertas CIT",failure);}
    }
    public static VoxelShape shape(BlockGetter level,BlockPos pos){
        if(ACCESS==null)return null;
        try{return (VoxelShape)ACCESS.shape.invokeExact(level,pos);}catch(Throwable failure){throw new IllegalStateException("Geometría de puertas CIT",failure);}
    }
    public static Vec3 waypoint(BlockGetter level,BlockPos pos,double floor,double width,double height){
        if(ACCESS==null)return new Vec3(pos.getX()+.5,floor,pos.getZ()+.5);
        try{return (Vec3)ACCESS.waypoint.invokeExact(level,pos,floor,width,height);}catch(Throwable failure){throw new IllegalStateException("Punto de paso de puertas CIT",failure);}
    }
    // Solo abre puertas en el tramo inmediato, antes de entrar en su colisión.
    public static boolean openAhead(Mob guide,Vec3 target){
        if(ACCESS==null)return true;
        var from=guide.position();
        if(from.distanceToSqr(target)>9)return true;
        var area=new AABB(from,target).inflate(guide.getBbWidth()/2+.05,0,guide.getBbWidth()/2+.05);
        area=new AABB(area.minX,Math.min(from.y,target.y),area.minZ,area.maxX,Math.max(from.y,target.y)+guide.getBbHeight(),area.maxZ);
        for(var cursor:BlockPos.betweenClosed(BlockPos.containing(area.minX,area.minY,area.minZ),BlockPos.containing(area.maxX,area.maxY,area.maxZ))){
            var state=guide.level().getBlockState(cursor);
            if(!isDoor(state)||!state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                    ||state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN))continue;
            boolean touches=false;
            for(var box:state.getCollisionShape(guide.level(),cursor).toAabbs())if(box.move(cursor).intersects(area)){touches=true;break;}
            if(touches)try{
                if(!(boolean)ACCESS.open.invokeExact(guide,cursor.immutable()))return false;
            }catch(Throwable failure){throw new IllegalStateException("Apertura de puertas CIT",failure);}
        }
        return true;
    }
}
