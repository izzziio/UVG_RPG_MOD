package com.postedsignage.client;

import com.postedsignage.PostedSignage;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** El contorno de cada celda no debe simular divisiones en el vidrio continuo. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID, value = Dist.CLIENT)
public final class CitGlassHighlight {
    private CitGlassHighlight() {}

    // Oculta solo el dibujo de selección; conserva la selección y la colisión.
    @SubscribeEvent
    public static void highlight(RenderHighlightEvent.Block event) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(event.getTarget().getBlockPos()).is(PostedSignage.CIT_GLASS.get()))
            event.setCanceled(true);
    }
}
