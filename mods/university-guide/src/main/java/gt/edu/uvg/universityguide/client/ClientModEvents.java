package gt.edu.uvg.universityguide.client;

import gt.edu.uvg.universityguide.ModContent;
import gt.edu.uvg.universityguide.UniversityGuideMod;
import gt.edu.uvg.universityguide.client.renderer.GuideRenderer;
import gt.edu.uvg.universityguide.client.renderer.TourStopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only renderer registration; dedicated servers must never load the screen or renderer classes. */
@EventBusSubscriber(modid = UniversityGuideMod.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    // Evita crear instancias de esta clase de utilidades.
    private ClientModEvents() {
    }

    /** Bind each registered entity type to its client renderer during renderer setup. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.GUIDE.get(), GuideRenderer::new);
        event.registerBlockEntityRenderer(ModContent.TOUR_STOP_ENTITY.get(), TourStopRenderer::new);
    }
}
