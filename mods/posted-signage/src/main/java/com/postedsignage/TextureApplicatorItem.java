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
        return useOn(context); // Muestrea o aplica antes de abrir cofres, hornos u otros bloques.
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
            Direction half = TextureSkinSurface.halfAt(clicked,pos,context.getClickLocation());
            BlockState sample = TextureSkinSurface.sample(clicked,half);
            Direction face = context.getClickedFace();
            if (level.getBlockEntity(pos) instanceof TextureCoverBlockEntity cover) {
                sample = cover.sample();
                face = cover.sampleFace();
            }
            TextureSkinEntity skin = TextureSkinEntity.findAt(level, pos, face, context.getClickLocation());
            if (skin != null) { sample = skin.sample(); face = skin.sampleFace(); }
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
        // Las capas antiguas se convierten sin perder su soporte ni orientación.
        if (clicked.is(PostedSignage.TEXTURE_COVER_BLOCK.get())) {
            face = clicked.getValue(TextureCoverBlock.FACING);
            pos = pos.relative(face.getOpposite());
            clicked = level.getBlockState(pos);
        }
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, face, stack)) return InteractionResult.FAIL;
        if (clicked.isAir() || clicked.getShape(level, pos).isEmpty()) return message(context, "no_space");
        if (!level.isClientSide) {
            TextureSkinEntity.apply(level, pos, face, sample, sourceFace == null ? Direction.NORTH : sourceFace,
                    placementRotation(face, pos.relative(face), player),
                    TextureSkinSurface.halfAt(clicked,pos,context.getClickLocation()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    /** Mantiene las superficies horizontales legibles y las verticales derechas. */
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
        // El borde superior sin girar apunta al norte en suelos y al sur en techos.
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
