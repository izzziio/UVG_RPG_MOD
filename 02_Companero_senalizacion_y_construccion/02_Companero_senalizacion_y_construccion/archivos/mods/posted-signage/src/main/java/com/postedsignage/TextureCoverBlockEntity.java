package com.postedsignage;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores a sampled state and face, with vanilla chunk/update packet synchronization. */
public final class TextureCoverBlockEntity extends BlockEntity {
    private BlockState sample = Blocks.BRICKS.defaultBlockState();
    private Direction sampleFace = Direction.NORTH;
    private int rotation;
    // Inicializa los datos de esta entidad.
    public TextureCoverBlockEntity(BlockPos pos, BlockState state) { super(PostedSignage.TEXTURE_COVER_ENTITY.get(), pos, state); }
    // Devuelve el bloque usado como textura.
    public BlockState sample() { return sample; }
    // Devuelve la cara de textura seleccionada.
    public Direction sampleFace() { return sampleFace; }
    // Devuelve el giro guardado en cuartos de vuelta.
    public int rotation() { return rotation; }
    // Guarda la textura elegida y sincroniza el cambio.
    public void setSample(BlockState state, Direction face) {
        setSample(state, face, 0);
    }
    // Guarda la textura elegida y sincroniza el cambio.
    public void setSample(BlockState state, Direction face, int quarterTurns) {
        sample = state;
        sampleFace = face;
        rotation = Math.floorMod(quarterTurns, 4);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    // Guarda los datos del bloque en el mundo.
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Sample", NbtUtils.writeBlockState(sample));
        tag.putString("SampleFace", sampleFace.getName());
        tag.putInt("Rotation", rotation);
    }
    // Restaura los datos guardados del bloque.
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sample = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Sample"));
        Direction face = Direction.byName(tag.getString("SampleFace"));
        sampleFace = face == null ? Direction.NORTH : face;
        // Older covers did not store rotation and retain their original UV layout.
        rotation = Math.floorMod(tag.getInt("Rotation"), 4);
    }
    // Prepara los datos que recibe el cliente.
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    // Crea el paquete de sincronización del bloque.
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
