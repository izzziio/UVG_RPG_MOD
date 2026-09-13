package com.postedsignage.client;

import com.postedsignage.PostedSignage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = PostedSignage.MOD_ID, value = Dist.CLIENT)
public final class PostedSignageClient {
    private PostedSignageClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                PostedSignage.CUSTOMIZABLE_HANGING_SIGN_BLOCK_ENTITY.get(),
                CustomizableHangingSignRenderer::new);
    }
}
