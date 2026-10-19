package com.postedsignage;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
// Guarda qué puerta controla esta celda móvil.
public final class CitSwingEntity extends BlockEntity {
    private BlockPos owner;
    // Inicializa los datos de esta entidad.
    public CitSwingEntity(BlockPos p,BlockState s){super(PostedSignage.CIT_SWING_ENTITY.get(),p,s);}
    // Devuelve la posición del controlador de la puerta.
    public BlockPos owner(){return owner;}
    // Vincula la celda móvil con su controlador.
    public void setOwner(BlockPos p){owner=p.immutable();setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    // Guarda los datos del bloque en el mundo.
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){super.saveAdditional(t,r);if(owner!=null)t.putLong("Owner",owner.asLong());}
    // Restaura los datos guardados del bloque.
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){super.loadAdditional(t,r);owner=t.contains("Owner")?BlockPos.of(t.getLong("Owner")):null;}
    // Prepara los datos que recibe el cliente.
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r){return saveWithoutMetadata(r);}
    // Crea el paquete de sincronización del bloque.
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
