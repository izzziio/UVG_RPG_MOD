package com.postedsignage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.postedsignage.TextureCoverBlock;
import com.postedsignage.TextureCoverBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** Uses the sampled face's atlas sprite, including animated/resource-pack textures. */
public final class TextureCoverRenderer implements BlockEntityRenderer<TextureCoverBlockEntity> {
    // Crea el renderizador del cliente.
    public TextureCoverRenderer(BlockEntityRendererProvider.Context context) {}
    // Dibuja la cara copiada con su orientación e iluminación.
    @Override public void render(TextureCoverBlockEntity cover, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var minecraft = Minecraft.getInstance();
        var state = cover.sample();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var quads = model.getQuads(state, cover.sampleFace(), RandomSource.create(42), net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
        if (quads.isEmpty()) quads = model.getQuads(state, null, RandomSource.create(42), net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null).stream().filter(q -> q.getDirection() == cover.sampleFace()).toList();
        var sprite = quads.isEmpty() ? model.getParticleIcon(net.neoforged.neoforge.client.model.data.ModelData.EMPTY) : quads.getFirst().getSprite();
        int tint = !quads.isEmpty() && quads.getFirst().isTinted()
                ? minecraft.getBlockColors().getColor(state, cover.getLevel(), cover.getBlockPos(), quads.getFirst().getTintIndex()) : 0xFFFFFF;
        Direction face = cover.getBlockState().getValue(TextureCoverBlock.FACING);
        float e = 0.00125F;
        float[][] vertices = switch (face) {
            case NORTH -> new float[][]{{1,0,1-e},{0,0,1-e},{0,1,1-e},{1,1,1-e}};
            case SOUTH -> new float[][]{{0,0,e},{1,0,e},{1,1,e},{0,1,e}};
            case WEST -> new float[][]{{1-e,0,0},{1-e,0,1},{1-e,1,1},{1-e,1,0}};
            case EAST -> new float[][]{{e,0,1},{e,0,0},{e,1,0},{e,1,1}};
            case UP -> new float[][]{{0,e,1},{1,e,1},{1,e,0},{0,e,0}};
            case DOWN -> new float[][]{{0,1-e,0},{1,1-e,0},{1,1-e,1},{0,1-e,1}};
        };
        // Use the block shader: the entity shader applies a second directional-light
        // multiplier on top of getShade(), making covers unnaturally dark. The block
        // shader applies the supplied world lightmap without that extra shading.
        var consumer = buffers.getBuffer(RenderType.cutout());
        float shade = cover.getLevel() == null ? 1 : cover.getLevel().getShade(face, true);
        for (int i = 0; i < 4; i++) {
            int uv = (i + cover.rotation()) % 4;
            consumer.addVertex(pose.last(), vertices[i][0], vertices[i][1], vertices[i][2])
                    .setColor((int)(((tint >> 16) & 255) * shade), (int)(((tint >> 8) & 255) * shade), (int)((tint & 255) * shade), 255)
                    .setUv(uv == 0 || uv == 3 ? sprite.getU0() : sprite.getU1(), uv < 2 ? sprite.getV1() : sprite.getV0())
                    .setLight(light)
                    .setNormal(pose.last(), face.getStepX(), face.getStepY(), face.getStepZ());
        }
    }
}
