package gt.edu.uvg.universityguide;

import com.mojang.authlib.GameProfile;
import gt.edu.uvg.universityguide.block.GuideBarrierBlock;
import gt.edu.uvg.universityguide.entity.GuideEntity;
import gt.edu.uvg.universityguide.entity.navigation.GuideRouteRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

/** Escenarios reales de navegación y acceso a barreras. */
@GameTestHolder(UniversityGuideMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuideNavigationGameTests {
    // Dos tramos de ancho dos, descanso 2x2 y barandal: bajada con giro real.
    @GameTest(template="empty",timeoutTicks=500) public static void guideDescendsTwoWideCornerAndRailing(GameTestHelper h){
        descendCorner(h,false);
    }
    @GameTest(template="empty",timeoutTicks=500) public static void guideDescendsShapedStairCorner(GameTestHelper h){
        descendCorner(h,true);
    }
    private static void descendCorner(GameTestHelper h,boolean winding){
        floor(h);
        for(int x=1;x<=2;x++){
            for(int z=2;z<=3;z++){
                int y=z-1;for(int below=1;below<y;below++)h.setBlock(new BlockPos(x,below,z),Blocks.STONE);
                h.setBlock(new BlockPos(x,y,z),Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
            }
            for(int z=4;z<=5;z++)for(int y=1;y<=2;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        }
        for(int x=3;x<=6;x++)for(int z=4;z<=5;z++){
            int top=Math.min(x,5);for(int y=1;y<top;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
            if(x<5)h.setBlock(new BlockPos(x,top,z),Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST));
        }
        if(winding){
            for(int x=1;x<=2;x++)for(int z=4;z<=5;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING,z==4?Direction.SOUTH:Direction.EAST));
        }
        for(int z=1;z<=5;z++)h.setBlock(new BlockPos(0,Math.min(Math.max(z,1),3),z),Blocks.IRON_BARS);
        for(int x=1;x<=6;x++){
            int y=x<3?3:Math.min(x+1,5);
            h.setBlock(new BlockPos(x,y,6),Blocks.IRON_BARS);
            if(x>=3)h.setBlock(new BlockPos(x,y,3),Blocks.IRON_BARS);
        }
        var g=guide(h,6,5,4);var end=feet(h,1,1,1);var path=g.getNavigation().createPath(BlockPos.containing(end),0);
        h.assertTrue(path!=null&&path.canReach(),"Finds descending L stair route");g.getNavigation().moveTo(path,1.1);
        h.startSequence().thenWaitUntil(()->h.assertTrue(g.position().distanceTo(end)<.45,"Descends corner: "+g.position().subtract(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)))
                +" done="+g.getNavigation().isDone()+" next="+(path.isDone()?"end":path.getNextEntityPos(g).subtract(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO))))))
                .thenExecute(g::discard).thenSucceed();
    }
    // Una nueva búsqueda vuelve a leer obstáculos y barreras, sin reutilizar el mundo anterior.
    @GameTest(template="empty") public static void repeatedSearchesRespectChangedObstacles(GameTestHelper h){
        floor(h);var g=guide(h,1,1,3);var end=h.absolutePos(new BlockPos(6,1,3));
        var first=g.getNavigation().createPath(end,0);
        h.assertTrue(first!=null&&first.canReach(),"Initial route exists");
        for(int z=0;z<8;z++)for(int y=1;y<4;y++)h.setBlock(new BlockPos(3,y,z),ModContent.GUIDE_BARRIER.get());
        var blocked=g.getNavigation().createPath(end,0);
        h.assertTrue(blocked==null||!blocked.canReach(),"New barrier invalidates cached search");
        for(int z=0;z<8;z++)for(int y=1;y<4;y++)h.setBlock(new BlockPos(3,y,z),Blocks.AIR);
        var restored=g.getNavigation().createPath(end,0);
        h.assertTrue(restored!=null&&restored.canReach(),"Removing barrier restores search");g.discard();h.succeed();
    }
    // Integración opcional: prueba todos los tamaños, etiquetas, bisagras y orientaciones.
    @GameTest(template="empty",timeoutTicks=200) public static void citDoorsArePlannedAsOpenablePassages(GameTestHelper h){
        var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        for(String name:new String[]{"cit_short_door_test","cit_wide_door_test","clear_short_door_test","clear_wide_door_test"}){
            var block=registry.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("posted_signage",name));
            if(block==Blocks.AIR){h.succeed();return;}
            for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true}){
                floor(h);var root=new BlockPos(3,1,3);var abs=h.absolutePos(root);var tangent=facing.getClockWise();
                boolean wide=name.contains("wide");
                var state=block.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,facing);
                for(var property:state.getProperties())if(property.getName().equals("right_hinge"))state=state.setValue((net.minecraft.world.level.block.state.properties.BooleanProperty)property,right);
                for(int n=-3;n<=4;n++)if(n<0||n>=(wide?2:1))for(int y=0;y<3;y++)h.setBlock(root.relative(tangent,n).above(y),Blocks.STONE);
                h.getLevel().setBlockAndUpdate(abs,state);block.setPlacedBy(h.getLevel(),abs,state,null,net.minecraft.world.item.ItemStack.EMPTY);
                var start=root.relative(facing,2);var end=h.absolutePos(root.relative(facing.getOpposite(),2));
                var g=guide(h,start.getX(),start.getY(),start.getZ());
                var path=g.getNavigation().createPath(end,0);
                h.assertTrue(path!=null&&path.canReach(),"Closed CIT doorway is reachable: "+name+" "+facing+" right="+right);
                boolean through=false;for(int i=0;i<path.getNodeCount();i++)if(path.getNode(i).asBlockPos().equals(abs))through=true;
                h.assertTrue(through,"Uses doorway cell: "+name+" "+facing+" right="+right);
                h.assertTrue(!h.getLevel().getBlockState(abs).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN),"Planning does not open doors");
                g.discard();h.getLevel().removeBlock(abs,false);
            }
        }h.succeed();
    }
    // El guía cruza una puerta real, abierta por su controlador, sin atravesar la hoja cerrada.
    @GameTest(template="empty",timeoutTicks=240) public static void guideOpensAndCrossesCitDoor(GameTestHelper h){
        crossDoor(h,"clear_short_door_test",Direction.NORTH,false);
    }
    @GameTest(template="empty",timeoutTicks=240) public static void guideOpensAndCrossesWideCitDoor(GameTestHelper h){
        crossDoor(h,"cit_wide_door_test",Direction.EAST,true);
    }
    private static void crossDoor(GameTestHelper h,String name,Direction facing,boolean right){
        floor(h);var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("posted_signage",name));
        if(block==Blocks.AIR){h.succeed();return;}
        var root=new BlockPos(3,1,3);var abs=h.absolutePos(root);
        var tangent=facing.getClockWise();
        for(int n=-3;n<=4;n++)if(n<0||n>=(name.contains("wide")?2:1))for(int y=0;y<3;y++)h.setBlock(root.relative(tangent,n).above(y),Blocks.STONE);
        var state=block.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,facing);
        for(var property:state.getProperties())if(property.getName().equals("right_hinge"))state=state.setValue((net.minecraft.world.level.block.state.properties.BooleanProperty)property,right);
        h.getLevel().setBlockAndUpdate(abs,state);
        block.setPlacedBy(h.getLevel(),abs,state,null,net.minecraft.world.item.ItemStack.EMPTY);
        var start=root.relative(facing,2);var finish=root.relative(facing.getOpposite(),2);
        var g=guide(h,start.getX(),1,start.getZ());var end=feet(h,finish.getX(),1,finish.getZ());var path=g.getNavigation().createPath(BlockPos.containing(end),0);
        h.assertTrue(path!=null&&path.canReach(),"Complete route through door");g.getNavigation().moveTo(path,1.1);
        h.startSequence().thenWaitUntil(()->h.assertTrue(g.position().distanceTo(end)<.4,"Crosses doorway: "+g.position().subtract(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)))
                +" open="+h.getLevel().getBlockState(abs).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)
                +" done="+g.getNavigation().isDone()+" next="+(path.isDone()?"end":path.getNextEntityPos(g).subtract(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO))))))
                .thenExecute(()->{h.assertTrue(h.getLevel().getBlockState(abs).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN),"Door really opened");g.discard();})
                .thenSucceed();
    }
    // Un bloque en el giro mantiene la puerta cerrada y la ruta deja de atravesarla.
    @GameTest(template="empty") public static void obstructedCitDoorIsNotAFreePassage(GameTestHelper h){
        floor(h);var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("posted_signage","clear_short_door_test"));
        if(block==Blocks.AIR){h.succeed();return;}
        var root=new BlockPos(3,1,3);var abs=h.absolutePos(root);var state=block.defaultBlockState();
        h.getLevel().setBlockAndUpdate(abs,state);block.setPlacedBy(h.getLevel(),abs,state,null,net.minecraft.world.item.ItemStack.EMPTY);
        h.setBlock(new BlockPos(3,1,2),Blocks.STONE);
        h.assertTrue(gt.edu.uvg.universityguide.entity.navigation.GuideDoors.shape(h.getLevel(),abs)==null,"Blocked swing cannot be planned open");
        var g=guide(h,3,1,4);
        h.assertTrue(!gt.edu.uvg.universityguide.entity.navigation.GuideDoors.openAhead(g,feet(h,3,1,3)),"Opening fails safely");
        h.assertTrue(h.getBlockState(new BlockPos(3,1,2)).is(Blocks.STONE),"Obstacle is preserved");
        h.assertTrue(!h.getLevel().getBlockState(abs).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN),"Door remains closed");
        g.discard();h.succeed();
    }
    // Centrado transversal en los cuatro sentidos sin cambiar el punto de subida.
    @GameTest(template="empty") public static void stairWaypointsCenterAcrossTheFlight(GameTestHelper h){
        for(Direction facing:Direction.Plane.HORIZONTAL){
            floor(h);var center=new BlockPos(3,1,3);var right=facing.getClockWise();
            for(int n=-1;n<=1;n++){
                for(int lane=0;lane<2;lane++)h.setBlock(center.relative(facing,n).relative(right,lane),
                        Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,facing));
                for(int y=0;y<3;y++)for(int side:new int[]{-1,2})h.setBlock(center.relative(facing,n).relative(right,side).above(y),Blocks.STONE);
            }
            var node=h.absolutePos(center.above());var base=Vec3.atBottomCenterOf(node);
            var point=GuideRouteRules.comfortableWaypoint(h.getLevel(),node,base.y,.6,1.8);
            h.assertTrue(point!=null,"Stair waypoint exists");
            var delta=point.subtract(base);
            h.assertTrue(delta.x*right.getStepX()+delta.z*right.getStepZ()>.15,"Moves away from stair side wall: "+facing);
            h.assertTrue(Math.abs(delta.x*facing.getStepX()+delta.z*facing.getStepZ())<1e-6,"Never shifts forward across a riser");
            h.assertTrue(route(h,base,point),"Lateral point remains supported");
        }
        h.succeed();
    }
    // Subida y bajada real a un piso superior por una escalera ancha con paredes.
    @GameTest(template="empty",timeoutTicks=400) public static void guideUsesCenterLaneToReachUpperFloor(GameTestHelper h){
        floor(h);
        for(int x=0;x<=4;x++)for(int z=0;z<8;z++)if(x==0||x==4)
            for(int y=1;y<7;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        for(int x=1;x<=3;x++){
            for(int n=0;n<3;n++){
                for(int y=1;y<=n;y++)h.setBlock(new BlockPos(x,y,2+n),Blocks.STONE);
                h.setBlock(new BlockPos(x,1+n,2+n),Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
            }
            for(int z=5;z<=6;z++)h.setBlock(new BlockPos(x,3,z),Blocks.STONE);
        }
        var g=guide(h,1,1,1);var top=feet(h,1,4,6);var bottom=feet(h,1,1,1);
        var path=g.getNavigation().createPath(BlockPos.containing(top),0);
        h.assertTrue(path!=null&&path.canReach(),"Finds full route to upper floor");
        boolean central=false;for(int i=0;i<path.getNodeCount();i++){
            var n=path.getNode(i);if(n.x==h.absolutePos(new BlockPos(2,0,0)).getX()&&n.y>bottom.y)central=true;
        }
        h.assertTrue(central,"Prefers center lane of wide stair");g.getNavigation().moveTo(path,1.1);
        h.startSequence().thenWaitUntil(()->h.assertTrue(g.position().distanceTo(top)<.45,"Arrives upstairs"))
                .thenExecute(()->{
                    var down=g.getNavigation().createPath(BlockPos.containing(bottom),0);
                    h.assertTrue(down!=null&&down.canReach(),"Finds descending route");g.getNavigation().moveTo(down,1.1);
                }).thenWaitUntil(()->h.assertTrue(g.position().distanceTo(bottom)<.45,"Returns downstairs: "+g.position()+" done="+g.getNavigation().isDone()))
                .thenExecute(g::discard).thenSucceed();
    }
    // La ruta abandona el borde cuando hay una alternativa ancha y próxima.
    @GameTest(template="empty") public static void guidePrefersSpaceAwayFromWalls(GameTestHelper h){
        floor(h);
        for(int x=0;x<8;x++)for(int y=1;y<4;y++)for(int z:new int[]{0,6})h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var near=feet(h,3,1,1);var center=feet(h,3,1,3);
        h.assertTrue(GuideRouteRules.clearance(h.getLevel(),center,.6,1.8)>GuideRouteRules.clearance(h.getLevel(),near,.6,1.8),"Actual wall clearance increases toward center");
        var g=guide(h,1,1,1);var p=g.getNavigation().createPath(h.absolutePos(new BlockPos(6,1,1)),0);
        h.assertTrue(p!=null&&p.canReach(),"Wide route remains reachable");boolean leftEdge=false;
        for(int i=0;i<p.getNodeCount();i++)if(p.getNode(i).z>=h.absolutePos(new BlockPos(0,0,2)).getZ())leftEdge=true;
        h.assertTrue(leftEdge,"Prefers open lane rather than following wall for whole route");g.discard();h.succeed();
    }
    // Dentro de un pasillo de dos bloques desplaza el punto hacia el espacio central.
    @GameTest(template="empty") public static void comfortableWaypointsStayInsideSafeCorridors(GameTestHelper h){
        floor(h);for(int x=0;x<8;x++)for(int y=1;y<4;y++)for(int z:new int[]{0,3})h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var base=feet(h,3,1,1);var node=h.absolutePos(new BlockPos(3,1,1));
        var point=GuideRouteRules.comfortableWaypoint(h.getLevel(),node,base.y,.6,1.8);
        h.assertTrue(point!=null&&point.z>base.z+.15,"Moves away from nearby wall");
        h.assertTrue(route(h,base,point),"Adjustment remains physically traversable");h.succeed();
    }
    // Comprueba progreso real continuo en una ruta recta, sin pedir saltos.
    @GameTest(template="empty",timeoutTicks=160) public static void guideKeepsMovingAcrossFlatWaypoints(GameTestHelper h){
        floor(h);var g=guide(h,1,1,3);var end=feet(h,6,1,3);
        var path=g.getNavigation().createPath(BlockPos.containing(end),0);h.assertTrue(path!=null&&path.canReach(),"Has complete route");
        g.getNavigation().moveTo(path,1.1);
        h.startSequence().thenWaitUntil(()->h.assertTrue(g.position().distanceTo(end)<.4,"Reaches destination without stalled navigation"))
                .thenExecute(g::discard).thenSucceed();
    }
    private static void floor(GameTestHelper h) {
        for (int x=0;x<8;x++) for(int z=0;z<8;z++) {
            h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            for(int y=1;y<7;y++) h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
    }
    // Un giro estrecho no adelanta el punto hasta poder doblar sin chocar.
    @GameTest(template="empty",timeoutTicks=240) public static void guideRoundsNarrowCornerWithoutStopping(GameTestHelper h){
        floor(h);
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)if(!(z==1&&x>=1&&x<=6)&&!(x==6&&z>=1&&z<=6))
            for(int y=1;y<4;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var g=guide(h,1,1,1);var end=feet(h,6,1,6);var p=g.getNavigation().createPath(BlockPos.containing(end),0);
        h.assertTrue(p!=null&&p.canReach(),"Corner has a complete route");g.getNavigation().moveTo(p,1.1);
        h.startSequence().thenWaitUntil(()->h.assertTrue(g.position().distanceTo(end)<.4,"Rounds corner and reaches target: "+g.position()))
                .thenExecute(g::discard).thenSucceed();
    }
    private static Vec3 feet(GameTestHelper h,int x,double y,int z) {
        return Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)).add(x+.5,y,z+.5);
    }
    private static boolean route(GameTestHelper h,Vec3 a,Vec3 b) {
        return GuideRouteRules.canTraverse(h.getLevel(),a,b,.6,1.8);
    }
    private static GuideEntity guide(GameTestHelper h,int x,double y,int z) {
        GuideEntity g=ModContent.GUIDE.get().create(h.getLevel());
        Vec3 p=feet(h,x,y,z);g.moveTo(p.x,p.y,p.z,0,0);g.setOnGround(true);
        h.getLevel().addFreshEntity(g);return g;
    }

    // Un obstáculo completo obliga a buscar un rodeo.
    @GameTest(template="empty")
    public static void fullBlocksAndFurnitureAreNotSteps(GameTestHelper h) {
        floor(h);h.setBlock(new BlockPos(3,1,3),Blocks.STONE);
        h.assertTrue(!route(h,feet(h,2,1,3),feet(h,3,2,3)),"Cannot climb a full block");
        h.assertTrue(!route(h,feet(h,3,2,3),feet(h,4,1,3)),"Cannot drop a full block");
        h.setBlock(new BlockPos(3,1,3),Blocks.CHEST);
        h.assertTrue(!route(h,feet(h,2,1,3),feet(h,3,1.875,3)),"Cannot climb furniture");
        GuideEntity g=guide(h,1,1,3);var path=g.getNavigation().createPath(h.absolutePos(new BlockPos(6,1,3)),0);
        h.assertTrue(path!=null&&path.canReach(),"Finds a route around furniture");g.discard();h.succeed();
    }

    // La forma de los peldaños determina qué lado permite ascender.
    @GameTest(template="empty")
    public static void stairsAndSlabsRespectRealGeometry(GameTestHelper h) {
        for(Direction d:Direction.Plane.HORIZONTAL) {
            floor(h);BlockPos center=new BlockPos(3,1,3);
            h.setBlock(center,Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,d));
            Vec3 low=feet(h,3-d.getStepX(),1,3-d.getStepZ()), top=feet(h,3,2,3);
            h.assertTrue(route(h,low,top),"Ascends stair from low face: "+d);
            h.assertTrue(route(h,top,low),"Descends stair to low face: "+d);
            Vec3 high=feet(h,3+d.getStepX(),1,3+d.getStepZ());
            h.assertTrue(!route(h,high,top),"Rejects high face: "+d);
        }
        floor(h);h.setBlock(new BlockPos(3,1,3),Blocks.STONE_SLAB);h.setBlock(new BlockPos(4,1,3),Blocks.STONE);
        h.assertTrue(route(h,feet(h,2,1,3),feet(h,4,2,3)),"Ascends slab to landing");
        h.assertTrue(route(h,feet(h,4,2,3),feet(h,2,1,3)),"Descends slab from landing");h.succeed();
    }

    // La barrera afecta pies, cabeza y diagonales, pero no otros pisos.
    @GameTest(template="empty")
    public static void barrierOccupiesOnlyItsCell(GameTestHelper h) {
        floor(h);var barrier=ModContent.GUIDE_BARRIER.get();
        BlockPos p=new BlockPos(3,1,3);h.setBlock(p,barrier);
        h.assertTrue(!route(h,feet(h,2,1,3),feet(h,4,1,3)),"Feet barrier blocks");
        h.setBlock(p,Blocks.AIR);h.setBlock(p.above(),barrier);
        h.assertTrue(!route(h,feet(h,2,1,3),feet(h,4,1,3)),"Head barrier blocks");
        h.assertTrue(!route(h,feet(h,2,1,2),feet(h,4,1,4)),"Diagonal cannot cross barrier");
        h.assertTrue(barrier.defaultBlockState().getCollisionShape(h.getLevel(),h.absolutePos(p)).isEmpty(),"No physical collision");
        for(int x=1;x<7;x++)h.setBlock(new BlockPos(x,3,3),Blocks.STONE);
        h.assertTrue(route(h,feet(h,2,4,3),feet(h,4,4,3)),"Upper floor remains accessible");
        h.setBlock(p.above(),Blocks.AIR);
        h.assertTrue(route(h,feet(h,2,1,3),feet(h,4,1,3)),"Removing barrier restores route");h.succeed();
    }

    // La selección y la edición exigen Creativo y operador a la vez.
    @GameTest(template="empty")
    public static void barrierPermissionsAndPersistence(GameTestHelper h) {
        floor(h);var block=ModContent.GUIDE_BARRIER.get();BlockPos pos=h.absolutePos(new BlockPos(3,1,3));
        h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        for(boolean creative:new boolean[]{false,true})for(boolean op:new boolean[]{false,true}) {
            Player player=new Player(h.getLevel(),pos,0,new GameProfile(UUID.randomUUID(),"barrier test")) {
                @Override public boolean isCreative(){return creative;}
                @Override public boolean isSpectator(){return false;}
                @Override public boolean hasPermissions(int permission){return op&&permission<=2;}
            };
            h.assertTrue(GuideBarrierBlock.canEdit(player)==(creative&&op),"Access matches permissions");
            h.assertTrue(!block.defaultBlockState().getShape(h.getLevel(),pos,CollisionContext.of(player)).isEmpty()==(creative&&op),"Selection matches permissions");
            if(!creative||!op)h.assertTrue(!block.onDestroyedByPlayer(block.defaultBlockState(),h.getLevel(),pos,player,false,block.defaultBlockState().getFluidState()),"Rejects unauthorized break");
        }
        var entity=h.getLevel().getBlockEntity(pos);var tag=entity.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos,block.defaultBlockState(),tag,h.getLevel().registryAccess());
        h.assertTrue(restored!=null&&restored.getType()==ModContent.GUIDE_BARRIER_ENTITY.get(),"Barrier survives serialization");h.succeed();
    }

    // El guía recorre una escalera completa caminando y vuelve por ella.
    @GameTest(template="empty",timeoutTicks=400)
    public static void guideWalksUpAndDownStairs(GameTestHelper h) {
        floor(h);
        for(int i=0;i<3;i++)h.setBlock(new BlockPos(3+i,1+i,3),Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST));
        h.setBlock(new BlockPos(6,3,3),Blocks.STONE);
        GuideEntity g=guide(h,2,1,3);Vec3 top=feet(h,6,4,3), bottom=feet(h,2,1,3);
        var path=g.getNavigation().createPath(BlockPos.containing(top),0);
        h.assertTrue(path!=null&&path.canReach(),"Exact path to upper floor exists");
        g.getNavigation().moveTo(path,1);
        h.startSequence().thenWaitUntil(()->h.assertTrue(g.position().distanceTo(top)<.5,"Arrives upstairs: "+g.position()+" target="+top+" done="+g.getNavigation().isDone()+" grounded="+g.onGround()+" step="+g.maxUpStep()))
                .thenExecute(()->h.assertTrue(g.getNavigation().moveTo(g.getNavigation().createPath(BlockPos.containing(bottom),0),1),"Path downstairs exists"))
                .thenWaitUntil(()->h.assertTrue(g.position().distanceTo(bottom)<.5,"Arrives downstairs"))
                .thenExecute(g::discard).thenSucceed();
    }

    // El cuerpo no continúa por una barrera colocada durante el recorrido.
    @GameTest(template="empty",timeoutTicks=100)
    public static void newlyPlacedBarrierStopsMovingGuide(GameTestHelper h) {
        floor(h);GuideEntity g=guide(h,2,1,3);Vec3 end=feet(h,6,1,3);
        g.getNavigation().moveTo(end.x,end.y,end.z,1);
        h.runAfterDelay(5,()->{
            BlockPos p=g.blockPosition();h.getLevel().setBlockAndUpdate(p,ModContent.GUIDE_BARRIER.get().defaultBlockState());
            Vec3 stopped=g.position();
            h.runAfterDelay(15,()->{
                h.assertTrue(g.position().distanceTo(stopped)<.05,"Remains inside newly placed barrier");
                h.getLevel().removeBlock(p,false);g.getNavigation().moveTo(end.x,end.y,end.z,1);
                h.runAfterDelay(20,()->{
                    h.assertTrue(g.position().distanceTo(stopped)>.4,"Moves again after removal");g.discard();h.succeed();
                });
            });
        });
    }

    // Los giros se hacen en descansos, sin recortar la esquina del piso.
    @GameTest(template="empty",timeoutTicks=400)
    public static void guideUsesLandingAndTurns(GameTestHelper h) {
        floor(h);
        h.setBlock(new BlockPos(2,1,3),Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST));
        h.setBlock(new BlockPos(3,2,3),Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST));
        h.setBlock(new BlockPos(4,2,3),Blocks.STONE);
        h.setBlock(new BlockPos(4,3,4),Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
        h.setBlock(new BlockPos(4,4,5),Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
        h.setBlock(new BlockPos(4,4,6),Blocks.STONE);
        GuideEntity g=guide(h,1,1,3);Vec3 top=feet(h,4,5,6);
        var path=g.getNavigation().createPath(BlockPos.containing(top),0);
        h.assertTrue(path!=null&&path.canReach(),"Finds staircase with landing");g.getNavigation().moveTo(path,1);
        h.startSequence().thenWaitUntil(()->h.assertTrue(g.position().distanceTo(top)<.5,"Reaches floor after turn: "+g.position()))
                .thenExecute(g::discard).thenSucceed();
    }

    // Una pared de barreras produce un destino inaccesible; una celda permite rodear.
    @GameTest(template="empty")
    public static void pathfinderRespectsBarriersAndDoors(GameTestHelper h) {
        floor(h);GuideEntity g=guide(h,1,1,3);BlockPos target=h.absolutePos(new BlockPos(6,1,3));
        h.setBlock(new BlockPos(3,1,3),ModContent.GUIDE_BARRIER.get());
        var path=g.getNavigation().createPath(target,0);
        h.assertTrue(path!=null&&path.canReach(),"Can route around one forbidden cell");
        for(int z=0;z<8;z++)for(int y=1;y<4;y++)h.setBlock(new BlockPos(3,y,z),ModContent.GUIDE_BARRIER.get());
        g.getNavigation().stop();path=g.getNavigation().createPath(target,0);
        h.assertTrue(path==null||!path.canReach(),"Cannot pass barrier wall");
        for(int y=1;y<4;y++)h.setBlock(new BlockPos(3,y,3),Blocks.AIR);
        h.setBlock(new BlockPos(3,1,3),Blocks.OAK_DOOR.defaultBlockState());
        h.setBlock(new BlockPos(3,2,3),Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        g.getNavigation().stop();path=g.getNavigation().createPath(target,0);
        h.assertTrue(path!=null&&path.canReach(),"Wooden doors remain routeable");g.discard();h.succeed();
    }
    // El guía aprovecha el hueco real, separado de la hoja vertical.
    @GameTest(template="empty")
    public static void openTrapdoorsUseTheirFreeSpace(GameTestHelper h) {
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.OAK_TRAPDOOR,Blocks.IRON_TRAPDOOR})
            for(Direction facing:Direction.Plane.HORIZONTAL)for(int height:new int[]{1,2})
                for(var half:net.minecraft.world.level.block.state.properties.Half.values()) {
                    floor(h);var local=new BlockPos(3,height,3);
                    var trapdoor=block.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING,facing)
                            .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,half)
                            .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN,true);
                    h.setBlock(local,trapdoor);
                    boolean alongX=facing.getAxis()==Direction.Axis.Z;
                    // En espacio abierto ahora puede preferir rodearla; aquí el corredor exige usar el hueco.
                    for(int n=0;n<8;n++)for(int y=1;y<4;y++)for(int side:new int[]{2,4})
                        h.setBlock(new BlockPos(alongX?n:side,y,alongX?side:n),Blocks.STONE);
                    GuideEntity g=guide(h,alongX?1:3,1,alongX?3:1);
                    var target=h.absolutePos(new BlockPos(alongX?6:3,1,alongX?3:6));
                    var path=g.getNavigation().createPath(target,0);
                    h.assertTrue(path!=null&&path.canReach(),"Path exists through free space");
                    var passage=h.absolutePos(new BlockPos(3,1,3));boolean passes=false;
                    for(int n=0;n<path.getNodeCount();n++)if(path.getNode(n).asBlockPos().equals(passage)) {
                        passes=true;var point=path.getEntityPosAtNode(g,n);
                        h.assertTrue(route(h,point,point),"Shifted waypoint fits the real collision shape");
                        double offset=alongX?Math.abs(point.z-(passage.getZ()+.5)):Math.abs(point.x-(passage.getX()+.5));
                        h.assertTrue(offset>.08,"Walks inside the free space instead of brushing the trapdoor");
                    }
                    h.assertTrue(passes,"Uses the trapdoor cell instead of excluding the whole block: "+facing+" height="+height);
                    g.discard();
                }
        h.succeed();
    }

    // Abrir una trampilla durante el recorrido reajusta el paso dentro del corredor.
    @GameTest(template="empty",timeoutTicks=240)
    public static void guidePassesWhenTrapdoorOpens(GameTestHelper h) {
        floor(h);
        for(int x=0;x<8;x++)for(int y=1;y<4;y++)for(int z:new int[]{2,4})h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        var local=new BlockPos(3,2,3);
        var closed=Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,net.minecraft.world.level.block.state.properties.Half.TOP)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING,Direction.NORTH);
        h.setBlock(local,closed);GuideEntity g=guide(h,1,1,3);Vec3 end=feet(h,6,1,3);
        var path=g.getNavigation().createPath(BlockPos.containing(end),0);
        h.assertTrue(path!=null&&path.canReach(),"Closed ceiling trapdoor leaves passage clear");
        g.getNavigation().moveTo(path,1);
        h.runAfterDelay(3,()->h.setBlock(local,closed.setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN,true)));
        h.startSequence().thenWaitUntil(()->h.assertTrue(g.position().distanceTo(end)<.5,"Continues to destination after trapdoor opens: "+g.position()))
                .thenExecute(g::discard).thenSucceed();
    }

    // Se puede pasar junto a la hoja, pero nunca atravesar su colisión.
    @GameTest(template="empty")
    public static void trapdoorPassageRespectsTheLeaf(GameTestHelper h) {
        floor(h);
        for(int z=0;z<8;z++)for(int y=1;y<4;y++)if(z!=3)h.setBlock(new BlockPos(3,y,z),Blocks.STONE);
        var floorTrapdoor=Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF,net.minecraft.world.level.block.state.properties.Half.TOP);
        h.setBlock(new BlockPos(3,0,3),floorTrapdoor);
        GuideEntity g=guide(h,1,1,3);var target=h.absolutePos(new BlockPos(6,1,3));
        var path=g.getNavigation().createPath(target,0);
        h.assertTrue(path!=null&&path.canReach(),"Closed flush floor trapdoor remains walkable");
        h.setBlock(new BlockPos(3,0,3),Blocks.STONE);
        h.setBlock(new BlockPos(3,1,3),Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN,true));
        path=g.getNavigation().createPath(target,0);
        h.assertTrue(path!=null&&path.canReach(),"Fits beside the open leaf in a one-block passage");
        h.setBlock(new BlockPos(3,1,3),Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING,Direction.EAST)
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN,true));
        path=g.getNavigation().createPath(target,0);
        h.assertTrue(path==null||!path.canReach(),"Cannot cross the vertical leaf itself");
        g.discard();h.succeed();
    }

    // Movimiento real en corredores estrechos, con ambas orientaciones de cada eje.
    @GameTest(template="empty",timeoutTicks=800)
    public static void guideWalksThroughOpenTrapdoorCorridors(GameTestHelper h) {
        final GuideEntity[] moving={null};final Vec3[] target={null};var sequence=h.startSequence();
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            sequence.thenExecute(()->{
                floor(h);boolean alongX=facing.getAxis()==Direction.Axis.Z;
                for(int n=0;n<8;n++)for(int y=1;y<4;y++)for(int side:new int[]{2,4})
                    h.setBlock(new BlockPos(alongX?n:side,y,alongX?side:n),Blocks.STONE);
                h.setBlock(new BlockPos(3,1,3),Blocks.IRON_TRAPDOOR.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING,facing)
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN,true));
                moving[0]=guide(h,alongX?1:3,1,alongX?3:1);target[0]=feet(h,alongX?6:3,1,alongX?3:6);
                var path=moving[0].getNavigation().createPath(BlockPos.containing(target[0]),0);
                h.assertTrue(path!=null&&path.canReach(),"Narrow passage has a route: "+facing);
                moving[0].getNavigation().moveTo(path,1);
            }).thenWaitUntil(()->h.assertTrue(moving[0].position().distanceTo(target[0])<.4,
                    "Passes open trapdoor: "+facing+" pos="+moving[0].position()+" done="+moving[0].getNavigation().isDone()))
                    .thenExecute(()->moving[0].discard());
        }
        sequence.thenSucceed();
    }

}
