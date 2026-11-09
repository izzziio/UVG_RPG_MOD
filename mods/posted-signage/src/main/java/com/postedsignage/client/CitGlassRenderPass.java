package com.postedsignage.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.postedsignage.PostedSignage;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Dibuja todo el cristal después de las entidades sin ocultarlas por profundidad. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID, value = Dist.CLIENT)
public final class CitGlassRenderPass {
    private static final RenderType GLASS = RenderType.create("posted_signage_glass", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderType.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderType.LIGHTMAP)
                    .setOverlayState(RenderType.OVERLAY)
                    .setWriteMaskState(RenderType.COLOR_WRITE)
                    .setOutputState(RenderType.PARTICLES_TARGET)
                    .createCompositeState(false));
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(1536));

    private CitGlassRenderPass() {}

    // Reúne paneles y hojas para ordenar sus caras transparentes de lejos a cerca.
    static VertexConsumer vertices() { return BUFFERS.getBuffer(GLASS); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) BUFFERS.endBatch();
    }
}
