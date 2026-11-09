package gt.edu.uvg.universityguide.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import gt.edu.uvg.universityguide.block.GuideBarrierBlock;
import gt.edu.uvg.universityguide.block.entity.GuideBarrierBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** La textura de advertencia nunca aparece para visitantes normales. */
public final class GuideBarrierRenderer implements BlockEntityRenderer<GuideBarrierBlockEntity> {
    public GuideBarrierRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(GuideBarrierBlockEntity entity, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!GuideBarrierBlock.canEdit(Minecraft.getInstance().player)) return;
        var renderer = Minecraft.getInstance().getBlockRenderer();
        renderer.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.solid()),entity.getBlockState(),
                renderer.getBlockModel(entity.getBlockState()),1,1,1,light,overlay);
    }
}
