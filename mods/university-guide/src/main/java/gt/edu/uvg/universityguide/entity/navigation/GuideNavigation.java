package gt.edu.uvg.universityguide.entity.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.Vec3;

/** Navegación terrestre sin saltos ni atajos fuera de los peldaños. */
public final class GuideNavigation extends GroundPathNavigation {
    private boolean preferSpace=true;
    private boolean holdWaypoint;
    private final java.util.Map<BlockPos,java.util.Optional<Vec3>> searchFeet=new java.util.HashMap<>();
    private final java.util.Map<Transition,Boolean> searchEdges=new java.util.HashMap<>();
    private record Transition(BlockPos from,BlockPos to) {}
    public GuideNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
        setCanPassDoors(true);
        setCanFloat(false);
    }

    // Conserva la búsqueda vanilla y filtra cada transición por geometría.
    @Override protected PathFinder createPathFinder(int maximum) {
        nodeEvaluator = new WalkNodeEvaluator() {
            private final java.util.Map<BlockPos,Float> costs=new java.util.HashMap<>();
            @Override public Node getStart(){costs.clear();return super.getStart();}
            // Solo bloquea la celda cuando el cuerpo no cabe en su espacio libre.
            @Override public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob entity) {
                BlockPos pos = new BlockPos(x,y,z);
                if (GuideRouteRules.trapdoorWaypoint(context.level(), pos, getFloorLevel(pos),
                        entity.getBbWidth(), entity.getBbHeight()) == null) return PathType.BLOCKED;
                if(GuideDoors.isDoor(context.level().getBlockState(pos)))
                    return GuideDoors.shape(context.level(),pos)==null?PathType.BLOCKED:PathType.WALKABLE;
                return super.getPathTypeOfMob(context, x, y, z, entity);
            }

            @Override public int getNeighbors(Node[] output, Node source) {
                int count = super.getNeighbors(output, source), accepted = 0;
                for (int i = 0; i < count; i++) {
                    Node next = output[i];
                    Vec3 from = feet(source), to = feet(next);
                    if (from != null && to != null && searchEdges.computeIfAbsent(new Transition(source.asBlockPos(),next.asBlockPos()),
                            edge->GuideRouteRules.canTraverse(currentContext.level(), from, to, mob.getBbWidth(), mob.getBbHeight()))) {
                        // Coste acotado: prefiere espacio sin prohibir pasillos estrechos.
                        float penalty=preferSpace?costs.computeIfAbsent(next.asBlockPos(),p->(float)(1.6*Math.max(0,1-
                                GuideRouteRules.clearance(currentContext.level(),to,mob.getBbWidth(),mob.getBbHeight())/1.15))):0;
                        next.costMalus=Math.max(next.costMalus,penalty);
                        output[accepted++] = next;
                    }
                }
                return accepted;
            }

            private Vec3 feet(Node node) {
                return searchFeet.computeIfAbsent(node.asBlockPos(),pos->java.util.Optional.ofNullable(
                        GuideRouteRules.trapdoorWaypoint(currentContext.level(),pos,getFloorLevel(pos),mob.getBbWidth(),mob.getBbHeight()))).orElse(null);
            }
        };
        nodeEvaluator.setCanPassDoors(true);
        nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(nodeEvaluator, maximum) {
            @Override public Path findPath(net.minecraft.world.level.PathNavigationRegion region, Mob entity, java.util.Set<BlockPos> targets,
                    float range, int accuracy, float depth) {
                Path result;
                searchFeet.clear();searchEdges.clear();
                try{
                    preferSpace=true;
                    result=super.findPath(region,entity,targets,range,accuracy,depth);
                    if(result==null||!result.canReach()){
                        // Llegar tiene prioridad: segundo intento sin sesgo y con presupuesto acotado.
                        preferSpace=false;
                        Path basic=super.findPath(region,entity,targets,range,accuracy,Math.min(2F,depth*2));
                        if(basic!=null&&basic.canReach())result=basic;
                    }
                }finally{preferSpace=true;searchFeet.clear();searchEdges.clear();}
                if (result == null) return null;
                var nodes = new java.util.ArrayList<Node>();
                for (int i=0;i<result.getNodeCount();i++) nodes.add(result.getNode(i));
                var comfortable=new Vec3[nodes.size()];
                var calculated=new boolean[nodes.size()];
                // El movimiento usa los mismos puntos libres que la búsqueda.
                return new Path(nodes,result.getTarget(),result.canReach()) {
                    @Override public Vec3 getEntityPosAtNode(net.minecraft.world.entity.Entity actor, int index) {
                        Node node = getNode(index);
                        Vec3 point = GuideRouteRules.trapdoorWaypoint(actor.level(), node.asBlockPos(),
                                WalkNodeEvaluator.getFloorLevel(actor.level(),node.asBlockPos()),actor.getBbWidth(),actor.getBbHeight());
                        if(point==null)return super.getEntityPosAtNode(actor,index);
                        // Trampillas cambiadas durante el recorrido se evalúan de nuevo.
                        if(Math.abs(point.x-node.x-.5)<.001&&Math.abs(point.z-node.z-.5)<.001){
                            if(!calculated[index]){
                                comfortable[index]=GuideRouteRules.comfortableWaypoint(actor.level(),node.asBlockPos(),point.y,actor.getBbWidth(),actor.getBbHeight());
                                // Un punto cómodo también debe conectar con la salida del giro.
                                boolean stair=actor.level().getBlockState(node.asBlockPos()).is(net.minecraft.tags.BlockTags.STAIRS)
                                        ||actor.level().getBlockState(node.asBlockPos().below()).is(net.minecraft.tags.BlockTags.STAIRS);
                                if(stair&&comfortable[index]!=null&&index+1<getNodeCount()){
                                    var after=getNode(index+1);var exit=GuideRouteRules.trapdoorWaypoint(actor.level(),after.asBlockPos(),
                                            WalkNodeEvaluator.getFloorLevel(actor.level(),after.asBlockPos()),actor.getBbWidth(),actor.getBbHeight());
                                    if(exit==null||!GuideRouteRules.canTraverse(actor.level(),comfortable[index],exit,actor.getBbWidth(),actor.getBbHeight()))
                                        comfortable[index]=point;
                                }
                                calculated[index]=true;
                            }
                            if(comfortable[index]!=null)point=comfortable[index];
                        }
                        return new Vec3(point.x,node.y,point.z);
                    }
                };
            }
        };
    }

    // Cada punto se alcanza antes de girar: no corta esquinas.
    @Override protected void followThePath() {
        if (path == null || path.isDone()) return;
        Vec3 next = path.getNextEntityPos(mob);
        double floor = getGroundY(next);
        double tolerance = path.getNextNode().type == PathType.TRAPDOOR ? .035 : .15;
        if (Math.abs(mob.getX()-next.x) < tolerance && Math.abs(mob.getZ()-next.z) < tolerance
                && Math.abs(mob.getY()-floor) < 0.55){
            boolean safe=path.getNextNodeIndex()+1>=path.getNodeCount();
            if(!safe){
                var after=path.getEntityPosAtNode(mob,path.getNextNodeIndex()+1);
                safe=GuideRouteRules.canTraverse(level,mob.position(),new Vec3(after.x,getGroundY(after),after.z),mob.getBbWidth(),mob.getBbHeight());
            }
            if(safe)path.advance();
            else holdWaypoint=!mob.onGround()&&mob.getDeltaMovement().y<0;
        }
        // Adelanta puntos próximos solo con un segmento seguro y margen lateral.
        if(!path.isDone()&&mob.tickCount%4==0){
            int index=path.getNextNodeIndex();
            for(int ahead=Math.min(index+2,path.getNodeCount()-1);ahead>index;ahead--){
                boolean flat=true;
                for(int i=index;i<=ahead;i++)if(path.getNode(i).y!=path.getNode(index).y||path.getNode(i).type==PathType.TRAPDOOR)flat=false;
                if(!flat)continue;
                Vec3 target=path.getEntityPosAtNode(mob,ahead);target=new Vec3(target.x,getGroundY(target),target.z);
                // No encadena atajos hasta el final de una ruta larga ni muestrea tramos lejanos.
                if(mob.position().distanceToSqr(target)>6.25)continue;
                if(GuideRouteRules.canTraverse(level,mob.position(),target,mob.getBbWidth()+.3,mob.getBbHeight())){
                    path.setNextNodeIndex(ahead);break;
                }
            }
        }
        doStuckDetection(mob.position());
    }

    @Override public boolean canCutCorner(PathType type) { return false; }

    // Una barrera nueva detiene inmediatamente una ruta ya calculada.
    @Override public void tick() {
        if (GuideRouteRules.blocked(level, mob.getBoundingBox())) {
            stop();
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
            return;
        }
        holdWaypoint=false;
        super.tick();
        // Ya alcanzó el punto: espera el apoyo en vez de volver a subir hacia su centro.
        if(holdWaypoint){
            mob.getMoveControl().setWantedPosition(mob.getX(),mob.getY(),mob.getZ(),0);
            mob.setZza(0);mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);
        }
    }
}
