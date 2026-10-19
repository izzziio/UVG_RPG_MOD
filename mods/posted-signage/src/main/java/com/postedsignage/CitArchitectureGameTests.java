package com.postedsignage;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

// Prueba puertas, vidrio conectado y permisos de edición.
@GameTestHolder(PostedSignage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitArchitectureGameTests {
    // Obtiene la posición base del escenario de prueba.
    private static BlockPos site(GameTestHelper h){return h.absolutePos(new BlockPos(3,1,3));}
    // Coloca una puerta de prueba con orientación y bisagra.
    private static CitDoorEntity place(GameTestHelper h,CitDoorBlock b,Direction f,boolean right){
        BlockPos p=site(h);var s=b.defaultBlockState().setValue(CitDoorBlock.FACING,f).setValue(CitDoorBlock.RIGHT,right);
        h.getLevel().setBlockAndUpdate(p,s);b.setPlacedBy(h.getLevel(),p,s,null,ItemStack.EMPTY);
        return (CitDoorEntity)h.getLevel().getBlockEntity(p);
    }
    // Crea un jugador de prueba con permisos controlados.
    private static Player actor(GameTestHelper h,boolean creative,boolean op){
        Player p=new EditorPlayer(h,creative,op);
        p.setPos(site(h).getCenter().add(0,0,3));return p;
    }
    // Jugador simulado para probar modos y permisos por separado.
    private static final class EditorPlayer extends Player {
        boolean creative,op;
        // Inicializa el jugador con los permisos de la prueba.
        EditorPlayer(GameTestHelper h,boolean creative,boolean op){super(h.getLevel(),site(h),0,new GameProfile(UUID.randomUUID(),"CIT test"));this.creative=creative;this.op=op;}
        // Simula el modo Creativo para la prueba.
        @Override public boolean isCreative(){return creative;}
        // Simula el modo espectador para la prueba.
        @Override public boolean isSpectator(){return false;}
        // Simula los permisos del jugador de prueba.
        @Override public boolean hasPermissions(int level){return op&&level<=2;}
    }
    // Verifica la migración de los objetos de puertas antiguas.
    @GameTest(template="empty")
    public static void retiredDoorItemsResolveToCurrentClearDoors(GameTestHelper h){
        var ids=net.minecraft.core.registries.BuiltInRegistries.ITEM;
        for(String name:new String[]{"glass_door","short_glass_door","wide_glass_door"}){
            var expected=name.equals("wide_glass_door")?PostedSignage.CLEAR_WIDE_DOOR_ITEM.get():PostedSignage.CLEAR_SHORT_DOOR_ITEM.get();
            h.assertTrue(ids.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID,name))==expected,"Old inventories resolve to clear door: "+name);
        }
        h.assertTrue(PostedSignage.WIDE_GLASS_DOOR_BLOCK.get().asItem()==PostedSignage.CLEAR_WIDE_DOOR_ITEM.get(),"Picking/breaking legacy wide door returns current door");
        h.assertTrue(PostedSignage.SHORT_GLASS_DOOR_BLOCK.get().asItem()==PostedSignage.CLEAR_SHORT_DOOR_ITEM.get(),"Picking/breaking legacy short door returns current door");
        h.succeed();
    }
    // Verifica montaje en ambas caras y cambios de soporte.
    @GameTest(template="empty")
    public static void signsAndPostersMountOnBothGlassFacesAndFollowSupport(GameTestHelper h){
        var level=h.getLevel();var support=site(h);var glass=PostedSignage.CIT_GLASS.get();
        var facingProperty=net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        for(var block:net.minecraft.core.registries.BuiltInRegistries.BLOCK){
            if(!(block instanceof WallSignBlock||block instanceof HangingSignBlock||block instanceof CustomizableHangingSignBlock))continue;
            for(Direction facing:Direction.Plane.HORIZONTAL){
                level.setBlockAndUpdate(support,glass.defaultBlockState().setValue(CitGlassBlock.AXIS,facing.getClockWise().getAxis()));
                for(Direction side:new Direction[]{facing,facing.getOpposite()}){
                    var player=actor(h,true,true);player.setYRot(side.getOpposite().toYRot());
                    var signPos=support.relative(side);var stack=new ItemStack(block);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                    var surface=support.getCenter().add(side.getStepX()*.02,0,side.getStepZ()*.02);
                    var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(surface,side,support,false));
                    h.assertTrue(stack.useOn(context).consumesAction(),"Place "+block+" on glass "+side);
                    var placed=level.getBlockState(signPos);
                    h.assertTrue(placed.is(block)&&placed.getValue(facingProperty)==side&&placed.getValue(GlassSignMount.GLASS_MOUNTED),"Mounted orientation and state");
                    var shape=placed.getShape(level,signPos);
                    var regular=placed.setValue(GlassSignMount.GLASS_MOUNTED,false).getShape(level,signPos);
                    var expected=regular.move(-side.getStepX()*.48,0,-side.getStepZ()*.48);
                    h.assertTrue(!Shapes.joinIsNotEmpty(shape,expected,BooleanOp.NOT_SAME),"Selection bounds are flush with glass");
                    var from=support.getCenter().add(side.getStepX()*2,0,side.getStepZ()*2);
                    var hit=level.clip(new ClipContext(from,support.getCenter(),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
                    h.assertTrue(hit.getBlockPos().equals(signPos),"Mounted sign remains selectable ahead of glass");
                }
                level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
                for(Direction side:new Direction[]{facing,facing.getOpposite()})h.assertTrue(!level.getBlockState(support.relative(side)).getValue(GlassSignMount.GLASS_MOUNTED),"Replacing support with a full wall restores wall alignment");
                level.removeBlock(support,false);
                for(Direction side:new Direction[]{facing,facing.getOpposite()})h.assertTrue(level.isEmptyBlock(support.relative(side)),"Removing support releases sign");
            }
        }h.succeed();
    }
    // Verifica puertas sin texto ni editor.
    @GameTest(template="empty")
    public static void clearDoorsKeepGeometryAndNeverOpenLabelEditor(GameTestHelper h){
        for(var b:new CitDoorBlock[]{PostedSignage.CLEAR_SHORT_DOOR.get(),PostedSignage.CLEAR_WIDE_DOOR.get()})
            for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true}){
                var e=place(h,b,facing,right);var player=actor(h,true,true);
                h.assertTrue(!e.hasLabel(),"Plain variant never renders text");
                h.assertTrue(e.beginEdit(player)==null&&!e.saveEdit(player,UUID.randomUUID(),"123",0),"Plain variant rejects editor and label packets");
                var framed=e.frameCells();h.assertTrue(e.toggle(player)&&e.toggle(player),"Same opening and closing behavior");
                var item=e.asItem();h.assertTrue(item.is(b.asItem())&&!item.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),"Recovered plain item has no label");
                e.dismantle(false);for(var pos:framed)h.assertTrue(h.getLevel().isEmptyBlock(pos),"Whole plain assembly removed");
            }
        h.succeed();
    }
    // Verifica la bisagra según la mitad pulsada.
    @GameTest(template="empty")
    public static void itemPlacementChoosesHingeFromClickedHalf(GameTestHelper h){
        var l=h.getLevel();var p=site(h);var player=actor(h,true,false);
        l.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
        for(var b:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})for(Direction f:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true}){
            player.setYRot(f.getOpposite().toYRot());var r=f.getClockWise();double offset=right?.25:-.25;
            var stack=new ItemStack(b);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var hit=new BlockHitResult(Vec3.atBottomCenterOf(p).add(r.getStepX()*offset,0,r.getStepZ()*offset),Direction.UP,p.below(),false);
            var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit);
            h.assertTrue(stack.useOn(context).consumesAction(),"BlockItem places complete door");
            var e=CitDoorEntity.find(l,p);h.assertTrue(e!=null&&e.getBlockState().getValue(CitDoorBlock.FACING)==f&&e.getBlockState().getValue(CitDoorBlock.RIGHT)==right,"Click half selects hinge in "+f);
            h.assertTrue(e.frameCells().size()==b.width*b.height,"Complete assembly");e.dismantle(false);
        }h.succeed();
    }
    // Rechaza guardados tras perder permisos de edición.
    @GameTest(template="empty")
    public static void permissionsAreRecheckedAfterOpeningEditor(GameTestHelper h){
        var e=place(h,PostedSignage.CIT_SHORT_DOOR.get(),Direction.NORTH,false);var p=(EditorPlayer)actor(h,true,true);
        var token=e.beginEdit(p);p.creative=false;h.assertTrue(!e.saveEdit(p,token,"123",0),"Survival operator loses save access");
        p.creative=true;p.op=false;h.assertTrue(!e.saveEdit(p,token,"123",0),"Creative nonoperator loses save access");
        p.op=true;h.assertTrue(e.saveEdit(p,token,"123",0),"Authorized player can save valid session");e.dismantle(false);h.succeed();
    }
    // Conserva el bloque que reemplaza una pieza.
    @GameTest(template="empty")
    public static void replacementCleansAssemblyWithoutRemovingReplacement(GameTestHelper h){
        var l=h.getLevel();
        for(boolean root:new boolean[]{false,true}){
            var e=place(h,PostedSignage.CIT_WIDE_DOOR.get(),Direction.NORTH,false);h.assertTrue(e.toggle(actor(h,false,false)),"Open assembly");
            var cells=new HashSet<>(e.frameCells());cells.addAll(e.leafCells(true).keySet());var selected=root?site(h):site(h).above();
            l.setBlockAndUpdate(selected,Blocks.STONE.defaultBlockState());
            h.assertTrue(l.getBlockState(selected).is(Blocks.STONE),"Replacement block remains");
            for(var cell:cells)if(!cell.equals(selected))h.assertTrue(l.isEmptyBlock(cell),"Replacement leaves no orphan assembly");
            l.removeBlock(selected,false);
        }h.succeed();
    }
    // Verifica que los mensajes conservan los ceros iniciales.
    @GameTest(template="empty")
    public static void editorMessagesRoundTripWithoutLosingLeadingZeros(GameTestHelper h){
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            var open=new CitDoorPayloads.Open(site(h),"0042B",6,UUID.randomUUID());CitDoorPayloads.Open.CODEC.encode(buffer,open);
            h.assertTrue(open.equals(CitDoorPayloads.Open.CODEC.decode(buffer)),"Open message round trip");
            var save=new CitDoorPayloads.Save(open.pos(),open.room(),open.color(),open.token());CitDoorPayloads.Save.CODEC.encode(buffer,save);
            h.assertTrue(save.equals(CitDoorPayloads.Save.CODEC.decode(buffer)),"Save message round trip");
        }finally{buffer.release();}h.succeed();
    }
    // Verifica marcos fijos y colisiones en las cuatro direcciones.
    @GameTest(template="empty")
    public static void framesRemainFixedAndLeavesCollideInAllOrientations(GameTestHelper h){
        for(var b:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})
            for(Direction f:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true}){
                var e=place(h,b,f,right);var player=actor(h,false,false);var frames=e.frameCells();
                var closed=e.leafCells(false);var open=e.leafCells(true);
                h.assertTrue(e.toggle(player),"Survival visitor opens "+f+" hinge "+right);
                h.assertTrue(h.getLevel().getBlockEntity(site(h))==e,"Controller stays stationary");
                for(var p:frames)h.assertTrue(h.getLevel().getBlockState(p).getValue(CitDoorBlock.OPEN),"All fixed frame cells retained");
                for(var cell:open.entrySet())h.assertTrue(!Shapes.joinIsNotEmpty(cell.getValue(),h.getLevel().getBlockState(cell.getKey()).getCollisionShape(h.getLevel(),cell.getKey()),BooleanOp.ONLY_FIRST),"Moving leaf has full collision");
                h.assertTrue(e.toggle(player),"Visitor can close door");
                for(var p:open.keySet())if(!frames.contains(p))h.assertTrue(h.getLevel().isEmptyBlock(p),"Closing removes moving cells");
                h.assertTrue(e.leafCells(false).keySet().equals(closed.keySet()),"Closed footprint restored");e.dismantle(false);
            }
        h.succeed();
    }
    // Verifica que los obstáculos impiden abrir la puerta.
    @GameTest(template="empty")
    public static void obstructionNeverOverwritesGlass(GameTestHelper h){
        for(var b:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})
            for(Direction f:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true}){
                var e=place(h,b,f,right);var frames=e.frameCells();
                BlockPos obstruction=e.leafCells(true).keySet().stream().filter(p->!frames.contains(p)).findFirst().orElseThrow();
                h.getLevel().setBlockAndUpdate(obstruction,PostedSignage.CIT_GLASS.get().defaultBlockState());
                h.assertTrue(!e.toggle(actor(h,false,false)),"Glass prevents swing");
                h.assertTrue(h.getLevel().getBlockState(obstruction).is(PostedSignage.CIT_GLASS.get()),"Glass preserved");
                h.assertTrue(!e.getBlockState().getValue(CitDoorBlock.OPEN),"Blocked swing stays closed");
                h.getLevel().removeBlock(obstruction,false);h.assertTrue(e.toggle(actor(h,false,false)),"Cleared destination opens");e.dismantle(false);
            }
        h.succeed();
    }
    // Prueba permisos, sesiones, distancia y códigos válidos.
    @GameTest(template="empty")
    public static void editorEnforcesPermissionsSessionsRangeAndCodes(GameTestHelper h){
        var e=place(h,PostedSignage.CIT_SHORT_DOOR.get(),Direction.NORTH,false);
        h.assertTrue(e.room().equals("525")&&e.color()==3,"Default yellow CIT-525");
        for(boolean creative:new boolean[]{false,true})for(boolean op:new boolean[]{false,true}){
            var p=actor(h,creative,op);var token=e.beginEdit(p);
            h.assertTrue((token!=null)==(creative&&op),"Both creative and command access required");
            if(token==null)h.assertTrue(!e.saveEdit(p,UUID.randomUUID(),"123",0),"Forged save rejected");
        }
        var p=actor(h,true,true);
        for(String invalid:new String[]{"","12345","422b","CIT-123","1AB","-1","１２"}){
            var token=e.beginEdit(p);h.assertTrue(!e.saveEdit(p,token,invalid,0),"Invalid room rejected: "+invalid);
        }
        for(int c=0;c<7;c++){
            var token=e.beginEdit(p);h.assertTrue(e.saveEdit(p,token,"0042B",c),"Every palette color saves");
            h.assertTrue(e.room().equals("0042B")&&e.color()==c,"Leading zeros retained");
            h.assertTrue(!e.saveEdit(p,token,"123",0),"Replayed save rejected");
        }
        var token=e.beginEdit(p);h.assertTrue(!e.saveEdit(actor(h,true,true),token,"1",0),"Other player rejected");
        h.assertTrue(!e.saveEdit(p,token,"1",7),"Invalid color rejected");
        var newer=e.beginEdit(p);h.assertTrue(!e.saveEdit(p,token,"1",0),"Superseded editor rejected");
        p.setPos(site(h).getCenter().add(100,0,0));h.assertTrue(!e.saveEdit(p,newer,"1",0),"Distant save rejected");
        p.setPos(site(h).getCenter());var last=e.beginEdit(p);e.dismantle(false);
        h.assertTrue(!e.saveEdit(p,last,"1",0),"Destroyed controller rejects stale save");h.succeed();
    }
    // Verifica etiquetas al girar, recargar y recuperar puertas.
    @GameTest(template="empty")
    public static void labelsPersistAcrossSwingSaveAndRecovery(GameTestHelper h){
        var e=place(h,PostedSignage.CIT_WIDE_DOOR.get(),Direction.EAST,true);var p=actor(h,true,true);
        h.assertTrue(e.saveEdit(p,e.beginEdit(p),"0001A",6),"Edited label");
        h.assertTrue(e.toggle(p),"Open before save");
        var copy=new CitDoorEntity(site(h),e.getBlockState());copy.loadWithComponents(e.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.room().equals("0001A")&&copy.color()==6&&copy.getBlockState().getValue(CitDoorBlock.OPEN),"NBT retains open state and label");
        var remote=new CitDoorEntity(site(h),e.getBlockState());remote.loadWithComponents(e.getUpdateTag(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(remote.room().equals(e.room())&&remote.color()==e.color(),"Client synchronization tag retains label");
        var item=e.asItem();e.dismantle(false);var next=place(h,PostedSignage.CIT_WIDE_DOOR.get(),Direction.SOUTH,false);next.readItem(item);
        h.assertTrue(next.room().equals("0001A")&&next.color()==6,"Recovered item preserves label");next.dismantle(false);h.succeed();
    }
    // Verifica una sola puerta recuperable desde cualquier pieza.
    @GameTest(template="empty")
    public static void everyDoorPartBreaksAssemblyWithExactlyOneLabeledDrop(GameTestHelper h){
        var l=h.getLevel();var player=FakePlayerFactory.getMinecraft(l);
        for(var b:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})
            for(Direction f:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true})for(boolean open:new boolean[]{false,true}){
                var sample=place(h,b,f,right);Set<BlockPos> parts=new LinkedHashSet<>(sample.frameCells());if(open)parts.addAll(sample.leafCells(true).keySet());sample.dismantle(false);
                for(var mode:new GameType[]{GameType.CREATIVE,GameType.SURVIVAL})for(var selected:parts){
                    var e=place(h,b,f,right);var editor=actor(h,true,true);e.saveEdit(editor,e.beginEdit(editor),"0422B",4);
                    if(open)h.assertTrue(e.toggle(editor),"Open for break test");
                    player.setGameMode(mode);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    var existing=new HashSet<>(l.getEntitiesOfClass(ItemEntity.class,new AABB(site(h)).inflate(6)));
                    h.assertTrue(player.gameMode.destroyBlock(selected),"Break selected frame/leaf");
                    var drops=l.getEntitiesOfClass(ItemEntity.class,new AABB(site(h)).inflate(6),i->i.getItem().is(b.asItem())&&!existing.contains(i));
                    h.assertTrue(drops.stream().mapToInt(i->i.getItem().getCount()).sum()==(mode==GameType.CREATIVE?0:1),"Exact door item count");
                    for(var drop:drops){var restored=new CitDoorEntity(site(h),b.defaultBlockState());restored.readItem(drop.getItem());h.assertTrue(restored.room().equals("0422B")&&restored.color()==4,"Drop retains label");drop.discard();}
                    for(var part:parts)h.assertTrue(l.isEmptyBlock(part),"No orphan parts");
                }
            }
        h.succeed();
    }
    // Verifica uniones sin bordes internos y esquinas enmarcadas.
    @GameTest(template="empty")
    public static void glassRemovesInternalFramesAndUpdatesCorners(GameTestHelper h){
        var l=h.getLevel();var p=site(h);var b=PostedSignage.CIT_GLASS.get();
        l.setBlockAndUpdate(p,b.defaultBlockState());l.setBlockAndUpdate(p.east(),b.defaultBlockState());
        h.assertTrue(l.getBlockState(p).getValue(CitGlassBlock.E),"Adjacent pane connects");
        h.assertTrue(CitGlassBlock.pieces(l.getBlockState(p),l,p).stream().noneMatch(q->!q.glass()&&q.box().minX>=.9&&q.box().maxY==1&&q.box().minY==0),"No internal vertical border");
        l.setBlockAndUpdate(p.above(),b.defaultBlockState());
        h.assertTrue(CitGlassBlock.pieces(l.getBlockState(p),l,p).stream().noneMatch(q->!q.glass()&&q.box().minY==.9),"No stacked internal border");
        l.setBlockAndUpdate(p.north(),b.defaultBlockState());
        h.assertTrue(CitGlassBlock.pieces(l.getBlockState(p),l,p).stream().anyMatch(q->!q.glass()&&q.box().equals(new AABB(.4,0,.4,.6,1,.6))),"Corner retains center post");
        l.removeBlock(p.east(),false);h.assertTrue(!l.getBlockState(p).getValue(CitGlassBlock.E),"Removal updates connection");h.succeed();
    }
    // Verifica que el vidrio vecino sobrevive a los giros.
    @GameTest(template="empty")
    public static void glassBesideAndAboveFramesSurvivesSwings(GameTestHelper h){
        var l=h.getLevel();var glass=PostedSignage.CIT_GLASS.get();
        for(var b:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})for(Direction f:Direction.Plane.HORIZONTAL){
            var e=place(h,b,f,false);var r=f.getClockWise();Set<BlockPos> panels=new HashSet<>();
            for(int y=0;y<b.height;y++){panels.add(site(h).relative(r,-1).above(y));panels.add(site(h).relative(r,b.width).above(y));}
            for(int x=0;x<b.width;x++)panels.add(site(h).relative(r,x).above(b.height));
            for(var p:panels)l.setBlockAndUpdate(p,glass.defaultBlockState().setValue(CitGlassBlock.AXIS,r.getAxis()));
            h.assertTrue(l.getBlockState(site(h).relative(r,-1)).getValue(CitGlassBlock.property(r)),"Glass connects doorway "+f);
            h.assertTrue(e.toggle(actor(h,false,false)),"Door opens beside glass "+f);
            for(var p:panels)h.assertTrue(l.getBlockState(p).is(glass),"Surrounding glass remains");
            e.dismantle(false);for(var p:panels)l.removeBlock(p,false);
        }h.succeed();
    }
    // Rechaza cambios de letreros sin Creativo y permisos.
    @GameTest(template="empty")
    public static void signUpdatesAndApplicatorsRequireCreativeOperator(GameTestHelper h){
        var l=h.getLevel();var p=site(h);
        for(var registered:net.minecraft.core.registries.BuiltInRegistries.BLOCK)if(registered instanceof CustomizableHangingSignBlock b){
            var state=b.defaultBlockState();l.setBlockAndUpdate(p.south(),Blocks.STONE.defaultBlockState());l.setBlockAndUpdate(p,state);
            var sign=(CustomizableHangingSignBlockEntity)l.getBlockEntity(p);
            sign.setText(sign.getFrontText().setMessage(0,Component.literal("front")),true);
            sign.setText(sign.getBackText().setMessage(0,Component.literal("back")),false);
            for(boolean creative:new boolean[]{false,true})for(boolean op:new boolean[]{false,true})if(!creative||!op){
                var player=actor(h,creative,op);sign.setAllowedPlayerEditor(player.getUUID());
                sign.updateSignText(player,true,List.of(FilteredText.passThrough("bad"),FilteredText.EMPTY,FilteredText.EMPTY,FilteredText.EMPTY));
                h.assertTrue(sign.getFrontText().getMessage(0,false).getString().equals("front"),"Direct unauthorized update blocked");
                for(var item:new Item[]{Items.RED_DYE,Items.GLOW_INK_SAC,Items.HONEYCOMB})b.useItemOn(new ItemStack(item),state,l,p,player,InteractionHand.MAIN_HAND,new BlockHitResult(p.getCenter(),Direction.NORTH,p,false));
                h.assertTrue(!sign.isWaxed()&&!sign.getFrontText().hasGlowingText()&&sign.getFrontText().getColor()==DyeColor.BLACK,"Unauthorized appearance changes blocked");
            }
            var owner=actor(h,true,true);sign.setAllowedPlayerEditor(owner.getUUID());
            sign.updateSignText(owner,true,List.of(FilteredText.passThrough("0422B"),FilteredText.EMPTY,FilteredText.EMPTY,FilteredText.EMPTY));
            h.assertTrue(sign.getFrontText().getMessage(0,false).getString().equals("0422B")&&sign.getBackText().getMessage(0,false).getString().equals("back"),"Authorized update preserves other face");l.removeBlock(p,false);
        }h.succeed();
    }
}
