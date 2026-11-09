package com.postedsignage;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

// Controla la puerta completa, su etiqueta y sus sesiones de edición.
public final class CitDoorEntity extends BlockEntity {
    // Colores dominantes exactos de las siete texturas CIT.
    public static final int[] COLORS={0x005BD2,0x189C47,0xD9091C,0xF2B801,0x2FAAEE,0x632098,0xFE6101};
    // Etiqueta persistente; las sesiones de edición son temporales.
    private String room="525";
    private int color=3;
    private UUID editor,token;
    private long expires;
    private BlockState boundsState;
    private AABB renderBounds;
    private BlockState leafState;
    private Map<BlockPos,VoxelShape> openLeaf,closedLeaf;
    private final Map<BlockPos,VoxelShape> collisionCells=new HashMap<>();
    // El estado cambia al girar la hoja; la caja se conserva entre fotogramas.
    public synchronized AABB renderBounds(){
        var s=getBlockState();
        if(s!=boundsState){
            var block=(CitDoorBlock)s.getBlock();
            renderBounds=CitDoorGeometry.renderBounds(worldPosition,s.getValue(CitDoorBlock.FACING),block.width,block.height,
                    s.getValue(CitDoorBlock.RIGHT),s.getValue(CitDoorBlock.OPEN));
            boundsState=s;
        }
        return renderBounds;
    }
    boolean changing,removing;
    // Inicializa los datos de esta entidad.
    public CitDoorEntity(BlockPos p,BlockState s){super(PostedSignage.CIT_DOOR_ENTITY.get(),p,s);}
    // Devuelve el código sin el prefijo CIT-.
    public String room(){return room;}
    // Devuelve el índice de la paleta CIT.
    public int color(){return color;}
    // Indica si esta variante admite texto.
    public boolean hasLabel(){return ((CitDoorBlock)getBlockState().getBlock()).hasLabel;}
    // Restringe la escritura de NBT a operadores.
    @Override public boolean onlyOpCanSetNbt(){return true;}
    // Identifica la celda que controla la puerta.
    public boolean isRoot(){return getBlockState().getValue(CitDoorBlock.X)==0&&getBlockState().getValue(CitDoorBlock.Y)==0;}
    // Valida dígitos y letra opcional sin perder ceros.
    public static boolean validRoom(String value){return value!=null&&value.matches("[0-9]{1,4}[A-Z]?");}
    // Localiza el controlador desde el marco o la hoja.
    public static CitDoorEntity find(BlockGetter l,BlockPos p){
        if(l instanceof net.minecraft.server.level.ServerLevel server&&server.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)==null)return null;
        BlockState s=l.getBlockState(p);
        if(s.getBlock() instanceof CitDoorBlock) {
            BlockPos root=CitDoorBlock.rootPos(p,s);
            return loadedEntity(l,root) instanceof CitDoorEntity e?e:null;
        }
        if(loadedEntity(l,p) instanceof CitSwingEntity e&&e.owner()!=null)
            return loadedEntity(l,e.owner()) instanceof CitDoorEntity root?root:null;
        return null;
    }
    // Nunca carga un chunk del controlador desde una consulta de forma o de luz.
    private static BlockEntity loadedEntity(BlockGetter level,BlockPos pos){
        if(level instanceof net.minecraft.server.level.ServerLevel server){
            var chunk=server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            return chunk==null?null:chunk.getBlockEntity(pos);
        }
        return level.getBlockEntity(pos);
    }
    // Enumera las celdas del marco fijo.
    public Set<BlockPos> frameCells(){
        var b=(CitDoorBlock)getBlockState().getBlock();Set<BlockPos> cells=new LinkedHashSet<>();
        for(int x=0;x<b.width;x++)for(int y=0;y<b.height;y++)cells.add(worldPosition.relative(getBlockState().getValue(CitDoorBlock.FACING).getClockWise(),x).above(y));
        return cells;
    }
    // Divide la hoja en colisiones por celda.
    public synchronized Map<BlockPos,VoxelShape> leafCells(boolean open){
        var s=getBlockState();var b=(CitDoorBlock)s.getBlock();
        // La hoja no cambia entre consultas de navegación, colisión y dibujo.
        if(leafState!=s){leafState=s;openLeaf=null;closedLeaf=null;collisionCells.clear();}
        if(open){
            if(openLeaf==null)openLeaf=Map.copyOf(CitDoorGeometry.leafCells(worldPosition,s.getValue(CitDoorBlock.FACING),b.width,b.height,s.getValue(CitDoorBlock.RIGHT),true));
            return openLeaf;
        }
        if(closedLeaf==null)closedLeaf=Map.copyOf(CitDoorGeometry.leafCells(worldPosition,s.getValue(CitDoorBlock.FACING),b.width,b.height,s.getValue(CitDoorBlock.RIGHT),false));
        return closedLeaf;
    }
    // Hasta seis celdas por marco; el estado nuevo descarta toda colisión anterior.
    // Renderizado y movimiento comparten cachés; el mismo bloqueo protege su invalidación.
    public synchronized VoxelShape shapeAt(BlockPos pos){
        var state=getBlockState();var block=(CitDoorBlock)state.getBlock();var leaf=leafCells(state.getValue(CitDoorBlock.OPEN));
        return collisionCells.computeIfAbsent(pos.immutable(),p->Shapes.or(
                CitDoorGeometry.frameShape(worldPosition,p,state.getValue(CitDoorBlock.FACING),block.width,block.height),
                leaf.getOrDefault(p,Shapes.empty())));
    }
    // Comprueba que la celda móvil pertenece a esta puerta.
    private boolean ownsProxy(BlockPos p){return level.getBlockEntity(p) instanceof CitSwingEntity e&&worldPosition.equals(e.owner());}
    // Comprueba que la celda pertenece a este marco.
    private boolean ownsFrame(BlockPos p){
        BlockState state=level.getBlockState(p);
        return state.is(getBlockState().getBlock())&&state.getValue(CitDoorBlock.FACING)==getBlockState().getValue(CitDoorBlock.FACING)
                &&worldPosition.equals(CitDoorBlock.rootPos(p,state));
    }
    // Valida el espacio antes de mover la puerta completa.
    public boolean toggle(Player player){
        if(level==null||level.isClientSide||!isRoot()||player.isSpectator()||player.distanceToSqr(worldPosition.getCenter())>64)return false;
        boolean open=!getBlockState().getValue(CitDoorBlock.OPEN);
        return moveLeaf(open,player);
    }
    // Consulta solo bloques; las entidades se comprueban al abrir de verdad.
    public boolean canOpenForNavigation(){return canMoveLeaf(true,null,false);}
    // El guía abre la misma hoja, sin editar etiquetas ni ignorar obstáculos.
    public boolean openForGuide(net.minecraft.world.entity.Mob guide){
        if(level==null||level.isClientSide||guide.level()!=level||!isRoot()
                ||!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(guide.getType()).toString().equals("universityguide:guide")
                ||guide.distanceToSqr(worldPosition.getCenter())>16)return false;
        return getBlockState().getValue(CitDoorBlock.OPEN)||moveLeaf(true,null);
    }
    private boolean canMoveLeaf(boolean open,Player player,boolean entities){
        if(level==null||!isRoot())return false;
        var frames=frameCells();var target=leafCells(open);
        for(BlockPos p:frames)if(!ownsFrame(p)||(player!=null&&!level.mayInteract(player,p)))return false;
        // Valida todas las celdas antes de modificar el mundo.
        for(var entry:target.entrySet()) {
            BlockPos p=entry.getKey();
            if(!level.hasChunkAt(p)||!level.isInWorldBounds(p)||!level.getWorldBorder().isWithinBounds(p)||(player!=null&&!level.mayInteract(player,p))
                    ||(!frames.contains(p)&&!ownsProxy(p)&&!level.isEmptyBlock(p))
                    ||(entities&&!level.isUnobstructed(null,entry.getValue().move(p.getX(),p.getY(),p.getZ()))))return false;
        }
        return true;
    }
    private boolean moveLeaf(boolean open,Player player){
        if(!canMoveLeaf(open,player,true))return false;
        var frames=frameCells();var target=leafCells(open);
        // Evita desmontajes recursivos durante el movimiento.
        changing=true;
        try {
            for(BlockPos p:leafCells(!open).keySet())if(!frames.contains(p)&&!target.containsKey(p)&&ownsProxy(p))level.removeBlock(p,false);
            for(BlockPos p:frames)level.setBlock(p,level.getBlockState(p).setValue(CitDoorBlock.OPEN,open),Block.UPDATE_ALL);
            for(BlockPos p:target.keySet())if(!frames.contains(p)){
                if(!ownsProxy(p))level.setBlock(p,PostedSignage.CIT_DOOR_SWING.get().defaultBlockState(),Block.UPDATE_ALL);
                if(level.getBlockEntity(p) instanceof CitSwingEntity e)e.setOwner(worldPosition);
            }
        } finally {changing=false;}
        sync();
        level.playSound(null,worldPosition,open?SoundEvents.IRON_DOOR_OPEN:SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,1,1);
        level.gameEvent(player,open?net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN:net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE,worldPosition);
        return true;
    }
    // Retira el conjunto y genera como máximo un objeto.
    public void dismantle(boolean drop){
        if(level==null||level.isClientSide||changing||removing||!isRoot())return;
        removing=true;
        ItemStack stack=asItem();
        Set<BlockPos> cells=new LinkedHashSet<>(frameCells());
        for(BlockPos p:leafCells(true).keySet())if(ownsProxy(p))cells.add(p);
        // Conserva el controlador hasta terminar la retirada de las piezas.
        cells.remove(worldPosition);
        for(BlockPos p:cells)if(ownsFrame(p)||ownsProxy(p))level.removeBlock(p,false);
        if(ownsFrame(worldPosition))level.removeBlock(worldPosition,false);
        if(drop)Block.popResource(level,worldPosition,stack);
    }
    // Crea el objeto recuperable con su etiqueta CIT.
    public ItemStack asItem(){
        ItemStack stack=new ItemStack(getBlockState().getBlock());
        if(!hasLabel())return stack;
        CompoundTag label=new CompoundTag();
        label.putString("Room",room);label.putInt("Color",color);CompoundTag data=new CompoundTag();data.put("CitDoor",label);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return stack;
    }
    // Recupera la etiqueta almacenada en el objeto.
    public void readItem(ItemStack stack){
        if(!hasLabel())return;
        CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("CitDoor");
        if(validRoom(data.getString("Room")))room=data.getString("Room");
        if(data.contains("Color")&&data.getInt("Color")>=0&&data.getInt("Color")<COLORS.length)color=data.getInt("Color");
    }
    // Crea una sesión temporal para un editor autorizado.
    public UUID beginEdit(Player player){
        if(level==null||level.isClientSide||!hasLabel()||!isRoot()||!CitAccess.canEditAt(player,worldPosition))return null;
        editor=player.getUUID();token=UUID.randomUUID();expires=level.getGameTime()+1200;return token;
    }
    // Envía al jugador los datos de la sesión de edición.
    public void openEditor(ServerPlayer player){
        UUID nonce=beginEdit(player);if(nonce!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new CitDoorPayloads.Open(worldPosition,room,color,nonce));
    }
    // Revalida sesión, permisos, distancia y etiqueta.
    public boolean saveEdit(Player player,UUID nonce,String value,int selected){
        if(level==null||level.isClientSide||!hasLabel()||level.getBlockEntity(worldPosition)!=this||!isRoot()
                ||!CitAccess.canEditAt(player,worldPosition)||!player.getUUID().equals(editor)||!Objects.equals(token,nonce)
                ||level.getGameTime()>expires||!validRoom(value)||selected<0||selected>=COLORS.length)return false;
        room=value;color=selected;editor=null;token=null;sync();return true;
    }
    // Marca los cambios y actualiza los clientes.
    public void sync(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    // Guarda los datos del bloque en el mundo.
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registry){super.saveAdditional(tag,registry);tag.putString("Room",room);tag.putInt("Color",color);}
    // Restaura los datos guardados del bloque.
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registry){super.loadAdditional(tag,registry);room=validRoom(tag.getString("Room"))?tag.getString("Room"):"525";color=tag.contains("Color")?Math.floorMod(tag.getInt("Color"),COLORS.length):3;}
    // Prepara los datos que recibe el cliente.
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registry){return saveWithoutMetadata(registry);}
    // Crea el paquete de sincronización del bloque.
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
