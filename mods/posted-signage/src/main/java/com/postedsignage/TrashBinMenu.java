package com.postedsignage;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Standard container synchronization and server-validated empty button. */
public final class TrashBinMenu extends AbstractContainerMenu {
    private final Container bin;
    // Prepara las ranuras del basurero y del jugador.
    public TrashBinMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(9)); }
    // Prepara las ranuras del basurero y del jugador.
    public TrashBinMenu(int id, Inventory inventory, Container bin) {
        super(PostedSignage.TRASH_BIN_MENU.get(), id);
        checkContainerSize(bin, 9);
        this.bin = bin;
        bin.startOpen(inventory.player);
        // Primero el basurero, luego la mochila y la barra rápida.
        for (int i = 0; i < 9; i++) addSlot(new Slot(bin, i, 8 + i * 18, 20));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 88 + row * 18));
        for (int i = 0; i < 9; i++) addSlot(new Slot(inventory, i, 8 + i * 18, 146));
    }
    // Comprueba que el jugador aún puede usar el menú.
    @Override public boolean stillValid(Player player) {
        return bin instanceof TrashBinBlockEntity entity ? entity.canAccess(player) : bin.stillValid(player);
    }
    // Valida el botón y vacía solo el basurero.
    @Override public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || player.containerMenu != this || !(bin instanceof TrashBinBlockEntity entity)
                || !entity.canAccess(player) || bin.isEmpty()) return false;
        bin.clearContent();
        bin.setChanged();
        broadcastChanges();
        return true;
    }
    // Transfiere objetos con Mayús entre ambos inventarios.
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack current = slot.getItem();
        ItemStack original = current.copy();
        if (!(index < 9 ? moveItemStackTo(current, 9, 45, true) : moveItemStackTo(current, 0, 9, false)))
            return ItemStack.EMPTY;
        if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, current);
        return original;
    }
    // Notifica al contenedor que el jugador cerró el menú.
    @Override public void removed(Player player) {
        super.removed(player);
        bin.stopOpen(player);
    }
}
