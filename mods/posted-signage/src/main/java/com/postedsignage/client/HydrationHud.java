package com.postedsignage.client;

import com.postedsignage.Hydration;
import com.postedsignage.PostedSignage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Indicador de diez gotas situado después del espacio de comida y aire. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID, value = Dist.CLIENT)
public final class HydrationHud {
    /** Reserva una fila adicional del HUD después del aire y la comida para no encimar las barras vanilla. */
    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID, "hydration"), (gui, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.player == null || !Hydration.eligible(mc.player)) return;
            int right = gui.guiWidth() / 2 + 91;
            // NeoForge reserva espacio para comida, aire y salud de monturas.
            int y = gui.guiHeight() - mc.gui.rightHeight;
            mc.gui.rightHeight += 10;
            int value = Hydration.level(mc.player);
            for (int i = 0; i < 10; i++) {
                int x = right - 9 - i * 8;
                // Cada gota representa dos puntos de hidratación.
                int fill = Math.clamp(value - (9 - i) * 2, 0, 2);
                String sprite = fill == 2 ? "full" : fill == 1 ? "half" : "empty";
                gui.blitSprite(ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID, "hydration/" + sprite), x, y, 9, 9);
            }
        });
    }
}
