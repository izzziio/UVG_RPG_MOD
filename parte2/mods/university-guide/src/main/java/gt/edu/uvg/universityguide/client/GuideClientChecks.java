package gt.edu.uvg.universityguide.client;

import gt.edu.uvg.universityguide.UniversityGuideMod;
import gt.edu.uvg.universityguide.client.screen.UvgTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Captura de aceptación solo en desarrollo; no se incluye en el JAR. */
@EventBusSubscriber(modid=UniversityGuideMod.MOD_ID,value=Dist.CLIENT)
public final class GuideClientChecks {
    private static int frames;
    @SubscribeEvent public static void rendered(ScreenEvent.Render.Post event){
        if(!Boolean.getBoolean("universityguide.menuCheck")||!(event.getScreen() instanceof UvgTitleScreen)||++frames!=30)return;
        var client=Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory,"uvg-menu-116.png",client.getMainRenderTarget(),
                message->org.slf4j.LoggerFactory.getLogger(GuideClientChecks.class).info("Menu capture: {}",message.getString()));
    }
}
