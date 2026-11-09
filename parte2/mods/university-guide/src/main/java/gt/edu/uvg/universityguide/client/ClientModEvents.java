package gt.edu.uvg.universityguide.client;

import gt.edu.uvg.universityguide.ModContent;
import gt.edu.uvg.universityguide.UniversityGuideMod;
import gt.edu.uvg.universityguide.client.renderer.GuideRenderer;
import gt.edu.uvg.universityguide.client.renderer.TourStopRenderer;
import gt.edu.uvg.universityguide.client.renderer.GuideBarrierRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Registra renderizadores solo en el cliente, sin cargar pantallas en servidores. */
@EventBusSubscriber(modid = UniversityGuideMod.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    // Evita crear instancias de esta clase de utilidades.
    private ClientModEvents() {
    }

    /** Asocia cada entidad con su renderizador del cliente. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.GUIDE_BARRIER_ENTITY.get(), GuideBarrierRenderer::new);
        event.registerEntityRenderer(ModContent.GUIDE.get(), GuideRenderer::new);
        event.registerBlockEntityRenderer(ModContent.TOUR_STOP_ENTITY.get(), TourStopRenderer::new);
    }
}
