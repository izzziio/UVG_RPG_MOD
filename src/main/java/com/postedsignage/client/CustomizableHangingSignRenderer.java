package com.postedsignage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.postedsignage.CustomizableHangingSignBlock;
import com.postedsignage.CustomizableHangingSignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;

/** Draws only the user-editable lettering over the fixed textured sign face. */
public final class CustomizableHangingSignRenderer
        implements BlockEntityRenderer<CustomizableHangingSignBlockEntity> {
    private static final int TEXT_COLOR = 0xFF252525;
    private static final float TEXT_SCALE = 0.0062F;

    private final Font font;

    public CustomizableHangingSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(CustomizableHangingSignBlockEntity sign, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = sign.getBlockState().getValue(CustomizableHangingSignBlock.FACING);
        float rotation = switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> -180.0F;
            case WEST -> -270.0F;
            default -> 0.0F;
        };

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        renderFace(sign.getFrontText(), false, poseStack, buffers, packedLight);
        renderFace(sign.getBackText(), true, poseStack, buffers, packedLight);

        poseStack.popPose();
    }

    private void renderFace(SignText text, boolean back, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(back ? -0.055 : 0.055, 0.0, 0.008);
        poseStack.mulPose(Axis.YP.rotationDegrees(back ? -90.0F : 90.0F));
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        boolean filtered = Minecraft.getInstance().isTextFilteringEnabled();
        Component[] lines = text.getMessages(filtered);
        for (int i = 0; i < lines.length; i++) {
            Component line = lines[i];
            float x = -font.width(line) / 2.0F;
            float y = (i - 1.5F) * 10.0F;
            font.drawInBatch(line, x, y, TEXT_COLOR, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        }
        poseStack.popPose();
    }
}
