package com.postedsignage;

import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

// Copia una cara de textura y la aplica a otra superficie.
public final class TextureApplicatorItem extends Item {
    // Conecta los registros y eventos del mod.
    public TextureApplicatorItem(Properties properties) { super(properties); }
    // Da prioridad al aplicador frente al bloque pulsado.
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context); // Sample/apply before chests, furnaces and other block interactions.
    }
    // Muestrea o aplica la textura según la interacción.
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (player == null || !player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.FAIL;
        BlockState clicked = level.getBlockState(pos);
        var stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            BlockState sample = clicked;
            Direction face = context.getClickedFace();
            if (level.getBlockEntity(pos) instanceof TextureCoverBlockEntity cover) {
                sample = cover.sample();
                face = cover.sampleFace();
            }
            if (sample.getRenderShape() != RenderShape.MODEL) return message(context, "invalid_sample");
            if (!level.isClientSide) {
                CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                data.put("Sample", NbtUtils.writeBlockState(sample));
                data.putString("SampleFace", face.getName());
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                player.displayClientMessage(Component.translatable("message.posted_signage.sampled", sample.getBlock().getName()), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains("Sample")) return message(context, "select_first");
        BlockState sample = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), data.getCompound("Sample"));
        if (sample.isAir() || sample.getRenderShape() != RenderShape.MODEL) return message(context, "invalid_sample");
        Direction sourceFace = Direction.byName(data.getString("SampleFace"));
        Direction face = context.getClickedFace();
        boolean repaint = clicked.is(PostedSignage.TEXTURE_COVER_BLOCK.get());
        BlockPos destination = repaint ? pos : pos.relative(face);
        if (!level.mayInteract(player, destination) || !player.mayUseItemAt(destination, face, stack)) return InteractionResult.FAIL;
        if (!repaint) {
            if (!clicked.isFaceSturdy(level, pos, face) || !level.isEmptyBlock(destination) || level.isOutsideBuildHeight(destination)) return message(context, "no_space");
        }
        if (!level.isClientSide) {
            if (!repaint) level.setBlockAndUpdate(destination, PostedSignage.TEXTURE_COVER_BLOCK.get().defaultBlockState().setValue(TextureCoverBlock.FACING, face));
            if (level.getBlockEntity(destination) instanceof TextureCoverBlockEntity cover) {
                Direction surface = cover.getBlockState().getValue(TextureCoverBlock.FACING);
                cover.setSample(sample, sourceFace == null ? Direction.NORTH : sourceFace,
                        placementRotation(surface, destination, player));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    /** Horizontal surfaces read upright from the player's side; vertical faces stay upright. */
    static int placementRotation(Direction surface, BlockPos destination, net.minecraft.world.entity.player.Player player) {
        if (surface.getAxis() != Direction.Axis.Y) return 0;
        double dx = player.getX() - (destination.getX() + 0.5);
        double dz = player.getZ() - (destination.getZ() + 0.5);
        Direction top;
        if (dx * dx + dz * dz < 0.0001) {
            top = player.getDirection();
        } else {
            Direction towardPlayer = Math.abs(dx) > Math.abs(dz)
                    ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            top = towardPlayer.getOpposite();
        }
        // The renderer's unrotated top edge points north on floors and south on ceilings.
        return switch (top) {
            case EAST -> 1;
            case WEST -> 3;
            case NORTH -> surface == Direction.UP ? 0 : 2;
            case SOUTH -> surface == Direction.UP ? 2 : 0;
            default -> 0;
        };
    }
    // Muestra al jugador el resultado de la aplicación.
    private InteractionResult message(UseOnContext context, String key) {
        if (!context.getLevel().isClientSide) context.getPlayer().displayClientMessage(Component.translatable("message.posted_signage." + key), true);
        return InteractionResult.FAIL;
    }
    // Muestra la selección de textura en la descripción.
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.posted_signage.texture_applicator"));
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains("Sample")) tooltip.add(Component.translatable("tooltip.posted_signage.sample", data.getCompound("Sample").getString("Name"), data.getString("SampleFace")));
    }
}
