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
import net.minecraft.util.FormattedCharSequence;

/** Dibuja únicamente el texto editable sobre el panel fijo. */
public final class CustomizableHangingSignRenderer
        implements BlockEntityRenderer<CustomizableHangingSignBlockEntity> {
    private static final int TEXT_COLOR = 0xFF252525;
    private static final float TEXT_SCALE = 0.0062F;

    private final Font font;
    private static int resourceGeneration;
    private final java.util.Map<SignText,FaceLayout> layouts=new java.util.WeakHashMap<>();
    private record FaceLayout(boolean filtered,int generation,FormattedCharSequence[] lines,float[] left,boolean[] empty) {}

    // Una recarga puede cambiar el ancho de los caracteres de la fuente.
    static void clearCache(){resourceGeneration++;}

    private FaceLayout layout(SignText text,boolean filtered){
        var cached=layouts.get(text);
        if(cached!=null&&cached.filtered()==filtered&&cached.generation()==resourceGeneration)return cached;
        var lines=text.getRenderMessages(filtered,Component::getVisualOrderText);
        float[] left=new float[lines.length];boolean[] empty=new boolean[lines.length];
        for(int i=0;i<lines.length;i++){
            left[i]=-font.width(lines[i])/2F;
            empty[i]=text.getMessage(i,filtered).getString().isEmpty();
        }
        var result=new FaceLayout(filtered,resourceGeneration,lines,left,empty);
        layouts.put(text,result);return result;
    }

    // Crea el renderizador del cliente.
    public CustomizableHangingSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    /** Orienta el bloque y dibuja por separado ambas caras del texto. */
    @Override
    public void render(CustomizableHangingSignBlockEntity sign, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = sign.getBlockState().getValue(CustomizableHangingSignBlock.FACING);
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if(!com.postedsignage.SignageClientConfig.textVisible(camera.distanceToSqr(
                sign.getBlockPos().getX()+.5,sign.getBlockPos().getY()+.5,sign.getBlockPos().getZ()+.5)))return;
        float rotation = switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> -180.0F;
            case WEST -> -270.0F;
            default -> 0.0F;
        };

        poseStack.pushPose();
        var mount=com.postedsignage.GlassSignMount.offset(sign.getBlockState(),facing);
        poseStack.translate(0.5+mount.x, 0.5, 0.5+mount.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        double x=camera.x-sign.getBlockPos().getX()-.5-mount.x;
        double z=camera.z-sign.getBlockPos().getZ()-.5-mount.z;
        // Omite únicamente el texto que queda detrás del tablero opaco.
        if(com.postedsignage.GlassSignMount.textFaceVisible(facing,false,x,z))
            renderFace(sign.getFrontText(), false, poseStack, buffers, packedLight);
        if(com.postedsignage.GlassSignMount.textFaceVisible(facing,true,x,z))
            renderFace(sign.getBackText(), true, poseStack, buffers, packedLight);

        poseStack.popPose();
    }

    /** Separa y orienta las letras; respeta el filtro de texto del jugador. */
    private void renderFace(SignText text, boolean back, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(back ? -0.055 : 0.055, 0.0, 0.008);
        poseStack.mulPose(Axis.YP.rotationDegrees(back ? -90.0F : 90.0F));
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        boolean filtered = Minecraft.getInstance().isTextFilteringEnabled();
        var layout=layout(text,filtered);
        for (int i = 0; i < layout.lines().length; i++) {
            if(layout.empty()[i])continue;
            float x = layout.left()[i];
            float y = (i - 1.5F) * 10.0F;
            font.drawInBatch(layout.lines()[i], x, y, TEXT_COLOR, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, packedLight);
        }
        poseStack.popPose();
    }
}
