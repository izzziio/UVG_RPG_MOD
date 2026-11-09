package com.postedsignage;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

// Prueba losas y herramientas de textura en un mundo real.
@GameTestHolder(PostedSignage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildingToolsGameTests {
    private static final BlockPos CENTER = new BlockPos(3,2,3);

    // Construye el contexto de colocación de la prueba.
    private static UseOnContext context(Player player, BlockPos pos, Direction face) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, pos, false));
    }

    // Comprueba orientación y límites de las losas.
    @GameTest(template="empty") public static void slabPlacementAndBounds(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var pos = helper.absolutePos(CENTER);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        for (var item : new net.minecraft.world.item.Item[]{PostedSignage.BRICK_VERTICAL_SLAB.get(), PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB.get()}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            var block = ((net.minecraft.world.item.BlockItem)item).getBlock();
            for (Direction face : Direction.Plane.HORIZONTAL) {
                var placement = new BlockPlaceContext(context(player, pos, face));
                var state = block.getStateForPlacement(placement);
                helper.assertTrue(state.getValue(VerticalSlabBlock.FACING) == face.getOpposite(), "Slab hugs clicked face");
                var shape = state.getShape(helper.getLevel(), placement.getClickedPos());
                var bounds = shape.bounds();
                helper.assertTrue(Math.abs(bounds.getXsize()*bounds.getYsize()*bounds.getZsize()-0.5) < 0.0001, "Slab occupies half a block");
                helper.assertTrue(bounds.getYsize() == 1, "Vertical slabs are full height");
                helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(VerticalSlabBlock.FACING) == face.getOpposite().getClockWise(), "Rotation follows collision geometry");
            }
        }
        helper.succeed();
    }

    // Dos objetos llenan una celda y un tercero se coloca en la celda siguiente.
    @GameTest(template="empty") public static void verticalSlabsPairInOneCell(GameTestHelper helper) {
        var level=helper.getLevel();var pos=helper.absolutePos(CENTER);var player=helper.makeMockPlayer(GameType.SURVIVAL);
        var slabs=new VerticalSlabBlock[]{PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get(),PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK.get()};
        for(var first:slabs)for(var second:slabs)for(Direction half:Direction.Plane.HORIZONTAL){
            level.setBlockAndUpdate(pos,first.defaultBlockState().setValue(VerticalSlabBlock.FACING,half));
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(second,3));
            // La cara interior se obtiene mediante un rayo real contra la media losa.
            var start=pos.getCenter().add(Vec3.atLowerCornerOf(half.getOpposite().getNormal()).scale(2));
            var end=pos.getCenter().add(Vec3.atLowerCornerOf(half.getNormal()).scale(.4));
            var hit=level.clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
            helper.assertTrue(hit.getBlockPos().equals(pos)&&hit.getDirection()==half.getOpposite(),"Ray targets inner half face");
            helper.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Second half places");
            var paired=level.getBlockState(pos);
            helper.assertTrue(paired.is(first)&&paired.getValue(VerticalSlabBlock.FACING)==half,"First half stays in place");
            var fill=second==slabs[0]?VerticalSlabBlock.Fill.BRICK:VerticalSlabBlock.Fill.QUARTZ;
            helper.assertTrue(paired.getValue(VerticalSlabBlock.FILL)==fill,"Second material stored");
            helper.assertTrue(player.getMainHandItem().getCount()==2,"Exactly one item consumed");
            helper.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(paired.getCollisionShape(level,pos),net.minecraft.world.phys.shapes.Shapes.block(),net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"Pair has full-block collision");
            helper.assertTrue(level.isEmptyBlock(pos.relative(half.getOpposite())),"Second slab shares original cell");
            helper.assertTrue(player.getMainHandItem().useOn(context(player,pos,half.getOpposite())).consumesAction(),"Third slab places next door");
            helper.assertTrue(level.getBlockState(pos).equals(paired),"Third slab cannot overwrite pair");
            helper.assertTrue(level.getBlockState(pos.relative(half.getOpposite())).is(second),"Third slab occupies next cell");
            level.removeBlock(pos,false);level.removeBlock(pos.relative(half.getOpposite()),false);
        }
        helper.succeed();
    }
    // La cara exterior conserva la colocación habitual en una celda vecina.
    @GameTest(template="empty") public static void verticalSlabsOnlyFillTheFreeHalf(GameTestHelper helper) {
        var level=helper.getLevel();var pos=helper.absolutePos(CENTER);var player=helper.makeMockPlayer(GameType.SURVIVAL);
        var brick=PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get();var quartz=PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK.get();
        for(Direction half:Direction.Plane.HORIZONTAL){
            var original=brick.defaultBlockState().setValue(VerticalSlabBlock.FACING,half);level.setBlockAndUpdate(pos,original);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(quartz,3));
            helper.assertTrue(player.getMainHandItem().useOn(context(player,pos,half)).consumesAction(),"Outer face places adjacent slab");
            helper.assertTrue(level.getBlockState(pos).equals(original)&&level.getBlockState(pos.relative(half)).is(quartz),"Occupied half is never replaced");
            level.removeBlock(pos.relative(half),false);
            var freeTop=pos.getCenter().add(half.getOpposite().getStepX()*.25,.5,half.getOpposite().getStepZ()*.25);
            var top=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(freeTop,Direction.UP,pos,false));
            helper.assertTrue(player.getMainHandItem().useOn(top).consumesAction(),"Top click over free half can pair");
            helper.assertTrue(level.getBlockState(pos).getValue(VerticalSlabBlock.FILL)==VerticalSlabBlock.Fill.QUARTZ,"Correct half filled from above");
            level.removeBlock(pos,false);
        }
        helper.succeed();
    }
    // Las combinaciones guardan sus materiales y devuelven los dos objetos.
    @GameTest(template="empty") public static void pairedSlabsPersistAndDropBothMaterials(GameTestHelper helper) {
        var level=helper.getLevel();var pos=helper.absolutePos(CENTER);
        var slabs=new VerticalSlabBlock[]{PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get(),PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK.get()};
        for(var first:slabs)for(Direction half:Direction.Plane.HORIZONTAL)for(var fill:VerticalSlabBlock.Fill.values()){
            var state=first.defaultBlockState().setValue(VerticalSlabBlock.FACING,half).setValue(VerticalSlabBlock.FILL,fill);
            var saved=net.minecraft.nbt.NbtUtils.writeBlockState(state);
            var restored=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),saved);
            helper.assertTrue(state.equals(restored),"Facing and both materials survive save/load");
            var drops=Block.getDrops(state,level,pos,null);
            int bricks=(first==slabs[0]?1:0)+(fill==VerticalSlabBlock.Fill.BRICK?1:0);
            int quartz=(first==slabs[1]?1:0)+(fill==VerticalSlabBlock.Fill.QUARTZ?1:0);
            helper.assertTrue(drops.stream().filter(i->i.is(slabs[0].asItem())).mapToInt(ItemStack::getCount).sum()==bricks,"Exact brick recovery");
            helper.assertTrue(drops.stream().filter(i->i.is(slabs[1].asItem())).mapToInt(ItemStack::getCount).sum()==quartz,"Exact quartz recovery");
            helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(VerticalSlabBlock.FILL)==fill,"Rotation preserves pair material");
            saved.getCompound("Properties").remove("fill");
            var legacy=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),saved);
            helper.assertTrue(legacy.getValue(VerticalSlabBlock.FILL)==VerticalSlabBlock.Fill.NONE,"Old single slabs remain single");
        }
        helper.succeed();
    }
    // Verifica las seis caras y la pérdida de soporte.
    @GameTest(template="empty") public static void coversAllSixFacesAndLoseSupport(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        for (Direction face : Direction.values()) {
            var dest = pos.relative(face);
            BlockState cover = PostedSignage.TEXTURE_COVER_BLOCK.get().defaultBlockState().setValue(TextureCoverBlock.FACING,face);
            helper.assertTrue(cover.canSurvive(level,dest), "Solid support accepted on " + face);
            level.setBlockAndUpdate(dest,cover);
            helper.assertTrue(cover.getCollisionShape(level,dest).isEmpty(), "Cover must not change movement collision");
        }
        level.destroyBlock(pos,false);
        for (Direction face : Direction.values()) helper.assertTrue(level.isEmptyBlock(pos.relative(face)), "Unsupported cover removed on " + face);
        helper.succeed();
    }

    // Muestreo, repintado y persistencia sin modificar ningún bloque.
    @GameTest(template="empty") public static void sampleApplyRepaintAndPersistence(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos, Blocks.BRICKS.defaultBlockState());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var tool = PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        player.setShiftKeyDown(true);
        helper.assertTrue(tool.useOn(context(player,pos,Direction.UP)).consumesAction(), "Sample source");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        helper.assertTrue(tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(), "Apply skin");
        var skin = TextureSkinEntity.find(level,pos,Direction.NORTH);
        helper.assertTrue(skin != null && skin.sample().is(Blocks.BRICKS) && skin.sampleFace()==Direction.UP, "Selected sample applied");
        helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && level.isEmptyBlock(pos.north()), "Support and neighboring air unchanged");
        var tag = new net.minecraft.nbt.CompoundTag(); skin.saveWithoutId(tag);
        var restored = new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level); restored.load(tag);
        helper.assertTrue(restored.sample().equals(skin.sample()) && restored.support().equals(pos) && restored.face()==Direction.NORTH, "Disk round trip");
        var synced = new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level);
        synced.getEntityData().assignValues(skin.getEntityData().getNonDefaultValues());
        helper.assertTrue(synced.sample().equals(skin.sample()) && synced.face()==skin.face(), "Tracking data includes texture and face");
        helper.assertTrue(skin.isIgnoringBlockTriggers(), "Skin cannot trigger pressure plates");
        helper.assertTrue(!skin.isPickable() && !skin.canBeCollidedWith() && !skin.isPushable(), "No hitbox or interaction target");
        player.setShiftKeyDown(true); tool.useOn(context(player,pos,Direction.EAST)); player.setShiftKeyDown(false);
        tool.useOn(context(player,pos,Direction.NORTH));
        helper.assertTrue(TextureSkinEntity.find(level,pos,Direction.NORTH)==skin && skin.sample().is(Blocks.STONE), "Repaint reuses skin");
        player.setShiftKeyDown(true); tool.useOn(context(player,pos,Direction.NORTH));
        helper.assertTrue(player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag().getString("SampleFace").equals("east"), "Sample existing painted face");
        level.destroyBlock(pos,false); skin.tick();
        helper.assertTrue(skin.isRemoved(), "Breaking support removes skin");
        helper.succeed();
    }

    // Caras adyacentes, vallas y permisos conservan el comportamiento original.
    @GameTest(template="empty") public static void skinsAllowNeighborsAndKeepPermissions(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        var player=helper.makeMockPlayer(GameType.SURVIVAL); var tool=PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
        helper.assertTrue(!tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(),"Selection required");
        player.setShiftKeyDown(true); tool.useOn(context(player,pos,Direction.UP)); player.setShiftKeyDown(false);
        for(Direction face:Direction.values()) helper.assertTrue(tool.useOn(context(player,pos,face)).consumesAction(),"Paint all faces");
        var paintedTool=player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.OAK_FENCE));
        helper.assertTrue(player.getMainHandItem().useOn(context(player,pos,Direction.NORTH)).consumesAction(),"Place actual fence against painted face");
        helper.assertTrue(level.getBlockState(pos.north()).is(Blocks.OAK_FENCE),"Fence occupies neighboring cell");
        helper.assertTrue(level.getBlockState(pos.north()).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH),"Fence connects to original support");
        player.setItemInHand(InteractionHand.MAIN_HAND,paintedTool);
        helper.assertTrue(tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(),"Paint beside an existing fence");
        level.setBlockAndUpdate(pos.east(),Blocks.STONE.defaultBlockState());
        helper.assertTrue(tool.useOn(context(player,pos.east(),Direction.NORTH)).consumesAction(),"Paint adjoining corner");
        helper.assertTrue(level.getBlockState(pos).getCollisionShape(level,pos).equals(Blocks.STONE.defaultBlockState().getCollisionShape(level,pos)),"Original collision unchanged");
        var adventure=helper.makeMockPlayer(GameType.ADVENTURE); GameType.ADVENTURE.updatePlayerAbilities(adventure.getAbilities());
        adventure.setItemInHand(InteractionHand.MAIN_HAND,paintedTool.copy());
        helper.assertTrue(!tool.useOn(context(adventure,pos,Direction.SOUTH)).consumesAction(),"Adventure cannot paint");
        helper.succeed();
    }

    // Giros guardados en las seis caras, incluso sobre bloques parciales.
    @GameTest(template="empty") public static void textureOrientationFollowsPlayerAndSurvivesReload(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos,Blocks.FURNACE.defaultBlockState());
        var player=helper.makeMockPlayer(GameType.SURVIVAL); var tool=PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
        player.setShiftKeyDown(true); tool.useOn(context(player,pos,Direction.NORTH)); player.setShiftKeyDown(false);
        Direction[] sides={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        int[] floor={2,3,0,1}, ceiling={0,3,2,1};
        for(Direction face:Direction.values()) for(int i=0;i<4;i++) {
            var destination=pos.relative(face); var side=sides[i];
            player.setPos(destination.getX()+.5+side.getStepX()*3,destination.getY()+1,destination.getZ()+.5+side.getStepZ()*3);
            helper.assertTrue(tool.useOn(context(player,pos,face)).consumesAction(),"Paint surface");
            var skin=TextureSkinEntity.find(level,pos,face); int expected=face==Direction.UP?floor[i]:face==Direction.DOWN?ceiling[i]:0;
            helper.assertTrue(skin.rotation()==expected,"Correct rotation");
            var tag=new net.minecraft.nbt.CompoundTag(); skin.saveWithoutId(tag);
            var restored=new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level); restored.load(tag);
            helper.assertTrue(restored.rotation()==expected,"Rotation survives reload");
        }
        for(var block:new Block[]{Blocks.STONE_SLAB,Blocks.STONE_STAIRS,PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get()}) {
            level.setBlockAndUpdate(pos,block.defaultBlockState());
            for(Direction face:Direction.values()) helper.assertTrue(tool.useOn(context(player,pos,face)).consumesAction(),"Partial blocks accept faces");
            helper.assertTrue(level.getBlockState(pos).is(block),"Partial support preserved");
        }
        helper.succeed();
    }

    // Los mundos anteriores recuperan las capas y liberan sus celdas.
    @GameTest(template="empty") public static void legacyCoversMigrate(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        var dest=pos.north(); var state=PostedSignage.TEXTURE_COVER_BLOCK.get().defaultBlockState().setValue(TextureCoverBlock.FACING,Direction.NORTH);
        level.setBlockAndUpdate(dest,state);
        var cover=(TextureCoverBlockEntity)level.getBlockEntity(dest); cover.setSample(Blocks.BRICKS.defaultBlockState(),Direction.UP,3);
        helper.runAfterDelay(3,()->{
            var skin=TextureSkinEntity.find(level,pos,Direction.NORTH);
            helper.assertTrue(level.isEmptyBlock(dest),"Legacy cell freed");
            helper.assertTrue(skin!=null && skin.sample().is(Blocks.BRICKS) && skin.rotation()==3,"Legacy sample and rotation preserved");
            helper.succeed();
        });
    }
    // Pulsa una cara exterior dentro de la mitad indicada.
    private static UseOnContext halfContext(Player player, BlockPos pos, Direction face, Direction half) {
        Vec3 center=pos.getCenter().add(half.getStepX()*.25,0,half.getStepZ()*.25);
        Vec3 hit=switch(face.getAxis()) {
            case X -> new Vec3(pos.getX()+(face==Direction.EAST?1:0),center.y,center.z);
            case Y -> new Vec3(center.x,pos.getY()+(face==Direction.UP?1:0),center.z);
            case Z -> new Vec3(center.x,center.y,pos.getZ()+(face==Direction.SOUTH?1:0));
        };
        return new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,face,pos,false));
    }
    private static void sampleTexture(Player player, BlockPos source, net.minecraft.server.level.ServerLevel level, Block block) {
        level.setBlockAndUpdate(source,block.defaultBlockState());player.setShiftKeyDown(true);
        PostedSignage.TEXTURE_APPLICATOR.get().useOn(context(player,source,Direction.UP));player.setShiftKeyDown(false);
    }
    // El recorte que consume el renderizador ocupa solo media celda.
    private static void assertHalfSkin(GameTestHelper h, TextureSkinEntity skin, Direction half) {
        h.assertTrue(skin!=null&&skin.half()==half,"Layer belongs to the selected half");double volume=0;
        for(var box:skin.surfaceBoxes()) {
            volume+=box.getXsize()*box.getYsize()*box.getZsize();
            boolean contained=switch(half) {
                case NORTH -> box.maxZ<=.5; case SOUTH -> box.minZ>=.5;
                case WEST -> box.maxX<=.5; case EAST -> box.minX>=.5;
                default -> false;
            };
            h.assertTrue(contained,"Rendered surface never extends onto the other slab");
        }
        h.assertTrue(Math.abs(volume-.5)<.00001,"Paint geometry covers exactly one half");
    }
    // Las caras compartidas admiten dos texturas independientes en todas las orientaciones.
    @GameTest(template="empty") public static void pairedSlabPaintingStaysOnClickedHalf(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(CENTER);var source=h.absolutePos(new BlockPos(1,2,1));
        var player=h.makeMockPlayer(GameType.SURVIVAL);var tool=PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
        for(var first:new VerticalSlabBlock[]{PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get(),PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK.get()})
            for(Direction facing:Direction.Plane.HORIZONTAL)for(Direction half:new Direction[]{facing,facing.getOpposite()})for(Direction face:Direction.values()) {
                if(face.getAxis()==facing.getAxis()&&face!=half)continue;
                var fill=first==PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get()?VerticalSlabBlock.Fill.QUARTZ:VerticalSlabBlock.Fill.BRICK;
                var paired=first.defaultBlockState().setValue(VerticalSlabBlock.FACING,facing).setValue(VerticalSlabBlock.FILL,fill);
                level.setBlockAndUpdate(pos,paired);sampleTexture(player,source,level,Blocks.STONE);
                var hit=halfContext(player,pos,face,half);
                h.assertTrue(tool.useOn(hit).consumesAction(),"Paint clicked half");
                var skin=TextureSkinEntity.findAt(level,pos,face,hit.getClickLocation());assertHalfSkin(h,skin,half);
                h.assertTrue(skin.sample().is(Blocks.STONE),"Selected material applied");
                if(face.getAxis()!=facing.getAxis()) {
                    var other=halfContext(player,pos,face,half.getOpposite());
                    h.assertTrue(TextureSkinEntity.findAt(level,pos,face,other.getClickLocation())==null,"Other half remains unpainted");
                    sampleTexture(player,source,level,Blocks.BRICKS);tool.useOn(other);
                    var second=TextureSkinEntity.findAt(level,pos,face,other.getClickLocation());assertHalfSkin(h,second,half.getOpposite());
                    h.assertTrue(second!=skin&&second.sample().is(Blocks.BRICKS)&&skin.sample().is(Blocks.STONE),"Painting second half preserves the first");
                    player.setShiftKeyDown(true);tool.useOn(hit);player.setShiftKeyDown(false);
                    var copied=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                            player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Sample"));
                    h.assertTrue(copied.is(Blocks.STONE),"Sampling picks the painted half under the cursor");
                }
                h.assertTrue(level.getBlockState(pos).equals(paired),"Painting preserves slab state and collision");
                for(var skinEntity:level.getEntitiesOfClass(TextureSkinEntity.class,new AABB(pos).inflate(.1)))skinEntity.discard();
            }
        h.succeed();
    }
    // Una pintura guardada antes del arreglo se divide sin perder la otra mitad.
    @GameTest(template="empty") public static void legacyPaintSplitsAndKeepsHalfAfterReload(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(CENTER);
        level.setBlockAndUpdate(pos,PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get().defaultBlockState()
                .setValue(VerticalSlabBlock.FACING,Direction.WEST).setValue(VerticalSlabBlock.FILL,VerticalSlabBlock.Fill.QUARTZ));
        var old=TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.BRICKS.defaultBlockState(),Direction.SOUTH,3);
        h.assertTrue(old.half()==null,"Legacy layer covers the whole face");
        var left=TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.STONE.defaultBlockState(),Direction.NORTH,1,Direction.WEST);
        var right=TextureSkinEntity.findAt(level,pos,Direction.UP,pos.getCenter().add(.25,.5,0));
        var cropped=TextureSkinSurface.uv(Direction.UP,.75,1,.5,right.rotation());
        h.assertTrue(Math.abs(cropped.u()-.5)<.00001&&Math.abs(cropped.v()-.75)<.00001,"Unpainted half retains the original full-face UV scale and rotation");
        h.assertTrue(old.isRemoved()&&right!=null&&right.sample().is(Blocks.BRICKS)&&right.rotation()==3,"Unpainted legacy half keeps its material and rotation");
        assertHalfSkin(h,left,Direction.WEST);assertHalfSkin(h,right,Direction.EAST);
        var saved=new net.minecraft.nbt.CompoundTag();left.saveWithoutId(saved);
        var restored=new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level);restored.load(saved);assertHalfSkin(h,restored,Direction.WEST);
        var synced=new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level);
        synced.getEntityData().assignValues(right.getEntityData().getNonDefaultValues());assertHalfSkin(h,synced,Direction.EAST);
        h.assertTrue(restored.sample().is(Blocks.STONE)&&restored.rotation()==1,"Half texture survives save/load");
        h.assertTrue(!left.isPickable()&&!right.canBeCollidedWith(),"Layers do not add hitboxes");
        h.succeed();
    }
    // Unir una segunda losa no extiende la pintura existente a la mitad nueva.
    @GameTest(template="empty") public static void paintingSingleSlabAndSamplingMixedPair(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(CENTER);var source=h.absolutePos(new BlockPos(1,2,1));
        var player=h.makeMockPlayer(GameType.SURVIVAL);var tool=PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
        level.setBlockAndUpdate(pos,PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get().defaultBlockState().setValue(VerticalSlabBlock.FACING,Direction.WEST));
        sampleTexture(player,source,level,Blocks.STONE);var leftHit=halfContext(player,pos,Direction.UP,Direction.WEST);tool.useOn(leftHit);
        var left=TextureSkinEntity.findAt(level,pos,Direction.UP,leftHit.getClickLocation());assertHalfSkin(h,left,Direction.WEST);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB.get()));
        h.assertTrue(player.getMainHandItem().useOn(context(player,pos,Direction.EAST)).consumesAction(),"Second slab joins painted half");
        assertHalfSkin(h,left,Direction.WEST);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));var rightHit=halfContext(player,pos,Direction.UP,Direction.EAST);
        player.setShiftKeyDown(true);tool.useOn(rightHit);player.setShiftKeyDown(false);
        var copied=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Sample"));
        h.assertTrue(copied.is(PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK.get())&&copied.getValue(VerticalSlabBlock.FILL)==VerticalSlabBlock.Fill.NONE,
                "Sampling the unpainted half chooses quartz, not brick or neighboring paint");
        tool.useOn(rightHit);assertHalfSkin(h,TextureSkinEntity.findAt(level,pos,Direction.UP,rightHit.getClickLocation()),Direction.EAST);
        h.assertTrue(left.sample().is(Blocks.STONE),"Original painted half remains independent");h.succeed();
    }

    // Repintar, recibir paquetes y cambiar la forma invalidan solo los datos necesarios.
    @GameTest(template="empty") public static void paintCacheTracksStatePacketsAndDynamicSupports(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(CENTER);
        var bottom=Blocks.STONE_SLAB.defaultBlockState();level.setBlockAndUpdate(pos,bottom);
        var skin=TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.STONE.defaultBlockState(),Direction.UP,0);
        var first=skin.surfaceBoxes();h.assertTrue(first==skin.surfaceBoxes(),"Static support reuses its clipped shape");
        TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.BRICKS.defaultBlockState(),Direction.SOUTH,3);
        h.assertTrue(skin.sample().is(Blocks.BRICKS)&&skin.sampleFace()==Direction.SOUTH&&skin.rotation()==3,"Repaint immediately updates decoded material and rotation");
        var saved=new net.minecraft.nbt.CompoundTag();skin.saveWithoutId(saved);
        var receiver=new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level);receiver.load(saved);
        h.assertTrue(receiver.sample().is(Blocks.BRICKS),"Prime receiver cache before next packet");
        TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.DIRT.defaultBlockState(),Direction.EAST,2);
        receiver.getEntityData().assignValues(skin.getEntityData().getNonDefaultValues());
        h.assertTrue(receiver.sample().is(Blocks.DIRT)&&receiver.sampleFace()==Direction.EAST&&receiver.rotation()==2,"Incoming packet invalidates an already populated cache");
        level.setBlockAndUpdate(pos,bottom.setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP));
        var top=skin.surfaceBoxes();h.assertTrue(!top.equals(first)&&top.stream().allMatch(box->box.minY>=.5),"Support shape change updates the rendered surface");
        skin.discard();var glass=PostedSignage.CIT_GLASS.get();level.setBlockAndUpdate(pos,glass.defaultBlockState());
        level.setBlockAndUpdate(pos.above(),glass.defaultBlockState());
        var dynamic=TextureSkinEntity.apply(level,pos,Direction.SOUTH,Blocks.BRICKS.defaultBlockState(),Direction.NORTH,0);
        var stacked=dynamic.surfaceBoxes();
        h.assertTrue(stacked==dynamic.surfaceBoxes(),"Unchanged dynamic geometry reuses the rendered mesh");
        level.removeBlock(pos.above(),false);
        h.assertTrue(!stacked.equals(dynamic.surfaceBoxes()),"Dynamic support keeps reacting to neighbor geometry changes");
        h.assertTrue(dynamic.surfaceBoxes().equals(TextureSkinSurface.boxes(level.getBlockState(pos),level,pos,null)),"Dynamic clipping matches the current collision geometry");
        h.succeed();
    }

    // Varias caras comparten consulta, pero un frame nuevo ve los vecinos actuales.
    @GameTest(template="empty") public static void paintedDynamicSupportsShareOnlyOneFrame(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,2,3));var glass=PostedSignage.CIT_GLASS.get();
        level.setBlockAndUpdate(pos,glass.defaultBlockState());
        var front=TextureSkinEntity.apply(level,pos,Direction.NORTH,Blocks.BRICKS.defaultBlockState(),Direction.NORTH,0);
        var back=TextureSkinEntity.apply(level,pos,Direction.SOUTH,Blocks.BRICKS.defaultBlockState(),Direction.NORTH,0);
        var cache=new TextureSkinFrameCache();var first=cache.boxes(front);
        h.assertTrue(first==cache.boxes(back),"Same support and half share dynamic shape query");
        level.setBlockAndUpdate(pos.above(),glass.defaultBlockState());cache.clear();
        var updated=cache.boxes(back);
        h.assertTrue(!first.equals(updated),"Next frame sees a changed neighbor");
        h.assertTrue(updated==cache.boxes(front),"Other face uses updated shape");
        level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        h.assertTrue(cache.boxes(front).equals(front.surfaceBoxes()),"Support replacement is immediate");
        h.succeed();
    }

    // Comprueba las dos caras, giros y montaje desplazado en vidrio.
    @GameTest(template="empty") public static void signTextVisibilityTracksFacingAndMount(GameTestHelper h){
        for(Direction facing:Direction.Plane.HORIZONTAL){
            var normal=facing.getClockWise();
            for(boolean glass:new boolean[]{false,true}){
                var state=PostedSignage.CUSTOMIZABLE_HANGING_SIGN_BLOCK.get().defaultBlockState()
                        .setValue(CustomizableHangingSignBlock.FACING,facing).setValue(GlassSignMount.GLASS_MOUNTED,glass);
                // El montaje solo traslada el centro; la normal permanece igual.
                var mount=GlassSignMount.offset(state,facing);
                double mountX=mount.x;
                double mountZ=mount.z;
                double centerX=-117+.5+mountX,centerZ=205+.5+mountZ;
                for(int side:new int[]{-1,1}){
                    double x=centerX+normal.getStepX()*side*2-centerX;
                    double z=centerZ+normal.getStepZ()*side*2-centerZ;
                    h.assertTrue(GlassSignMount.textFaceVisible(facing,side<0,x,z),"Visible text remains: "+facing+" glass="+glass);
                    h.assertTrue(!GlassSignMount.textFaceVisible(facing,side>0,x,z),"Hidden text omitted: "+facing+" glass="+glass);
                }
            }
            h.assertTrue(GlassSignMount.textFaceVisible(facing,false,0,0)&&GlassSignMount.textFaceVisible(facing,true,0,0),"Grazing camera keeps both sides");
        }
        h.succeed();
    }

    // La selección visual usa cada plano real, incluidas formas parciales o altas.
    @GameTest(template="empty") public static void paintedFacesCullOnlyWhenEveryPlaneFacesAway(GameTestHelper h){
        var cube=java.util.List.of(new AABB(0,0,0,1,1,1));
        for(Direction face:Direction.values()){
            Vec3 normal=Vec3.atLowerCornerOf(face.getNormal());
            Vec3 front=new Vec3(.5,.5,.5).add(normal.scale(2));
            Vec3 back=new Vec3(.5,.5,.5).subtract(normal.scale(2));
            h.assertTrue(TextureSkinSurface.facesCamera(face,cube,front.x,front.y,front.z),"Front face remains visible: "+face);
            h.assertTrue(!TextureSkinSurface.facesCamera(face,cube,back.x,back.y,back.z),"Back face is skipped: "+face);
            // El origen del mundo no influye: se usan coordenadas locales del soporte.
            BlockPos origin=new BlockPos(-117,63,205);Vec3 world=front.add(origin.getX(),origin.getY(),origin.getZ());
            h.assertTrue(TextureSkinSurface.facesCamera(face,cube,world.x-origin.getX(),world.y-origin.getY(),world.z-origin.getZ()),"Negative world coordinates preserve visibility");
        }
        var bottom=java.util.List.of(new AABB(0,0,0,1,.5,1));
        var top=java.util.List.of(new AABB(0,.5,0,1,1,1));
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.UP,bottom,.5,.75,.5),"Lower slab top is visible from within the cell");
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.DOWN,top,.5,.25,.5),"Upper slab underside is visible from within the cell");
        var west=java.util.List.of(new AABB(0,0,0,.5,1,1));
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.EAST,west,.75,.5,.5),"Vertical slab inner face remains visible");
        var steps=java.util.List.of(new AABB(0,0,0,1,.5,1),new AABB(0,.5,.5,1,1,1));
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.UP,steps,.5,.75,.25),"One visible step keeps the layer");
        h.assertTrue(!TextureSkinSurface.facesCamera(Direction.UP,steps,.5,.25,.25),"All step faces behind camera plane are skipped");
        var tall=java.util.List.of(new AABB(0,0,0,1,1.5,1));
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.DOWN,tall,.5,-.5,.5),"Extended shapes use their own planes");
        h.assertTrue(!TextureSkinSurface.facesCamera(Direction.UP,tall,.5,1.25,.5),"A tall support does not assume a unit cube");
        h.assertTrue(!TextureSkinSurface.facesCamera(Direction.NORTH,java.util.List.of(),0,0,0),"An empty shape has nothing to draw");
        double e=TextureSkinSurface.SURFACE_OFFSET;
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.UP,bottom,.5,.5+e,.5),"Coplanar cameras remain conservative");
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.UP,bottom,.5,.5+e+.00001,.5),"Just in front of offset face stays visible");
        h.assertTrue(!TextureSkinSurface.facesCamera(Direction.UP,bottom,.5,.5+e-.00001,.5),"Offset is shared with emitted vertices");
        h.succeed();
    }

}
