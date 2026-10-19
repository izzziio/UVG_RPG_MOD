package gt.edu.uvg.universityguide.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import gt.edu.uvg.universityguide.block.entity.TourStopBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Draws the green editor outline only for players allowed to manage tour stops. */
public final class TourStopRenderer implements BlockEntityRenderer<TourStopBlockEntity> {
    // Crea el renderizador del cliente.
    public TourStopRenderer(BlockEntityRendererProvider.Context context) {
    }

    // Dibuja el marcador de parada para jugadores autorizados.
    @Override
    public void render(TourStopBlockEntity stop, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (Minecraft.getInstance().player == null || !Minecraft.getInstance().player.canUseGameMasterBlocks()) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines,
                0.2, 0.02, 0.2, 0.8, 1.0, 0.8,
                0.15F, 0.95F, 0.35F, 1.0F,
                0.15F, 0.95F, 0.35F);
    }
}
