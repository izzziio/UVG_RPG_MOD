package com.postedsignage.client;

import com.postedsignage.PostedSignage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Registers block-entity renderers only on the client; static block models are handled by Minecraft. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID, value = Dist.CLIENT)
public final class PostedSignageClient {
    // Evita crear instancias de esta clase de utilidades.
    private PostedSignageClient() {}

    // Asocia cada menú con su pantalla de cliente.
    @SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(PostedSignage.TRASH_BIN_MENU.get(), TrashBinScreen::new);
    }

    /** Bind each registered entity type to its client renderer during renderer setup. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PostedSignage.CIT_DOOR_ENTITY.get(), CitDoorRenderer::new);
        event.registerBlockEntityRenderer(PostedSignage.CIT_GLASS_ENTITY.get(), CitGlassRenderer::new);
        event.registerBlockEntityRenderer(PostedSignage.TEXTURE_COVER_ENTITY.get(), TextureCoverRenderer::new);
        event.registerBlockEntityRenderer(
                PostedSignage.CUSTOMIZABLE_HANGING_SIGN_BLOCK_ENTITY.get(),
                CustomizableHangingSignRenderer::new);
    }
}
