package com.postedsignage.client;

import com.postedsignage.PostedSignage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Registra renderizadores de entidades solo en el cliente. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID, value = Dist.CLIENT)
public final class PostedSignageClient {
    // Evita crear instancias de esta clase de utilidades.
    private PostedSignageClient() {}
    // El inventario conserva su modelo; solo los estados colocados reciben marcos de terreno.
    @SubscribeEvent
    public static void bakeFrames(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event){
        var sprite=event.getTextureGetter().apply(new net.minecraft.client.resources.model.Material(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID,"block/cit_glass_panel_white")));
        var cache=CitFrameModel.newCache();
        for(var block:new net.minecraft.world.level.block.Block[]{PostedSignage.CIT_GLASS.get(),PostedSignage.CIT_SHORT_DOOR.get(),
                PostedSignage.CIT_WIDE_DOOR.get(),PostedSignage.CLEAR_SHORT_DOOR.get(),PostedSignage.CLEAR_WIDE_DOOR.get()})
            for(var state:block.getStateDefinition().getPossibleStates()){
                var key=net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
                var original=event.getModels().get(key);
                if(original!=null)event.getModels().put(key,new CitFrameModel(original,sprite,cache));
            }
    }
    // Cada frame consulta de nuevo los vecinos de soportes dinámicos.
    @SubscribeEvent
    public static void beginFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event){TextureSkinRenderer.beginFrame();}

    // Un resource pack nuevo invalida sprites y modelos guardados.
    @SubscribeEvent
    public static void atlasReloaded(net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent event){
        if(event.getAtlas().location().equals(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)){
            CitGeometryRenderer.clearCache();TextureSkinRenderer.clearCache();CustomizableHangingSignRenderer.clearCache();CitDoorRenderer.clearCache();
        }
    }

    // Asocia cada menú con su pantalla de cliente.
    @SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(PostedSignage.TRASH_BIN_MENU.get(), TrashBinScreen::new);
    }

    /** Asocia cada entidad con su renderizador del cliente. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PostedSignage.CIT_DOOR_ENTITY.get(), CitDoorRenderer::new);
        event.registerBlockEntityRenderer(PostedSignage.CIT_GLASS_ENTITY.get(), CitGlassRenderer::new);
        event.registerBlockEntityRenderer(PostedSignage.TEXTURE_COVER_ENTITY.get(), TextureCoverRenderer::new);
        event.registerEntityRenderer(PostedSignage.TEXTURE_SKIN.get(), TextureSkinRenderer::new);
        event.registerBlockEntityRenderer(
                PostedSignage.CUSTOMIZABLE_HANGING_SIGN_BLOCK_ENTITY.get(),
                CustomizableHangingSignRenderer::new);
    }
}
