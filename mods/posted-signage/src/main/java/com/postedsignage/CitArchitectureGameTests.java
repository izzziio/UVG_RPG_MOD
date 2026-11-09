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
    // Simula varios compiladores de chunks consultando la misma puerta.
    @GameTest(template="empty") public static void doorShapesSupportConcurrentReaders(GameTestHelper h){
        var workers=java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            for(var block:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})
                for(var facing:Direction.Plane.HORIZONTAL)for(boolean open:new boolean[]{false,true}){
                    var state=block.defaultBlockState().setValue(CitDoorBlock.FACING,facing).setValue(CitDoorBlock.OPEN,open);
                    var door=new CitDoorEntity(BlockPos.ZERO,state);
                    var start=new java.util.concurrent.CountDownLatch(1);
                    var tasks=new ArrayList<java.util.concurrent.Future<?>>();
                    for(int i=0;i<8;i++)tasks.add(workers.submit(()->{
                        try {start.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                        for(int j=0;j<500;j++){
                            door.shapeAt(BlockPos.ZERO.relative(facing.getClockWise(),j%block.width).above(j%block.height));
                            door.leafCells(j%2==0);door.renderBounds();
                        }
                    }));
                    start.countDown();
                    for(var task:tasks)task.get(10,java.util.concurrent.TimeUnit.SECONDS);
                    h.assertTrue(!door.shapeAt(BlockPos.ZERO).isEmpty(),"El marco conserva su colisión");
                }
        }catch(Exception e){throw new RuntimeException("Fallo concurrente al consultar puertas",e);}
        finally {workers.shutdownNow();}
        h.succeed();
    }
    // Una consulta de iluminación nunca espera al controlador de otro chunk.
    @GameTest(template="empty") public static void doorLightingNeverLoadsControllerChunks(GameTestHelper h){
        BlockGetter unloaded=new BlockGetter(){
            @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){throw new AssertionError("La iluminación consultó la entidad de bloque");}
            @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos){throw new AssertionError("La iluminación consultó el estado del mundo");}
            @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){throw new AssertionError("La iluminación consultó el fluido");}
            @Override public int getHeight(){return 384;}
            @Override public int getMinBuildHeight(){return -64;}
        };
        var boundary=new BlockPos(15,64,15);
        for(var block:new Block[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get(),PostedSignage.CLEAR_SHORT_DOOR.get(),PostedSignage.CLEAR_WIDE_DOOR.get(),PostedSignage.CIT_DOOR_SWING.get()}){
            var state=block.defaultBlockState();
            h.assertTrue(state.getLightBlock(unloaded,boundary)==0,"La puerta de vidrio no bloquea la luz");
            h.assertTrue(state.propagatesSkylightDown(unloaded,boundary),"La luz del cielo es independiente del controlador");
        }
        var far=h.absolutePos(new BlockPos(8192,1,8192));var chunks=h.getLevel().getChunkSource();
        h.assertTrue(chunks.getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"El chunk remoto empieza sin cargar");
        h.assertTrue(CitDoorEntity.find(h.getLevel(),far)==null,"El controlador ausente no se carga");
        h.assertTrue(chunks.getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"La consulta deja el chunk sin cargar");h.succeed();
    }
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
            h.assertTrue(ids.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID,name))==expected,"Los inventarios antiguos se resuelven a la puerta sin texto:"+name);
        }
        h.assertTrue(PostedSignage.WIDE_GLASS_DOOR_BLOCK.get().asItem()==PostedSignage.CLEAR_WIDE_DOOR_ITEM.get(),"Seleccionar o romper la puerta ancha antigua devuelve la puerta actual");
        h.assertTrue(PostedSignage.SHORT_GLASS_DOOR_BLOCK.get().asItem()==PostedSignage.CLEAR_SHORT_DOOR_ITEM.get(),"Seleccionar o romper la puerta corta antigua devuelve la puerta actual");
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
                    h.assertTrue(stack.useOn(context).consumesAction(),"Place "+block+" sobre vidrio"+side);
                    var placed=level.getBlockState(signPos);
                    h.assertTrue(placed.is(block)&&placed.getValue(facingProperty)==side&&placed.getValue(GlassSignMount.GLASS_MOUNTED),"Orientación y estado al montarse");
                    var shape=placed.getShape(level,signPos);
                    var regular=placed.setValue(GlassSignMount.GLASS_MOUNTED,false).getShape(level,signPos);
                    var expected=regular.move(-side.getStepX()*.48,0,-side.getStepZ()*.48);
                    h.assertTrue(!Shapes.joinIsNotEmpty(shape,expected,BooleanOp.NOT_SAME),"Los límites de selección quedan al ras del vidrio");
                    var from=support.getCenter().add(side.getStepX()*2,0,side.getStepZ()*2);
                    var hit=level.clip(new ClipContext(from,support.getCenter(),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
                    h.assertTrue(hit.getBlockPos().equals(signPos),"El rótulo montado sigue siendo seleccionable delante del vidrio");
                }
                level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());
                for(Direction side:new Direction[]{facing,facing.getOpposite()})h.assertTrue(!level.getBlockState(support.relative(side)).getValue(GlassSignMount.GLASS_MOUNTED),"Reemplazar el soporte por una pared completa restaura la alineación con la pared");
                level.removeBlock(support,false);
                for(Direction side:new Direction[]{facing,facing.getOpposite()})h.assertTrue(level.isEmptyBlock(support.relative(side)),"Retirar el soporte libera el rótulo");
            }
        }h.succeed();
    }
    // Verifica puertas sin texto ni editor.
    @GameTest(template="empty")
    public static void clearDoorsKeepGeometryAndNeverOpenLabelEditor(GameTestHelper h){
        for(var b:new CitDoorBlock[]{PostedSignage.CLEAR_SHORT_DOOR.get(),PostedSignage.CLEAR_WIDE_DOOR.get()})
            for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true}){
                var e=place(h,b,facing,right);var player=actor(h,true,true);
                h.assertTrue(!e.hasLabel(),"La variante sin texto nunca dibuja texto");
                h.assertTrue(e.beginEdit(player)==null&&!e.saveEdit(player,UUID.randomUUID(),"123",0),"La variante sin texto rechaza los paquetes del editor y del rótulo");
                var framed=e.frameCells();h.assertTrue(e.toggle(player)&&e.toggle(player),"Mismo comportamiento al abrir y cerrar");
                var item=e.asItem();h.assertTrue(item.is(b.asItem())&&!item.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),"El objeto sin texto recuperado no tiene rótulo");
                e.dismantle(false);for(var pos:framed)h.assertTrue(h.getLevel().isEmptyBlock(pos),"Se retira todo el conjunto sin texto");
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
            h.assertTrue(stack.useOn(context).consumesAction(),"BlockItem coloca la puerta completa");
            var e=CitDoorEntity.find(l,p);h.assertTrue(e!=null&&e.getBlockState().getValue(CitDoorBlock.FACING)==f&&e.getBlockState().getValue(CitDoorBlock.RIGHT)==right,"La mitad pulsada selecciona la bisagra en"+f);
            h.assertTrue(e.frameCells().size()==b.width*b.height,"Conjunto completo");e.dismantle(false);
        }h.succeed();
    }
    // Rechaza guardados tras perder permisos de edición.
    @GameTest(template="empty")
    public static void permissionsAreRecheckedAfterOpeningEditor(GameTestHelper h){
        var e=place(h,PostedSignage.CIT_SHORT_DOOR.get(),Direction.NORTH,false);var p=(EditorPlayer)actor(h,true,true);
        var token=e.beginEdit(p);p.creative=false;h.assertTrue(!e.saveEdit(p,token,"123",0),"Un operador en Supervivencia pierde el acceso a guardar");
        p.creative=true;p.op=false;h.assertTrue(!e.saveEdit(p,token,"123",0),"Un jugador en Creativo sin ser operador pierde el acceso a guardar");
        p.op=true;h.assertTrue(e.saveEdit(p,token,"123",0),"Un jugador autorizado puede guardar una sesión válida");e.dismantle(false);h.succeed();
    }
    // Conserva el bloque que reemplaza una pieza.
    @GameTest(template="empty")
    public static void replacementCleansAssemblyWithoutRemovingReplacement(GameTestHelper h){
        var l=h.getLevel();
        for(boolean root:new boolean[]{false,true}){
            var e=place(h,PostedSignage.CIT_WIDE_DOOR.get(),Direction.NORTH,false);h.assertTrue(e.toggle(actor(h,false,false)),"Abrir el conjunto");
            var cells=new HashSet<>(e.frameCells());cells.addAll(e.leafCells(true).keySet());var selected=root?site(h):site(h).above();
            l.setBlockAndUpdate(selected,Blocks.STONE.defaultBlockState());
            h.assertTrue(l.getBlockState(selected).is(Blocks.STONE),"El bloque de reemplazo permanece");
            for(var cell:cells)if(!cell.equals(selected))h.assertTrue(l.isEmptyBlock(cell),"El reemplazo no deja conjuntos huérfanos");
            l.removeBlock(selected,false);
        }h.succeed();
    }
    // Verifica que los mensajes conservan los ceros iniciales.
    @GameTest(template="empty")
    public static void editorMessagesRoundTripWithoutLosingLeadingZeros(GameTestHelper h){
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            var open=new CitDoorPayloads.Open(site(h),"0042B",6,UUID.randomUUID());CitDoorPayloads.Open.CODEC.encode(buffer,open);
            h.assertTrue(open.equals(CitDoorPayloads.Open.CODEC.decode(buffer)),"Ida y vuelta del mensaje de apertura");
            var save=new CitDoorPayloads.Save(open.pos(),open.room(),open.color(),open.token());CitDoorPayloads.Save.CODEC.encode(buffer,save);
            h.assertTrue(save.equals(CitDoorPayloads.Save.CODEC.decode(buffer)),"Ida y vuelta del mensaje de guardado");
        }finally{buffer.release();}h.succeed();
    }
    // Verifica marcos fijos y colisiones en las cuatro direcciones.
    @GameTest(template="empty")
    public static void framesRemainFixedAndLeavesCollideInAllOrientations(GameTestHelper h){
        for(var b:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})
            for(Direction f:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true}){
                var e=place(h,b,f,right);var player=actor(h,false,false);var frames=e.frameCells();
                var closed=e.leafCells(false);var open=e.leafCells(true);
                h.assertTrue(e.toggle(player),"El visitante en Supervivencia abre"+f+" hinge "+right);
                h.assertTrue(h.getLevel().getBlockEntity(site(h))==e,"El controlador permanece inmóvil");
                for(var p:frames)h.assertTrue(h.getLevel().getBlockState(p).getValue(CitDoorBlock.OPEN),"Se conservan todas las celdas fijas del marco");
                for(var cell:open.entrySet())h.assertTrue(!Shapes.joinIsNotEmpty(cell.getValue(),h.getLevel().getBlockState(cell.getKey()).getCollisionShape(h.getLevel(),cell.getKey()),BooleanOp.ONLY_FIRST),"La hoja en movimiento tiene colisión completa");
                h.assertTrue(e.toggle(player),"El visitante puede cerrar la puerta");
                for(var p:open.keySet())if(!frames.contains(p))h.assertTrue(h.getLevel().isEmptyBlock(p),"Al cerrar se retiran las celdas móviles");
                h.assertTrue(e.leafCells(false).keySet().equals(closed.keySet()),"Se restaura la huella cerrada");e.dismantle(false);
            }
        h.succeed();
    }
    // Puertas contiguas abren en cualquier orden sin reservar la celda vecina.
    @GameTest(template="empty")
    public static void adjacentDoorsOpenIndependently(GameTestHelper h){
        var level=h.getLevel();var player=actor(h,false,false);
        for(var block:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get(),PostedSignage.CLEAR_SHORT_DOOR.get(),PostedSignage.CLEAR_WIDE_DOOR.get()})
            for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean firstRight:new boolean[]{false,true})for(boolean secondRight:new boolean[]{false,true}){
                var first=place(h,block,facing,firstRight);var right=facing.getClockWise();
                var secondPos=site(h).relative(right,block.width);
                var state=block.defaultBlockState().setValue(CitDoorBlock.FACING,facing).setValue(CitDoorBlock.RIGHT,secondRight);
                level.setBlockAndUpdate(secondPos,state);block.setPlacedBy(level,secondPos,state,null,ItemStack.EMPTY);
                var second=(CitDoorEntity)level.getBlockEntity(secondPos);
                for(int order=0;order<2;order++){
                    var a=order==0?first:second;var b=order==0?second:first;
                    h.assertTrue(a.toggle(player),"La primera hoja se abre:"+block+" "+facing+" "+firstRight+"/"+secondRight);
                    h.assertTrue(b.toggle(player),"La vecina se abre de forma independiente:"+block+" "+facing+" "+firstRight+"/"+secondRight);
                    h.assertTrue(a.toggle(player)&&b.getBlockState().getValue(CitDoorBlock.OPEN),"Cerrar una deja abierta la vecina");
                    h.assertTrue(b.toggle(player),"La vecina puede cerrarse de forma independiente");
                }
                first.dismantle(false);second.dismantle(false);
            }
        h.succeed();
    }
    // Las paredes laterales y traseras no bloquean el espacio libre de apertura.
    @GameTest(template="empty")
    public static void doorsOpenBesideWallsWithoutHandleOverhang(GameTestHelper h){
        var level=h.getLevel();var player=actor(h,false,false);
        for(var block:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})
            for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean rightHinge:new boolean[]{false,true}){
                var door=place(h,block,facing,rightHinge);var right=facing.getClockWise();Set<BlockPos> walls=new HashSet<>();
                for(int y=0;y<block.height;y++){
                    for(int x=0;x<block.width;x++)walls.add(site(h).relative(right,x).relative(facing.getOpposite()).above(y));
                    for(int front=0;front<=block.width;front++)for(int side:new int[]{-1,block.width})
                        walls.add(site(h).relative(right,side).relative(facing,front).above(y));
                }
                for(var pos:walls)level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
                for(var cell:door.leafCells(true).entrySet()){
                    h.assertTrue(!walls.contains(cell.getKey()),"La manija abierta permanece dentro del ancho del vano");
                }
                h.assertTrue(door.toggle(player)&&door.toggle(player),"Las paredes laterales o traseras no impiden el giro:"+facing+" "+rightHinge);
                for(var pos:walls)h.assertTrue(level.getBlockState(pos).is(Blocks.STONE),"La pared permanece intacta");
                door.dismantle(false);for(var pos:walls)level.removeBlock(pos,false);
            }
        h.succeed();
    }
    // Limpia la celda de una manija antigua sin desmontar la puerta ni su etiqueta.
    @GameTest(template="empty")
    public static void obsoleteSwingCellsAreReleased(GameTestHelper h){
        var door=place(h,PostedSignage.CIT_SHORT_DOOR.get(),Direction.NORTH,false);var level=h.getLevel();
        var player=actor(h,true,true);h.assertTrue(door.saveEdit(player,door.beginEdit(player),"0042B",6),"Se guarda el rótulo");
        h.assertTrue(door.toggle(player),"La puerta se abre");
        var oldCell=site(h).west().north();
        level.setBlockAndUpdate(oldCell,PostedSignage.CIT_DOOR_SWING.get().defaultBlockState());
        ((CitSwingEntity)level.getBlockEntity(oldCell)).setOwner(site(h));
        h.runAfterDelay(3,()->{
            h.assertTrue(level.isEmptyBlock(oldCell),"Se retira la celda móvil obsoleta");
            h.assertTrue(level.getBlockEntity(site(h))==door&&door.getBlockState().getValue(CitDoorBlock.OPEN),"El conjunto sigue abierto e intacto");
            h.assertTrue(door.room().equals("0042B")&&door.color()==6,"Se conserva el rótulo");
            h.assertTrue(door.toggle(player),"La puerta migrada se cierra");door.dismantle(false);h.succeed();
        });
    }
    // Verifica que los obstáculos impiden abrir la puerta.
    @GameTest(template="empty")
    public static void obstructionNeverOverwritesGlass(GameTestHelper h){
        for(var b:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get()})
            for(Direction f:Direction.Plane.HORIZONTAL)for(boolean right:new boolean[]{false,true}){
                var e=place(h,b,f,right);var frames=e.frameCells();
                BlockPos obstruction=e.leafCells(true).keySet().stream().filter(p->!frames.contains(p)).findFirst().orElseThrow();
                h.getLevel().setBlockAndUpdate(obstruction,PostedSignage.CIT_GLASS.get().defaultBlockState());
                h.assertTrue(!e.toggle(actor(h,false,false)),"El vidrio impide el giro");
                h.assertTrue(h.getLevel().getBlockState(obstruction).is(PostedSignage.CIT_GLASS.get()),"Se conserva el vidrio");
                h.assertTrue(!e.getBlockState().getValue(CitDoorBlock.OPEN),"Con el giro bloqueado sigue cerrada");
                h.getLevel().removeBlock(obstruction,false);h.assertTrue(e.toggle(actor(h,false,false)),"Con el destino despejado se abre");e.dismantle(false);
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
            h.assertTrue((token!=null)==(creative&&op),"Se requiere acceso a Creativo y a comandos");
            if(token==null)h.assertTrue(!e.saveEdit(p,UUID.randomUUID(),"123",0),"Se rechaza el guardado falsificado");
        }
        var p=actor(h,true,true);
        for(String invalid:new String[]{"","12345","422b","CIT-123","1AB","-1","１２"}){
            var token=e.beginEdit(p);h.assertTrue(!e.saveEdit(p,token,invalid,0),"Se rechaza el salón inválido:"+invalid);
        }
        for(int c=0;c<7;c++){
            var token=e.beginEdit(p);h.assertTrue(e.saveEdit(p,token,"0042B",c),"Se guarda cada color de la paleta");
            h.assertTrue(e.room().equals("0042B")&&e.color()==c,"Se conservan los ceros a la izquierda");
            h.assertTrue(!e.saveEdit(p,token,"123",0),"Se rechaza el guardado repetido");
        }
        var token=e.beginEdit(p);h.assertTrue(!e.saveEdit(actor(h,true,true),token,"1",0),"Se rechaza al otro jugador");
        h.assertTrue(!e.saveEdit(p,token,"1",7),"Se rechaza el color inválido");
        var newer=e.beginEdit(p);h.assertTrue(!e.saveEdit(p,token,"1",0),"Se rechaza el editor reemplazado");
        p.setPos(site(h).getCenter().add(100,0,0));h.assertTrue(!e.saveEdit(p,newer,"1",0),"Se rechaza el guardado lejano");
        p.setPos(site(h).getCenter());var last=e.beginEdit(p);e.dismantle(false);
        h.assertTrue(!e.saveEdit(p,last,"1",0),"Un controlador destruido rechaza el guardado obsoleto");h.succeed();
    }
    // Verifica etiquetas al girar, recargar y recuperar puertas.
    @GameTest(template="empty")
    public static void labelsPersistAcrossSwingSaveAndRecovery(GameTestHelper h){
        var e=place(h,PostedSignage.CIT_WIDE_DOOR.get(),Direction.EAST,true);var p=actor(h,true,true);
        h.assertTrue(e.saveEdit(p,e.beginEdit(p),"0001A",6),"Edited label");
        h.assertTrue(e.toggle(p),"Abrir antes de guardar");
        var copy=new CitDoorEntity(site(h),e.getBlockState());copy.loadWithComponents(e.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.room().equals("0001A")&&copy.color()==6&&copy.getBlockState().getValue(CitDoorBlock.OPEN),"NBT conserva el estado abierto y el rótulo");
        var remote=new CitDoorEntity(site(h),e.getBlockState());remote.loadWithComponents(e.getUpdateTag(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(remote.room().equals(e.room())&&remote.color()==e.color(),"La etiqueta de sincronización con el cliente conserva el rótulo");
        var item=e.asItem();e.dismantle(false);var next=place(h,PostedSignage.CIT_WIDE_DOOR.get(),Direction.SOUTH,false);next.readItem(item);
        h.assertTrue(next.room().equals("0001A")&&next.color()==6,"El objeto recuperado conserva el rótulo");next.dismantle(false);h.succeed();
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
                    if(open)h.assertTrue(e.toggle(editor),"Abrir para la prueba de ruptura");
                    player.setGameMode(mode);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    var existing=new HashSet<>(l.getEntitiesOfClass(ItemEntity.class,new AABB(site(h)).inflate(6)));
                    h.assertTrue(player.gameMode.destroyBlock(selected),"Romper el marco u hoja seleccionados");
                    var drops=l.getEntitiesOfClass(ItemEntity.class,new AABB(site(h)).inflate(6),i->i.getItem().is(b.asItem())&&!existing.contains(i));
                    h.assertTrue(drops.stream().mapToInt(i->i.getItem().getCount()).sum()==(mode==GameType.CREATIVE?0:1),"Cantidad exacta de objetos de puerta");
                    for(var drop:drops){var restored=new CitDoorEntity(site(h),b.defaultBlockState());restored.readItem(drop.getItem());h.assertTrue(restored.room().equals("0422B")&&restored.color()==4,"El objeto soltado conserva el rótulo");drop.discard();}
                    for(var part:parts)h.assertTrue(l.isEmptyBlock(part),"Sin partes huérfanas");
                }
            }
        h.succeed();
    }
    // Verifica uniones sin bordes internos y esquinas enmarcadas.
    @GameTest(template="empty")
    public static void glassRemovesInternalFramesAndUpdatesCorners(GameTestHelper h){
        var l=h.getLevel();var p=site(h);var b=PostedSignage.CIT_GLASS.get();
        l.setBlockAndUpdate(p,b.defaultBlockState());l.setBlockAndUpdate(p.east(),b.defaultBlockState());
        h.assertTrue(l.getBlockState(p).getValue(CitGlassBlock.E),"El panel adyacente se conecta");
        h.assertTrue(CitGlassBlock.pieces(l.getBlockState(p),l,p).stream().noneMatch(q->!q.glass()&&q.box().minX>=.9&&q.box().maxY==1&&q.box().minY==0),"Sin borde vertical interno");
        l.setBlockAndUpdate(p.above(),b.defaultBlockState());
        h.assertTrue(CitGlassBlock.pieces(l.getBlockState(p),l,p).stream().noneMatch(q->!q.glass()&&q.box().minY==.9),"Sin borde interno apilado");
        l.setBlockAndUpdate(p.north(),b.defaultBlockState());
        h.assertTrue(CitGlassBlock.pieces(l.getBlockState(p),l,p).stream().anyMatch(q->!q.glass()&&q.box().equals(new AABB(.4,0,.4,.6,1,.6))),"La esquina conserva el poste central");
        l.removeBlock(p.east(),false);h.assertTrue(!l.getBlockState(p).getValue(CitGlassBlock.E),"Retirar actualiza la conexión");h.succeed();
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
            h.assertTrue(l.getBlockState(site(h).relative(r,-1)).getValue(CitGlassBlock.property(r)),"El vidrio conecta el vano"+f);
            h.assertTrue(e.toggle(actor(h,false,false)),"La puerta se abre junto al vidrio"+f);
            for(var p:panels)h.assertTrue(l.getBlockState(p).is(glass),"El vidrio circundante permanece");
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
                h.assertTrue(sign.getFrontText().getMessage(0,false).getString().equals("front"),"Se bloquea la actualización directa no autorizada");
                for(var item:new Item[]{Items.RED_DYE,Items.GLOW_INK_SAC,Items.HONEYCOMB})b.useItemOn(new ItemStack(item),state,l,p,player,InteractionHand.MAIN_HAND,new BlockHitResult(p.getCenter(),Direction.NORTH,p,false));
                h.assertTrue(!sign.isWaxed()&&!sign.getFrontText().hasGlowingText()&&sign.getFrontText().getColor()==DyeColor.BLACK,"Se bloquean los cambios de apariencia no autorizados");
            }
            var owner=actor(h,true,true);sign.setAllowedPlayerEditor(owner.getUUID());
            sign.updateSignText(owner,true,List.of(FilteredText.passThrough("0422B"),FilteredText.EMPTY,FilteredText.EMPTY,FilteredText.EMPTY));
            h.assertTrue(sign.getFrontText().getMessage(0,false).getString().equals("0422B")&&sign.getBackText().getMessage(0,false).getString().equals("back"),"La actualización autorizada conserva la otra cara");l.removeBlock(p,false);
        }h.succeed();
    }
    // Repartir un marco entre celdas conserva su área, normales y UV.
    @GameTest(template="empty") public static void terrainFramesPartitionWithoutInteriorCaps(GameTestHelper h){
        for(var block:new CitDoorBlock[]{PostedSignage.CIT_SHORT_DOOR.get(),PostedSignage.CIT_WIDE_DOOR.get(),
                PostedSignage.CLEAR_SHORT_DOOR.get(),PostedSignage.CLEAR_WIDE_DOOR.get()})
            for(Direction facing:Direction.Plane.HORIZONTAL){
                var full=CitGeometryMesh.compile(CitDoorGeometry.frame(block.width,block.height),false);
                double total=0;boolean intermediateUv=false;
                // Cruza límites horizontales y verticales de sección, también en negativos.
                var root=new BlockPos(-17,79,15);
                for(int x=0;x<block.width;x++)for(int y=0;y<block.height;y++){
                    var state=block.defaultBlockState().setValue(CitDoorBlock.FACING,facing)
                            .setValue(CitDoorBlock.X,x).setValue(CitDoorBlock.Y,y);
                    var pos=root.relative(facing.getClockWise(),x).above(y);
                    float[] mesh=CitFrameGeometry.mesh(state,h.getLevel(),pos);total+=meshArea(mesh);
                    for(int i=0;i<mesh.length;i+=8){
                        for(int a=0;a<3;a++)h.assertTrue(mesh[i+a]>=-1e-6&&mesh[i+a]<=1+1e-6,"El vértice del marco permanece en su propia celda");
                        h.assertTrue(mesh[i+3]>=0&&mesh[i+3]<=1&&mesh[i+4]>=0&&mesh[i+4]<=1,"El UV permanece en la textura de origen");
                        if(mesh[i+4]>.01&&mesh[i+4]<.99)intermediateUv=true;
                        double n=mesh[i+5]*mesh[i+5]+mesh[i+6]*mesh[i+6]+mesh[i+7]*mesh[i+7];
                        h.assertTrue(Math.abs(n-1)<1e-6,"Se conservan las normales unitarias");
                    }
                    h.assertTrue(java.util.Arrays.equals(mesh,CitFrameGeometry.mesh(state.setValue(CitDoorBlock.OPEN,true)
                            .setValue(CitDoorBlock.RIGHT,true),h.getLevel(),pos)),"El marco no se mueve con la hoja ni con la bisagra");
                }
                h.assertTrue(Math.abs(meshArea(full)-total)<1e-5,"Sin superficies faltantes ni tapas agregadas:"+facing);
                h.assertTrue(intermediateUv,"Las coordenadas de textura continúan entre celdas");
            }
        h.succeed();
    }
    // Calcula área de rectángulos usando dos lados consecutivos.
    private static double meshArea(float[] mesh){
        double total=0;
        for(int i=0;i<mesh.length;i+=32){
            var a=new Vec3(mesh[i+8]-mesh[i],mesh[i+9]-mesh[i+1],mesh[i+10]-mesh[i+2]);
            var b=new Vec3(mesh[i+24]-mesh[i],mesh[i+25]-mesh[i+1],mesh[i+26]-mesh[i+2]);
            total+=a.cross(b).length();
        }
        return total;
    }
    // Los vecinos verticales cambian el marco horneado sin incluir cristal en él.
    @GameTest(template="empty") public static void terrainGlassFramesTrackVerticalNeighbors(GameTestHelper h){
        var level=h.getLevel();var pos=site(h);var block=PostedSignage.CIT_GLASS.get();
        level.setBlockAndUpdate(pos,block.defaultBlockState());var state=level.getBlockState(pos);
        var before=CitFrameGeometry.mesh(state,level,pos);
        level.setBlockAndUpdate(pos.above(),block.defaultBlockState());
        var after=CitFrameGeometry.mesh(level.getBlockState(pos),level,pos);
        h.assertTrue(meshArea(after)<meshArea(before),"Apilar retira el borde superior interno");
        level.removeBlock(pos.above(),false);
        h.assertTrue(java.util.Arrays.equals(before,CitFrameGeometry.mesh(level.getBlockState(pos),level,pos)),"Retirar el vecino restaura el borde expuesto");
        h.succeed();
    }

    // La profundidad de texto sigue ambas bisagras y todos los giros.
    @GameTest(template="empty") public static void doorLabelDepthFollowsSwing(GameTestHelper h){
        var root=new BlockPos(-123,64,237);
        for(Direction facing:Direction.Plane.HORIZONTAL)for(int width:new int[]{1,2})
            for(boolean right:new boolean[]{false,true})for(boolean open:new boolean[]{false,true})
                for(double depth:new double[]{.2,.476,.5,.524,.8}){
                    var local=new net.minecraft.world.phys.Vec3(right?width-.44:.44,1,depth);
                    var world=CitDoorGeometry.world(open?CitDoorGeometry.swing(local,width,right):local,root,facing);
                    h.assertTrue(Math.abs(CitDoorGeometry.leafDepth(world,root,facing,width,right,open)-depth)<1e-9,
                            "El plano del texto sigue la hoja:"+facing+" right="+right+" open="+open);
                }
        h.succeed();
    }
    // La caja contiene toda la puerta, incluso si la celda raíz queda fuera de vista.
    @GameTest(template="empty") public static void doorRenderBoundsContainFrameAndMovingLeaf(GameTestHelper h){
        var root=site(h);
        for(Direction facing:Direction.Plane.HORIZONTAL)for(int width:new int[]{1,2})
            for(boolean right:new boolean[]{false,true})for(boolean open:new boolean[]{false,true}){
                int height=width==1?2:3;
                var bounds=CitDoorGeometry.renderBounds(root,facing,width,height,right,open);
                for(boolean moving:new boolean[]{false,true})for(var p:moving?CitDoorGeometry.leaf(width,height,right):CitDoorGeometry.frame(width,height)){
                    var box=CitDoorGeometry.transform(p.box(),root,facing,width,right,moving&&open);
                    h.assertTrue(bounds.contains(box.minX,box.minY,box.minZ)&&bounds.contains(box.maxX,box.maxY,box.maxZ),"Los límites contienen todas las esquinas");
                }
            }
        var block=PostedSignage.CIT_SHORT_DOOR.get();h.getLevel().setBlockAndUpdate(root,block.defaultBlockState());
        var e=(CitDoorEntity)h.getLevel().getBlockEntity(root);var before=e.renderBounds();
        h.assertTrue(before==e.renderBounds(),"Los límites se reutilizan entre fotogramas");
        h.getLevel().setBlockAndUpdate(root,e.getBlockState().setValue(CitDoorBlock.OPEN,true));
        h.assertTrue(!before.equals(e.renderBounds()),"Abrir invalida los límites en caché");
        h.succeed();
    }
    // La unión reduce vértices sin alterar bordes opacos ni esquinas.
    @GameTest(template="empty") public static void glassBatchingPreservesFramesAndCornerGeometry(GameTestHelper h){
        var pos=site(h);var level=h.getLevel();var glass=PostedSignage.CIT_GLASS.get();
        for(var axis:new Direction.Axis[]{Direction.Axis.X,Direction.Axis.Z})for(int mask=0;mask<16;mask++){
            var state=glass.defaultBlockState().setValue(CitGlassBlock.AXIS,axis);int bit=0;
            for(Direction d:Direction.Plane.HORIZONTAL)state=state.setValue(CitGlassBlock.property(d),(mask&(1<<bit++))!=0);
            var pieces=CitGlassBlock.pieces(state,level,pos);var merged=CitDoorGeometry.mergeStraightGlass(pieces);
            h.assertTrue(java.util.Arrays.equals(CitGeometryMesh.compile(pieces,false),CitGeometryMesh.compile(merged,false)),"Vértices opacos, normales y UV sin cambios");
            boolean straight=CitGlassBlock.arms(state).size()==2&&CitGlassBlock.arms(state).stream().map(Direction::getAxis).distinct().count()==1;
            int oldLength=CitGeometryMesh.compile(pieces,true).length,newLength=CitGeometryMesh.compile(merged,true).length;
            h.assertTrue(straight?newLength*2==oldLength:java.util.Arrays.equals(CitGeometryMesh.compile(pieces,true),CitGeometryMesh.compile(merged,true)),"Solo se unen los paneles rectos");
            double before=pieces.stream().filter(CitDoorGeometry.Piece::glass).mapToDouble(p->p.box().getXsize()*p.box().getZsize()).sum();
            double after=merged.stream().filter(CitDoorGeometry.Piece::glass).mapToDouble(p->p.box().getXsize()*p.box().getZsize()).sum();
            h.assertTrue(Math.abs(before-after)<1e-12,"Se conserva la cobertura del vidrio");
        }
        h.succeed();
    }

    // Reutilizar el panel no debe dejar marcos antiguos al cambiar sus vecinos.
    @GameTest(template="empty")
    public static void cachedGlassGeometryTracksNeighborChanges(GameTestHelper h){
        var level=h.getLevel();var pos=site(h);var glass=PostedSignage.CIT_GLASS.get();
        level.setBlockAndUpdate(pos,glass.defaultBlockState());
        var entity=(CitGlassEntity)level.getBlockEntity(pos);var initial=entity.geometry();
        h.assertTrue(initial==entity.geometry(),"El panel sin cambios reutiliza la geometría");
        level.setBlockAndUpdate(pos.above(),glass.defaultBlockState());var stacked=entity.geometry();
        h.assertTrue(!stacked.equals(initial),"Apilar retira el marco superior interior");
        h.assertTrue(stacked.equals(CitGlassBlock.pieces(entity.getBlockState(),level,pos)),"La geometría apilada en caché coincide con el mundo actual");
        level.setBlockAndUpdate(pos.east(),glass.defaultBlockState());var joined=entity.geometry();
        h.assertTrue(!joined.equals(stacked),"La conexión horizontal retira el marco lateral interno");
        level.removeBlock(pos.above(),false);level.removeBlock(pos.east(),false);
        h.assertTrue(entity.geometry().equals(initial),"Retirar los vecinos restaura los bordes expuestos");
        level.setBlockAndUpdate(pos.above(),PostedSignage.CIT_SHORT_DOOR.get().defaultBlockState());
        h.assertTrue(entity.geometry().equals(CitGlassBlock.pieces(entity.getBlockState(),level,pos)),"El vano superior también invalida los bordes en caché");
        h.succeed();
    }

}
