package com.postedsignage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Gestiona depósitos, apertura y retirada del basurero.
public final class TrashBinBlock extends FacilityMultiBlock implements EntityBlock {
    public static final MapCodec<TrashBinBlock> CODEC = simpleCodec(TrashBinBlock::new);
    // Configura las propiedades iniciales del bloque.
    public TrashBinBlock(Properties properties) { super(properties, 1, 1); }
    // Permite reconstruir el bloque desde su registro.
    @Override protected MapCodec<TrashBinBlock> codec() { return CODEC; }
    // Crea los datos asociados al bloque colocado.
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TrashBinBlockEntity(pos, state); }
    // Deposita los objetos de la mano en el basurero.
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrashBinBlockEntity bin) {
            if (!bin.canAccess(player)) return ItemInteractionResult.FAIL;
            int inserted = bin.storeHeldItems(player, hand);
            if (inserted == 0) player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.posted_signage.bin_full"), true);
        }
        // Consume even when full so held blocks, buckets and food are not used accidentally.
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    // Abre el inventario al usar la mano vacía.
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrashBinBlockEntity bin) {
            if (!bin.canAccess(player)) return InteractionResult.FAIL;
            player.openMenu(bin);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    // Suelta el contenido guardado al retirar el basurero.
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof TrashBinBlockEntity bin) {
            Containers.dropContents(level, pos, bin);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    // Devuelve los límites de selección y colisión.
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(1, 0, 1, 15, 16, 15);
    }
}
