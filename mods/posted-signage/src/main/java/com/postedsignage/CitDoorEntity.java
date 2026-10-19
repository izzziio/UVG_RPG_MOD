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
    // Exact dominant colors from the seven existing CIT-sign atlas tiles.
    public static final int[] COLORS={0x005BD2,0x189C47,0xD9091C,0xF2B801,0x2FAAEE,0x632098,0xFE6101};
    // Etiqueta persistente; las sesiones de edición son temporales.
    private String room="525";
    private int color=3;
    private UUID editor,token;
    private long expires;
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
        BlockState s=l.getBlockState(p);
        if(s.getBlock() instanceof CitDoorBlock) {
            BlockPos root=CitDoorBlock.rootPos(p,s);
            return l.getBlockEntity(root) instanceof CitDoorEntity e?e:null;
        }
        if(l.getBlockEntity(p) instanceof CitSwingEntity e&&e.owner()!=null)
            return l.getBlockEntity(e.owner()) instanceof CitDoorEntity root?root:null;
        return null;
    }
    // Enumera las celdas del marco fijo.
    public Set<BlockPos> frameCells(){
        var b=(CitDoorBlock)getBlockState().getBlock();Set<BlockPos> cells=new LinkedHashSet<>();
        for(int x=0;x<b.width;x++)for(int y=0;y<b.height;y++)cells.add(worldPosition.relative(getBlockState().getValue(CitDoorBlock.FACING).getClockWise(),x).above(y));
        return cells;
    }
    // Divide la hoja en colisiones por celda.
    public Map<BlockPos,VoxelShape> leafCells(boolean open){
        var s=getBlockState();var b=(CitDoorBlock)s.getBlock();
        return CitDoorGeometry.leafCells(worldPosition,s.getValue(CitDoorBlock.FACING),b.width,b.height,s.getValue(CitDoorBlock.RIGHT),open);
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
        var frames=frameCells();var target=leafCells(open);
        for(BlockPos p:frames)if(!ownsFrame(p)||!level.mayInteract(player,p))return false;
        // Valida todas las celdas antes de modificar el mundo.
        for(var entry:target.entrySet()) {
            BlockPos p=entry.getKey();
            if(!level.hasChunkAt(p)||!level.isInWorldBounds(p)||!level.getWorldBorder().isWithinBounds(p)||!level.mayInteract(player,p)
                    ||(!frames.contains(p)&&!ownsProxy(p)&&!level.isEmptyBlock(p))
                    ||!level.isUnobstructed(null,entry.getValue().move(p.getX(),p.getY(),p.getZ())))return false;
        }
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
        // Keep the controller alive until all companion removal callbacks have completed.
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
