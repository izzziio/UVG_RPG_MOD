package com.postedsignage;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Nine persistent slots; held stacks are stored and the menu provides explicit disposal. */
public final class TrashBinBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

    // Inicializa los datos de esta entidad.
    public TrashBinBlockEntity(BlockPos pos, BlockState state) { super(PostedSignage.TRASH_BIN_ENTITY.get(), pos, state); }
    // Define las nueve ranuras del basurero.
    @Override public int getContainerSize() { return 9; }
    // Devuelve el contenido del inventario.
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    // Reemplaza el contenido del inventario.
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    // Obtiene el nombre traducido del basurero.
    @Override protected Component getDefaultName() { return Component.translatable("block.posted_signage.trash_bin"); }
    // Conecta el inventario con su menú.
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new TrashBinMenu(id, inventory, this);
    }
    // Extrae una ranura y marca el cambio para guardarlo.
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = super.removeItemNoUpdate(slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    // Vacía el inventario y marca los datos modificados.
    @Override public void clearContent() { super.clearContent(); setChanged(); }
    // Comprueba permisos, cercanía y acceso al contenedor.
    public boolean canAccess(Player player) {
        return level != null && !level.isClientSide() && player.mayBuild() && stillValid(player)
                && level.mayInteract(player, worldPosition) && canOpen(player);
    }
    // Guarda lo que cabe y deja el sobrante en la mano.
    public int storeHeldItems(Player player, InteractionHand hand) {
        if (!canAccess(player)) return 0;
        ItemStack held = player.getItemInHand(hand);
        int before = held.getCount();
        // Fill compatible stacks first, then vacant slots. Never consume overflow.
        for (int pass = 0; pass < 2 && !held.isEmpty(); pass++) {
            for (int slot = 0; slot < getContainerSize() && !held.isEmpty(); slot++) {
                ItemStack stored = getItem(slot);
                if (pass == 0 && !stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, held)) {
                    int moved = Math.min(held.getCount(), Math.max(0,
                            Math.min(getMaxStackSize(), stored.getMaxStackSize()) - stored.getCount()));
                    stored.grow(moved);
                    held.shrink(moved);
                } else if (pass == 1 && stored.isEmpty()) {
                    int moved = Math.min(held.getCount(), Math.min(getMaxStackSize(), held.getMaxStackSize()));
                    setItem(slot, held.split(moved));
                }
            }
        }
        int inserted = before - held.getCount();
        if (inserted > 0) {
            setChanged();
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return inserted;
    }
    // Guarda los datos del bloque en el mundo.
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }
    // Restaura los datos guardados del bloque.
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(9, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
