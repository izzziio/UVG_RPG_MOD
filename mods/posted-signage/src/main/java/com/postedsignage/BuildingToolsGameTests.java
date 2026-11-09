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
                helper.assertTrue(state.getValue(VerticalSlabBlock.FACING) == face.getOpposite(), "La losa se pega a la cara pulsada");
                var shape = state.getShape(helper.getLevel(), placement.getClickedPos());
                var bounds = shape.bounds();
                helper.assertTrue(Math.abs(bounds.getXsize()*bounds.getYsize()*bounds.getZsize()-0.5) < 0.0001, "La losa ocupa medio bloque");
                helper.assertTrue(bounds.getYsize() == 1, "Las losas verticales tienen altura completa");
                helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(VerticalSlabBlock.FACING) == face.getOpposite().getClockWise(), "La rotación sigue la geometría de colisión");
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
            helper.assertTrue(hit.getBlockPos().equals(pos)&&hit.getDirection()==half.getOpposite(),"El rayo apunta a la cara interna de la mitad");
            helper.assertTrue(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"La segunda mitad se coloca");
            var paired=level.getBlockState(pos);
            helper.assertTrue(paired.is(first)&&paired.getValue(VerticalSlabBlock.FACING)==half,"La primera mitad se mantiene en su lugar");
            var fill=second==slabs[0]?VerticalSlabBlock.Fill.BRICK:VerticalSlabBlock.Fill.QUARTZ;
            helper.assertTrue(paired.getValue(VerticalSlabBlock.FILL)==fill,"Se guarda el segundo material");
            helper.assertTrue(player.getMainHandItem().getCount()==2,"Se consume exactamente un objeto");
            helper.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(paired.getCollisionShape(level,pos),net.minecraft.world.phys.shapes.Shapes.block(),net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"El par tiene colisión de bloque completo");
            helper.assertTrue(level.isEmptyBlock(pos.relative(half.getOpposite())),"La segunda losa comparte la celda original");
            helper.assertTrue(player.getMainHandItem().useOn(context(player,pos,half.getOpposite())).consumesAction(),"La tercera losa se coloca al lado");
            helper.assertTrue(level.getBlockState(pos).equals(paired),"La tercera losa no puede sobrescribir el par");
            helper.assertTrue(level.getBlockState(pos.relative(half.getOpposite())).is(second),"La tercera losa ocupa la siguiente celda");
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
            helper.assertTrue(player.getMainHandItem().useOn(context(player,pos,half)).consumesAction(),"La cara exterior coloca una losa adyacente");
            helper.assertTrue(level.getBlockState(pos).equals(original)&&level.getBlockState(pos.relative(half)).is(quartz),"La mitad ocupada nunca se reemplaza");
            level.removeBlock(pos.relative(half),false);
            var freeTop=pos.getCenter().add(half.getOpposite().getStepX()*.25,.5,half.getOpposite().getStepZ()*.25);
            var top=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(freeTop,Direction.UP,pos,false));
            helper.assertTrue(player.getMainHandItem().useOn(top).consumesAction(),"Un clic superior sobre la mitad libre puede formar el par");
            helper.assertTrue(level.getBlockState(pos).getValue(VerticalSlabBlock.FILL)==VerticalSlabBlock.Fill.QUARTZ,"La mitad correcta se llena desde arriba");
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
            helper.assertTrue(state.equals(restored),"La orientación y ambos materiales sobreviven al guardar y cargar");
            var drops=Block.getDrops(state,level,pos,null);
            int bricks=(first==slabs[0]?1:0)+(fill==VerticalSlabBlock.Fill.BRICK?1:0);
            int quartz=(first==slabs[1]?1:0)+(fill==VerticalSlabBlock.Fill.QUARTZ?1:0);
            helper.assertTrue(drops.stream().filter(i->i.is(slabs[0].asItem())).mapToInt(ItemStack::getCount).sum()==bricks,"Recuperación exacta del ladrillo");
            helper.assertTrue(drops.stream().filter(i->i.is(slabs[1].asItem())).mapToInt(ItemStack::getCount).sum()==quartz,"Recuperación exacta del cuarzo");
            helper.assertTrue(state.rotate(Rotation.CLOCKWISE_90).getValue(VerticalSlabBlock.FILL)==fill,"La rotación conserva el material del par");
            saved.getCompound("Properties").remove("fill");
            var legacy=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),saved);
            helper.assertTrue(legacy.getValue(VerticalSlabBlock.FILL)==VerticalSlabBlock.Fill.NONE,"Las losas simples antiguas siguen siendo simples");
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
            helper.assertTrue(cover.canSurvive(level,dest), "Soporte sólido aceptado en" + face);
            level.setBlockAndUpdate(dest,cover);
            helper.assertTrue(cover.getCollisionShape(level,dest).isEmpty(), "El recubrimiento no debe cambiar la colisión de movimiento");
        }
        level.destroyBlock(pos,false);
        for (Direction face : Direction.values()) helper.assertTrue(level.isEmptyBlock(pos.relative(face)), "Recubrimiento sin soporte retirado en" + face);
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
        helper.assertTrue(tool.useOn(context(player,pos,Direction.UP)).consumesAction(), "Tomar muestra de origen");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        helper.assertTrue(tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(), "Aplicar capa");
        var skin = TextureSkinEntity.find(level,pos,Direction.NORTH);
        helper.assertTrue(skin != null && skin.sample().is(Blocks.BRICKS) && skin.sampleFace()==Direction.UP, "Se aplica la muestra seleccionada");
        helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE) && level.isEmptyBlock(pos.north()), "El soporte y el aire vecino no cambian");
        var tag = new net.minecraft.nbt.CompoundTag(); skin.saveWithoutId(tag);
        var restored = new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level); restored.load(tag);
        helper.assertTrue(restored.sample().equals(skin.sample()) && restored.support().equals(pos) && restored.face()==Direction.NORTH, "Ida y vuelta a disco");
        var synced = new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level);
        synced.getEntityData().assignValues(skin.getEntityData().getNonDefaultValues());
        helper.assertTrue(synced.sample().equals(skin.sample()) && synced.face()==skin.face(), "Los datos de seguimiento incluyen la textura y la cara");
        helper.assertTrue(skin.isIgnoringBlockTriggers(), "La capa no puede activar placas de presión");
        helper.assertTrue(!skin.isPickable() && !skin.canBeCollidedWith() && !skin.isPushable(), "Sin caja de colisión ni objetivo de interacción");
        player.setShiftKeyDown(true); tool.useOn(context(player,pos,Direction.EAST)); player.setShiftKeyDown(false);
        tool.useOn(context(player,pos,Direction.NORTH));
        helper.assertTrue(TextureSkinEntity.find(level,pos,Direction.NORTH)==skin && skin.sample().is(Blocks.STONE), "Repintar reutiliza la capa");
        player.setShiftKeyDown(true); tool.useOn(context(player,pos,Direction.NORTH));
        helper.assertTrue(player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag().getString("SampleFace").equals("east"), "Tomar muestra de una cara ya pintada");
        level.destroyBlock(pos,false); skin.tick();
        helper.assertTrue(skin.isRemoved(), "Romper el soporte retira la capa");
        helper.succeed();
    }

    // Caras adyacentes, vallas y permisos conservan el comportamiento original.
    @GameTest(template="empty") public static void skinsAllowNeighborsAndKeepPermissions(GameTestHelper helper) {
        var level=helper.getLevel(); var pos=helper.absolutePos(CENTER);
        level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        var player=helper.makeMockPlayer(GameType.SURVIVAL); var tool=PostedSignage.TEXTURE_APPLICATOR.get();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));
        helper.assertTrue(!tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(),"Se requiere una selección");
        player.setShiftKeyDown(true); tool.useOn(context(player,pos,Direction.UP)); player.setShiftKeyDown(false);
        for(Direction face:Direction.values()) helper.assertTrue(tool.useOn(context(player,pos,face)).consumesAction(),"Pintar todas las caras");
        var paintedTool=player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.OAK_FENCE));
        helper.assertTrue(player.getMainHandItem().useOn(context(player,pos,Direction.NORTH)).consumesAction(),"Colocar una valla real contra la cara pintada");
        helper.assertTrue(level.getBlockState(pos.north()).is(Blocks.OAK_FENCE),"La valla ocupa la celda vecina");
        helper.assertTrue(level.getBlockState(pos.north()).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH),"La valla se conecta al soporte original");
        player.setItemInHand(InteractionHand.MAIN_HAND,paintedTool);
        helper.assertTrue(tool.useOn(context(player,pos,Direction.NORTH)).consumesAction(),"Pintar junto a una valla existente");
        level.setBlockAndUpdate(pos.east(),Blocks.STONE.defaultBlockState());
        helper.assertTrue(tool.useOn(context(player,pos.east(),Direction.NORTH)).consumesAction(),"Pintar la esquina contigua");
        helper.assertTrue(level.getBlockState(pos).getCollisionShape(level,pos).equals(Blocks.STONE.defaultBlockState().getCollisionShape(level,pos)),"La colisión original no cambia");
        var adventure=helper.makeMockPlayer(GameType.ADVENTURE); GameType.ADVENTURE.updatePlayerAbilities(adventure.getAbilities());
        adventure.setItemInHand(InteractionHand.MAIN_HAND,paintedTool.copy());
        helper.assertTrue(!tool.useOn(context(adventure,pos,Direction.SOUTH)).consumesAction(),"En Aventura no se puede pintar");
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
            helper.assertTrue(tool.useOn(context(player,pos,face)).consumesAction(),"Pintar la superficie");
            var skin=TextureSkinEntity.find(level,pos,face); int expected=face==Direction.UP?floor[i]:face==Direction.DOWN?ceiling[i]:0;
            helper.assertTrue(skin.rotation()==expected,"Rotación correcta");
            var tag=new net.minecraft.nbt.CompoundTag(); skin.saveWithoutId(tag);
            var restored=new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level); restored.load(tag);
            helper.assertTrue(restored.rotation()==expected,"La rotación sobrevive a la recarga");
        }
        for(var block:new Block[]{Blocks.STONE_SLAB,Blocks.STONE_STAIRS,PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get()}) {
            level.setBlockAndUpdate(pos,block.defaultBlockState());
            for(Direction face:Direction.values()) helper.assertTrue(tool.useOn(context(player,pos,face)).consumesAction(),"Los bloques parciales aceptan caras");
            helper.assertTrue(level.getBlockState(pos).is(block),"Se conserva el soporte parcial");
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
            helper.assertTrue(level.isEmptyBlock(dest),"Se libera la celda antigua");
            helper.assertTrue(skin!=null && skin.sample().is(Blocks.BRICKS) && skin.rotation()==3,"Se conservan la muestra y la rotación antiguas");
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
        h.assertTrue(skin!=null&&skin.half()==half,"La capa pertenece a la mitad seleccionada");double volume=0;
        for(var box:skin.surfaceBoxes()) {
            volume+=box.getXsize()*box.getYsize()*box.getZsize();
            boolean contained=switch(half) {
                case NORTH -> box.maxZ<=.5; case SOUTH -> box.minZ>=.5;
                case WEST -> box.maxX<=.5; case EAST -> box.minX>=.5;
                default -> false;
            };
            h.assertTrue(contained,"La superficie dibujada nunca se extiende a la otra losa");
        }
        h.assertTrue(Math.abs(volume-.5)<.00001,"La geometría de pintura cubre exactamente una mitad");
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
                h.assertTrue(tool.useOn(hit).consumesAction(),"Pintar la mitad pulsada");
                var skin=TextureSkinEntity.findAt(level,pos,face,hit.getClickLocation());assertHalfSkin(h,skin,half);
                h.assertTrue(skin.sample().is(Blocks.STONE),"Se aplica el material seleccionado");
                if(face.getAxis()!=facing.getAxis()) {
                    var other=halfContext(player,pos,face,half.getOpposite());
                    h.assertTrue(TextureSkinEntity.findAt(level,pos,face,other.getClickLocation())==null,"La otra mitad sigue sin pintar");
                    sampleTexture(player,source,level,Blocks.BRICKS);tool.useOn(other);
                    var second=TextureSkinEntity.findAt(level,pos,face,other.getClickLocation());assertHalfSkin(h,second,half.getOpposite());
                    h.assertTrue(second!=skin&&second.sample().is(Blocks.BRICKS)&&skin.sample().is(Blocks.STONE),"Pintar la segunda mitad conserva la primera");
                    player.setShiftKeyDown(true);tool.useOn(hit);player.setShiftKeyDown(false);
                    var copied=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                            player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Sample"));
                    h.assertTrue(copied.is(Blocks.STONE),"La muestra toma la mitad pintada bajo el cursor");
                }
                h.assertTrue(level.getBlockState(pos).equals(paired),"Pintar conserva el estado y la colisión de la losa");
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
        h.assertTrue(old.half()==null,"La capa antigua cubre toda la cara");
        var left=TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.STONE.defaultBlockState(),Direction.NORTH,1,Direction.WEST);
        var right=TextureSkinEntity.findAt(level,pos,Direction.UP,pos.getCenter().add(.25,.5,0));
        var cropped=TextureSkinSurface.uv(Direction.UP,.75,1,.5,right.rotation());
        h.assertTrue(Math.abs(cropped.u()-.5)<.00001&&Math.abs(cropped.v()-.75)<.00001,"La mitad sin pintar conserva la escala UV y la rotación originales de la cara completa");
        h.assertTrue(old.isRemoved()&&right!=null&&right.sample().is(Blocks.BRICKS)&&right.rotation()==3,"La mitad antigua sin pintar conserva su material y rotación");
        assertHalfSkin(h,left,Direction.WEST);assertHalfSkin(h,right,Direction.EAST);
        var saved=new net.minecraft.nbt.CompoundTag();left.saveWithoutId(saved);
        var restored=new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level);restored.load(saved);assertHalfSkin(h,restored,Direction.WEST);
        var synced=new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level);
        synced.getEntityData().assignValues(right.getEntityData().getNonDefaultValues());assertHalfSkin(h,synced,Direction.EAST);
        h.assertTrue(restored.sample().is(Blocks.STONE)&&restored.rotation()==1,"La textura de la mitad sobrevive al guardar y cargar");
        h.assertTrue(!left.isPickable()&&!right.canBeCollidedWith(),"Las capas no agregan cajas de colisión");
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
        h.assertTrue(player.getMainHandItem().useOn(context(player,pos,Direction.EAST)).consumesAction(),"La segunda losa se une a la mitad pintada");
        assertHalfSkin(h,left,Direction.WEST);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(tool));var rightHit=halfContext(player,pos,Direction.UP,Direction.EAST);
        player.setShiftKeyDown(true);tool.useOn(rightHit);player.setShiftKeyDown(false);
        var copied=net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                player.getMainHandItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Sample"));
        h.assertTrue(copied.is(PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK.get())&&copied.getValue(VerticalSlabBlock.FILL)==VerticalSlabBlock.Fill.NONE,
                "La muestra de la mitad sin pintar elige cuarzo, no ladrillo ni pintura vecina");
        tool.useOn(rightHit);assertHalfSkin(h,TextureSkinEntity.findAt(level,pos,Direction.UP,rightHit.getClickLocation()),Direction.EAST);
        h.assertTrue(left.sample().is(Blocks.STONE),"La mitad pintada original sigue siendo independiente");h.succeed();
    }

    // Repintar, recibir paquetes y cambiar la forma invalidan solo los datos necesarios.
    @GameTest(template="empty") public static void paintCacheTracksStatePacketsAndDynamicSupports(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(CENTER);
        var bottom=Blocks.STONE_SLAB.defaultBlockState();level.setBlockAndUpdate(pos,bottom);
        var skin=TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.STONE.defaultBlockState(),Direction.UP,0);
        var first=skin.surfaceBoxes();h.assertTrue(first==skin.surfaceBoxes(),"El soporte estático reutiliza su forma recortada");
        TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.BRICKS.defaultBlockState(),Direction.SOUTH,3);
        h.assertTrue(skin.sample().is(Blocks.BRICKS)&&skin.sampleFace()==Direction.SOUTH&&skin.rotation()==3,"Repintar actualiza de inmediato el material decodificado y la rotación");
        var saved=new net.minecraft.nbt.CompoundTag();skin.saveWithoutId(saved);
        var receiver=new TextureSkinEntity(PostedSignage.TEXTURE_SKIN.get(),level);receiver.load(saved);
        h.assertTrue(receiver.sample().is(Blocks.BRICKS),"Preparar la caché del receptor antes del siguiente paquete");
        TextureSkinEntity.apply(level,pos,Direction.UP,Blocks.DIRT.defaultBlockState(),Direction.EAST,2);
        receiver.getEntityData().assignValues(skin.getEntityData().getNonDefaultValues());
        h.assertTrue(receiver.sample().is(Blocks.DIRT)&&receiver.sampleFace()==Direction.EAST&&receiver.rotation()==2,"Un paquete entrante invalida una caché ya llena");
        level.setBlockAndUpdate(pos,bottom.setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP));
        var top=skin.surfaceBoxes();h.assertTrue(!top.equals(first)&&top.stream().allMatch(box->box.minY>=.5),"El cambio de forma del soporte actualiza la superficie dibujada");
        skin.discard();var glass=PostedSignage.CIT_GLASS.get();level.setBlockAndUpdate(pos,glass.defaultBlockState());
        level.setBlockAndUpdate(pos.above(),glass.defaultBlockState());
        var dynamic=TextureSkinEntity.apply(level,pos,Direction.SOUTH,Blocks.BRICKS.defaultBlockState(),Direction.NORTH,0);
        var stacked=dynamic.surfaceBoxes();
        h.assertTrue(stacked==dynamic.surfaceBoxes(),"La geometría dinámica sin cambios reutiliza la malla dibujada");
        level.removeBlock(pos.above(),false);
        h.assertTrue(!stacked.equals(dynamic.surfaceBoxes()),"El soporte dinámico sigue reaccionando a los cambios de geometría vecinos");
        h.assertTrue(dynamic.surfaceBoxes().equals(TextureSkinSurface.boxes(level.getBlockState(pos),level,pos,null)),"El recorte dinámico coincide con la geometría de colisión actual");
        h.succeed();
    }

    // Varias caras comparten consulta, pero un frame nuevo ve los vecinos actuales.
    @GameTest(template="empty") public static void paintedDynamicSupportsShareOnlyOneFrame(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,2,3));var glass=PostedSignage.CIT_GLASS.get();
        level.setBlockAndUpdate(pos,glass.defaultBlockState());
        var front=TextureSkinEntity.apply(level,pos,Direction.NORTH,Blocks.BRICKS.defaultBlockState(),Direction.NORTH,0);
        var back=TextureSkinEntity.apply(level,pos,Direction.SOUTH,Blocks.BRICKS.defaultBlockState(),Direction.NORTH,0);
        var cache=new TextureSkinFrameCache();var first=cache.boxes(front);
        h.assertTrue(first==cache.boxes(back),"El mismo soporte y mitad comparten la consulta de forma dinámica");
        level.setBlockAndUpdate(pos.above(),glass.defaultBlockState());cache.clear();
        var updated=cache.boxes(back);
        h.assertTrue(!first.equals(updated),"El siguiente fotograma detecta un vecino cambiado");
        h.assertTrue(updated==cache.boxes(front),"La otra cara usa la forma actualizada");
        level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        h.assertTrue(cache.boxes(front).equals(front.surfaceBoxes()),"El reemplazo del soporte es inmediato");
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
                    h.assertTrue(GlassSignMount.textFaceVisible(facing,side<0,x,z),"El texto visible permanece:"+facing+" glass="+glass);
                    h.assertTrue(!GlassSignMount.textFaceVisible(facing,side>0,x,z),"Texto oculto omitido:"+facing+" glass="+glass);
                }
            }
            h.assertTrue(GlassSignMount.textFaceVisible(facing,false,0,0)&&GlassSignMount.textFaceVisible(facing,true,0,0),"La cámara rasante conserva ambos lados");
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
            h.assertTrue(TextureSkinSurface.facesCamera(face,cube,front.x,front.y,front.z),"La cara frontal sigue visible:"+face);
            h.assertTrue(!TextureSkinSurface.facesCamera(face,cube,back.x,back.y,back.z),"Se omite la cara trasera:"+face);
            // El origen del mundo no influye: se usan coordenadas locales del soporte.
            BlockPos origin=new BlockPos(-117,63,205);Vec3 world=front.add(origin.getX(),origin.getY(),origin.getZ());
            h.assertTrue(TextureSkinSurface.facesCamera(face,cube,world.x-origin.getX(),world.y-origin.getY(),world.z-origin.getZ()),"Las coordenadas negativas conservan la visibilidad");
        }
        var bottom=java.util.List.of(new AABB(0,0,0,1,.5,1));
        var top=java.util.List.of(new AABB(0,.5,0,1,1,1));
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.UP,bottom,.5,.75,.5),"La parte superior de la losa inferior es visible desde la celda");
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.DOWN,top,.5,.25,.5),"La parte inferior de la losa superior es visible desde la celda");
        var west=java.util.List.of(new AABB(0,0,0,.5,1,1));
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.EAST,west,.75,.5,.5),"La cara interna de la losa vertical sigue visible");
        var steps=java.util.List.of(new AABB(0,0,0,1,.5,1),new AABB(0,.5,.5,1,1,1));
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.UP,steps,.5,.75,.25),"Un peldaño visible conserva la capa");
        h.assertTrue(!TextureSkinSurface.facesCamera(Direction.UP,steps,.5,.25,.25),"Se omiten todas las caras del peldaño detrás del plano de la cámara");
        var tall=java.util.List.of(new AABB(0,0,0,1,1.5,1));
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.DOWN,tall,.5,-.5,.5),"Las formas extendidas usan sus propios planos");
        h.assertTrue(!TextureSkinSurface.facesCamera(Direction.UP,tall,.5,1.25,.5),"Un soporte alto no supone un cubo unitario");
        h.assertTrue(!TextureSkinSurface.facesCamera(Direction.NORTH,java.util.List.of(),0,0,0),"Una forma vacía no tiene nada que dibujar");
        double e=TextureSkinSurface.SURFACE_OFFSET;
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.UP,bottom,.5,.5+e,.5),"Las cámaras coplanares siguen siendo conservadoras");
        h.assertTrue(TextureSkinSurface.facesCamera(Direction.UP,bottom,.5,.5+e+.00001,.5),"Justo frente a la cara desplazada sigue visible");
        h.assertTrue(!TextureSkinSurface.facesCamera(Direction.UP,bottom,.5,.5+e-.00001,.5),"El desplazamiento se comparte con los vértices emitidos");
        h.succeed();
    }

}
