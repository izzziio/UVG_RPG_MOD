package com.postedsignage;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

/** Pruebas de colocación, colisión, botonera, cierre seguro y botín. */
@GameTestHolder(PostedSignage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InteriorGameTests {
    private InteriorGameTests(){}
    private static BlockPos root(GameTestHelper h){return h.absolutePos(new BlockPos(3,2,3));}
    // Prepara los apoyos de cada pieza sin colocar el objeto con comandos.
    private static void supports(GameTestHelper h,BlockPos root,WallFixtureBlock block,Direction facing,boolean glass){
        for(int x=0;x<block.width;x++)for(int y=0;y<block.height;y++){
            var backing=root.relative(facing.getClockWise(),x).above(y).relative(facing.getOpposite());
            var state=glass?PostedSignage.CIT_GLASS.get().defaultBlockState().setValue(CitGlassBlock.AXIS,facing.getClockWise().getAxis()):Blocks.STONE.defaultBlockState();
            h.getLevel().setBlockAndUpdate(backing,state);
        }
    }
    @GameTest(template="empty") public static void wallInteriorsPlaceFromAllFourWallFaces(GameTestHelper h){
        var level=h.getLevel();var root=root(h);var player=h.makeMockPlayer(GameType.CREATIVE);
        for(var block:new WallFixtureBlock[]{PostedSignage.WALL_TV_BLOCK.get(),PostedSignage.FIRE_HOSE_CABINET_BLOCK.get(),PostedSignage.CAMPUS_RULES_POSTER_BLOCK.get()})
            for(Direction face:Direction.Plane.HORIZONTAL){
                supports(h,root,block,face,false);var support=root.relative(face.getOpposite());
                var hit=new BlockHitResult(support.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,support,false);
                var stack=new ItemStack(block);var result=((BlockItem)block.asItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit));
                h.assertTrue(result.consumesAction(),"Real item places complete fixture");
                for(int x=0;x<block.width;x++)for(int y=0;y<block.height;y++){
                    var p=root.relative(face.getClockWise(),x).above(y);var state=level.getBlockState(p);
                    h.assertTrue(state.is(block)&&state.getValue(FacilityMultiBlock.FACING)==face,"All parts follow clicked face");
                    var bounds=state.getShape(level,p).bounds();h.assertTrue(bounds.getXsize()<=1&&bounds.getYsize()<=1&&bounds.getZsize()<=1,"Bounds remain within footprint");
                }
                level.removeBlock(root,false);
                for(int x=0;x<block.width;x++)for(int y=0;y<block.height;y++)level.removeBlock(root.relative(face.getClockWise(),x).above(y).relative(face.getOpposite()),false);
            }
        h.succeed();
    }
    @GameTest(template="empty") public static void wallInteriorsSupportGlassAndRejectBlockedFootprints(GameTestHelper h){
        var level=h.getLevel();var root=root(h);var b=PostedSignage.WALL_TV_BLOCK.get();var face=Direction.NORTH;
        supports(h,root,b,face,true);var player=h.makeMockPlayer(GameType.CREATIVE);var support=root.south();
        var ctx=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,new ItemStack(b),new BlockHitResult(support.getCenter(),face,support,false));
        var state=b.getStateForPlacement(ctx);h.assertTrue(state!=null&&state.getValue(GlassSignMount.GLASS_MOUNTED),"TV mounts on connected glass");
        level.setBlockAndUpdate(root.east().above(),Blocks.STONE.defaultBlockState());
        h.assertTrue(b.getStateForPlacement(ctx)==null,"Occupied second row refuses placement");
        h.assertTrue(level.getBlockState(root.east().above()).is(Blocks.STONE),"Existing block preserved");
        h.succeed();
    }
    @GameTest(template="empty") public static void elevatorButtonsOpenAndDoorRefusesOccupiedClosure(GameTestHelper h){
        var level=h.getLevel();var root=root(h);var door=PostedSignage.ELEVATOR_DOOR_BLOCK.get();
        var s=door.defaultBlockState().setValue(FacilityMultiBlock.FACING,Direction.NORTH);level.setBlockAndUpdate(root,s);door.setPlacedBy(level,root,s,null,ItemStack.EMPTY);
        var button=PostedSignage.ELEVATOR_BUTTONS_BLOCK.get();var pos=root.east(3).above();level.setBlockAndUpdate(pos.south(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,button.defaultBlockState());
        h.assertTrue(root.equals(button.target(level,pos,Direction.NORTH)),"Button finds same-facing door");
        var player=h.makeMockPlayer(GameType.CREATIVE);button.useWithoutItem(level.getBlockState(pos),level,pos,player,new BlockHitResult(pos.getCenter(),Direction.NORTH,pos,false));
        for(int x=0;x<2;x++)for(int y=0;y<3;y++)h.assertTrue(level.getBlockState(root.east(x).above(y)).getValue(ElevatorDoorBlock.OPEN),"Button opens all six parts");
        var pig=net.minecraft.world.entity.EntityType.PIG.create(level);pig.setPos(root.getX()+.8,root.getY()+.1,root.getZ()+.5);level.addFreshEntity(pig);
        h.assertTrue(!door.setOpen(level,root,false,null)&&level.getBlockState(root).getValue(ElevatorDoorBlock.OPEN),"Occupied doorway does not close");
        pig.discard();h.assertTrue(door.setOpen(level,root,false,null),"Clears and closes safely");
        h.assertTrue(button.target(level,pos.above(4),Direction.NORTH)==null,"Button cannot call a different floor");
        h.succeed();
    }
    @GameTest(template="empty") public static void newAssembliesBreakOnceFromEveryCell(GameTestHelper h){
        var level=h.getLevel();var root=root(h);var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        for(var block:new InteriorAssemblyBlock[]{PostedSignage.WALL_TV_BLOCK.get(),PostedSignage.ELEVATOR_DOOR_BLOCK.get(),PostedSignage.FIRE_HOSE_CABINET_BLOCK.get(),PostedSignage.CAMPUS_RULES_POSTER_BLOCK.get()})
            for(var mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE})for(int selectedX=0;selectedX<block.width;selectedX++)for(int selectedY=0;selectedY<block.height;selectedY++){
                if(block instanceof WallFixtureBlock wall)supports(h,root,wall,Direction.NORTH,false);
                var s=block.defaultBlockState();level.setBlockAndUpdate(root,s);block.setPlacedBy(level,root,s,null,ItemStack.EMPTY);
                var before=new java.util.HashSet<>(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(root).inflate(10)));
                actor.setGameMode(mode);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                h.assertTrue(actor.gameMode.destroyBlock(root.east(selectedX).above(selectedY)),"Break selected part");
                int count=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(root).inflate(10)).stream()
                        .filter(e->!before.contains(e)&&e.getItem().is(block.asItem())).mapToInt(e->e.getItem().getCount()).sum();
                h.assertTrue(count==(mode==GameType.SURVIVAL?1:0),"Exactly one item in survival, none in creative");
                for(int x=0;x<block.width;x++)for(int y=0;y<block.height;y++)h.assertTrue(!level.getBlockState(root.east(x).above(y)).is(block),"No orphan parts");
            }
        h.succeed();
    }
}
