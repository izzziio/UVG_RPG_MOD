package gt.edu.uvg.universityguide.entity.navigation;

import gt.edu.uvg.universityguide.block.GuideBarrierBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Comprueba el suelo real y el volumen del guía entre dos puntos. */
public final class GuideRouteRules {
    private static final double EPS = 0.001;
    private GuideRouteRules() {}

    private record Surface(double y, boolean step) {}
    // Mide el espacio lateral real, sin contar el suelo ni impedir puertas transitables.
    public static double clearance(BlockGetter level,Vec3 feet,double width,double height){
        double best=1.4;
        for(var pos:BlockPos.betweenClosed(Mth.floor(feet.x-1.7),Mth.floor(feet.y+EPS),Mth.floor(feet.z-1.7),
                Mth.floor(feet.x+1.7),Mth.floor(feet.y+height-EPS),Mth.floor(feet.z+1.7))){
            var state=level.getBlockState(pos);if(state.is(BlockTags.WOODEN_DOORS))continue;
            if(state.getBlock() instanceof GuideBarrierBlock){
                var box=new AABB(pos);best=Math.min(best,horizontalGap(box,feet,width));
            }
            for(var local:state.isAir()?java.util.List.<AABB>of():collision(level,pos)){
                var box=local.move(pos);if(box.maxY<=feet.y+EPS||box.minY>=feet.y+height-EPS)continue;
                // El siguiente peldaño es apoyo transitable, no una pared lateral.
                if((state.is(BlockTags.STAIRS)&&box.maxY<=feet.y+1.001)
                        ||(state.is(BlockTags.SLABS)&&box.maxY<=feet.y+.501))continue;
                best=Math.min(best,horizontalGap(box,feet,width));
            }
        }
        return Math.max(0,best);
    }
    private static double horizontalGap(AABB box,Vec3 feet,double width){
        double dx=Math.max(Math.max(box.minX-feet.x,feet.x-box.maxX),0);
        double dz=Math.max(Math.max(box.minZ-feet.z,feet.z-box.maxZ),0);
        return Math.sqrt(dx*dx+dz*dz)-width/2;
    }
    // Ajuste pequeño; en peldaños solo transversal y en trampillas conserva el hueco preciso.
    public static Vec3 comfortableWaypoint(BlockGetter level,BlockPos node,double floor,double width,double height){
        var base=trapdoorWaypoint(level,node,floor,width,height);if(base==null)return null;
        if(Math.abs(base.x-node.getX()-.5)>.001||Math.abs(base.z-node.getZ()-.5)>.001)return base;
        net.minecraft.core.Direction.Axis stairAxis=null;
        for(var p:new BlockPos[]{node,node.below()}){
            var state=level.getBlockState(p);
            if(state.is(BlockTags.STAIRS)&&state.hasProperty(net.minecraft.world.level.block.StairBlock.FACING))
                stairAxis=state.getValue(net.minecraft.world.level.block.StairBlock.FACING).getAxis();
            else if(state.is(BlockTags.SLABS))return base;
        }
        Vec3 best=base;double score=clearance(level,base,width,height);
        for(double x:new double[]{-.2,0,.2})for(double z:new double[]{-.2,0,.2}){
            if(x==0&&z==0)continue;var candidate=base.add(x,0,z);
            // En escaleras solo centra a lo ancho, sin adelantar la subida o el descenso.
            if((stairAxis==net.minecraft.core.Direction.Axis.X&&x!=0)||(stairAxis==net.minecraft.core.Direction.Axis.Z&&z!=0))continue;
            double value=clearance(level,candidate,width,height)-.03*(Math.abs(x)+Math.abs(z));
            if(value>score+.01){best=candidate;score=value;}
        }
        return best==base||canTraverse(level,base,best,width,height)?best:base;
    }

    // Las barreras bloquean el cuerpo completo, sin afectar columnas vecinas.
    public static boolean blocked(BlockGetter level, AABB body) {
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(body.minX + EPS), Mth.floor(body.minY + EPS),
                Mth.floor(body.minZ + EPS), Mth.floor(body.maxX - EPS), Mth.floor(body.maxY - EPS), Mth.floor(body.maxZ - EPS))) {
            if (level.getBlockState(pos).getBlock() instanceof GuideBarrierBlock) return true;
        }
        return false;
    }

    // Centra el cuerpo en el hueco real que dejan las trampillas verticales.
    public static Vec3 trapdoorWaypoint(BlockGetter level, BlockPos node, double floor, double width, double height) {
        double radius = width / 2 + .002;
        double minX = node.getX()+radius, maxX = node.getX()+1-radius;
        double minZ = node.getZ()+radius, maxZ = node.getZ()+1-radius;
        boolean adjusted = false;
        for (BlockPos pos : BlockPos.betweenClosed(node.getX(), Mth.floor(floor+EPS), node.getZ(),
                node.getX(), Mth.floor(floor+height-EPS), node.getZ())) {
            var state = level.getBlockState(pos);
            if (!state.is(BlockTags.TRAPDOORS) || !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                    || !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) continue;
            for (AABB local : state.getCollisionShape(level, pos).toAabbs()) {
                AABB box = local.move(pos);
                if (box.getXsize() < .5) {
                    if (box.minX < node.getX()+.5) minX = Math.max(minX, box.maxX+radius);
                    else maxX = Math.min(maxX, box.minX-radius);
                } else if (box.getZsize() < .5) {
                    if (box.minZ < node.getZ()+.5) minZ = Math.max(minZ, box.maxZ+radius);
                    else maxZ = Math.min(maxZ, box.minZ-radius);
                }
                adjusted = true;
            }
        }
        if (!adjusted) return GuideDoors.waypoint(level,node,floor,width,height);
        if (minX > maxX || minZ > maxZ) return null;
        return new Vec3((minX+maxX)/2, floor, (minZ+maxZ)/2);
    }

    // Muestrea peldaños y esquinas con el ancho real de la entidad.
    public static boolean canTraverse(BlockGetter level, Vec3 from, Vec3 to, double width, double height) {
        double radius = width / 2 - EPS;
        // Una consulta síncrona comparte formas sin conservar datos del mundo entre ticks.
        var shapes=new java.util.HashMap<BlockPos,java.util.List<AABB>>();
        Surface previous = surface(level, from.x, from.z, from.y, radius,shapes);
        if (previous == null || Math.abs(previous.y - from.y) > 0.51) return false;
        int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) / 0.08));
        for (int i = 0; i <= samples; i++) {
            double t = (double)i / samples;
            double x = Mth.lerp(t, from.x, to.x), z = Mth.lerp(t, from.z, to.z);
            Surface next = surface(level, x, z, previous.y, radius,shapes);
            if (next == null) return false;
            double rise = next.y - previous.y;
            // Admite umbrales bajos del marco, sin permitir subir bloques completos.
            if (Math.abs(rise) > 0.501 || (Math.abs(rise) > .0625 && !next.step && !previous.step)) return false;
            AABB body = new AABB(x-radius, next.y+EPS, z-radius, x+radius, next.y+height-EPS, z+radius);
            if (!clear(level, body,shapes)) return false;
            previous = next;
        }
        return Math.abs(previous.y - to.y) < 0.51;
    }

    // Busca apoyo cercano; nunca convierte una caída en una ruta válida.
    private static Surface surface(BlockGetter level, double x, double z, double y, double radius,
            java.util.Map<BlockPos,java.util.List<AABB>> shapes) {
        Surface best = null;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(x-radius), Mth.floor(y-1.501), Mth.floor(z-radius),
                Mth.floor(x+radius), Mth.floor(y+0.501), Mth.floor(z+radius))) {
            var state = level.getBlockState(pos);
            for (AABB local : shapes.computeIfAbsent(pos.immutable(),p->collision(level,p))) {
                AABB box = local.move(pos);
                double top = box.maxY;
                if (box.maxX <= x-radius || box.minX >= x+radius
                        || box.maxZ <= z-radius || box.minZ >= z+radius
                        || top > y+0.501 || top < y-0.501) continue;
                if (best == null || top > best.y) best = new Surface(top, state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS));
            }
        }
        return best;
    }

    // Las puertas de madera conservan la apertura automática existente.
    private static boolean clear(BlockGetter level, AABB body,java.util.Map<BlockPos,java.util.List<AABB>> shapes) {
        if (blocked(level, body)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(body.minX), Mth.floor(body.minY), Mth.floor(body.minZ),
                Mth.floor(body.maxX), Mth.floor(body.maxY), Mth.floor(body.maxZ))) {
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return false;
            if (state.is(BlockTags.WOODEN_DOORS)) continue;
            for (AABB box : shapes.computeIfAbsent(pos.immutable(),p->collision(level,p))) {
                if (box.move(pos).intersects(body)) return false;
            }
        }
        return true;
    }
    // La puerta futura conserva marco y hoja; una apertura bloqueada conserva la colisión real.
    private static java.util.List<AABB> collision(BlockGetter level,BlockPos pos){
        var state=level.getBlockState(pos);
        if(GuideDoors.isDoor(state)||state.isAir()){
            var open=GuideDoors.shape(level,pos);if(open!=null)return open.toAabbs();
        }
        return state.getCollisionShape(level,pos).toAabbs();
    }
}
