package gt.edu.uvg.universityguide.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gt.edu.uvg.universityguide.entity.GuideEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Renders account skins on wide/slim player models and draws the animated guide indicator above the NPC. */
public final class GuideRenderer extends MobRenderer<GuideEntity, PlayerModel<GuideEntity>> {
    private static final Component INDICATOR = Component.literal(" ! ").withStyle(ChatFormatting.BOLD);
    private static final double INDICATOR_FULL_OPACITY_DISTANCE = 64.0;
    private static final double INDICATOR_MAX_DISTANCE = 96.0;
    private static final int INDICATOR_GOLD = 0xFFFF55;
    private static final int INDICATOR_BACKGROUND = 0x171006;

    private final PlayerModel<GuideEntity> wideModel;
    private final PlayerModel<GuideEntity> slimModel;

    // Crea el renderizador del cliente.
    public GuideRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    /** Let Minecraft's skin manager cache textures and choose fallback skins. */
    private PlayerSkin skin(GuideEntity entity) {
        return Minecraft.getInstance().getSkinManager().getInsecureSkin(entity.getSkinProfile());
    }

    // Elige la textura del guía según su perfil.
    @Override
    public ResourceLocation getTextureLocation(GuideEntity entity) {
        return skin(entity).texture();
    }

    /** Switch arm geometry to match the resolved skin before drawing the NPC and floating indicator. */
    @Override
    public void render(GuideEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        model = skin(entity).model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
        model.setAllVisible(true);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        renderIndicator(entity, partialTicks, poseStack, buffer);
    }

    /** Billboard the indicator toward the camera and fade it at long distance to limit visual clutter. */
    private void renderIndicator(GuideEntity entity, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffer) {
        double distanceSquared = entityRenderDispatcher.distanceToSqr(entity);
        if (distanceSquared > INDICATOR_MAX_DISTANCE * INDICATOR_MAX_DISTANCE) {
            return;
        }

        double distance = Math.sqrt(distanceSquared);
        float fade = distance <= INDICATOR_FULL_OPACITY_DISTANCE
                ? 1.0F
                : (float)((INDICATOR_MAX_DISTANCE - distance)
                        / (INDICATOR_MAX_DISTANCE - INDICATOR_FULL_OPACITY_DISTANCE));
        fade = Mth.clamp(fade, 0.0F, 1.0F);

        float animationTime = entity.tickCount + partialTick + entity.getId() * 2.75F;
        float bob = Mth.sin(animationTime * 0.12F) * 0.08F;
        float pulse = 1.0F + Mth.sin(animationTime * 0.18F) * 0.07F;
        float wobble = Mth.sin(animationTime * 0.09F) * 2.5F;

        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + 1.0F + bob, 0.0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(wobble));
        poseStack.scale(0.05F * pulse, -0.05F * pulse, 0.05F * pulse);

        Font font = getFont();
        float textX = -font.width(INDICATOR) / 2.0F;
        float textY = -font.lineHeight / 2.0F;
        Matrix4f matrix = poseStack.last().pose();

        font.drawInBatch(
                INDICATOR,
                textX,
                textY,
                withAlpha(INDICATOR_GOLD, fade * 0.70F),
                false,
                matrix,
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                withAlpha(INDICATOR_BACKGROUND, fade * 0.25F),
                LightTexture.FULL_BRIGHT
        );
        font.drawInBatch(
                INDICATOR,
                textX,
                textY,
                withAlpha(INDICATOR_GOLD, fade),
                true,
                matrix,
                buffer,
                Font.DisplayMode.NORMAL,
                withAlpha(INDICATOR_BACKGROUND, fade * 0.72F),
                LightTexture.FULL_BRIGHT
        );
        poseStack.popPose();
    }

    /** Pack a clamped opacity into the ARGB color expected by the font renderer. */
    private static int withAlpha(int rgb, float alpha) {
        return Mth.clamp((int)(alpha * 255.0F), 0, 255) << 24 | rgb;
    }
}
