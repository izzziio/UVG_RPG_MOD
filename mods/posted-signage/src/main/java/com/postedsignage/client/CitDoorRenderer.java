package com.postedsignage.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.postedsignage.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;

// Dibuja marco, hoja y etiqueta de la puerta CIT.
public final class CitDoorRenderer implements BlockEntityRenderer<CitDoorEntity> {
    private final Font font;
    // Crea el renderizador del cliente.
    public CitDoorRenderer(BlockEntityRendererProvider.Context c){font=c.getFont();}
    // Dibuja el marco fijo y la hoja con su etiqueta giratoria.
    @Override public void render(CitDoorEntity e,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!e.isRoot())return;
        var s=e.getBlockState();var b=(CitDoorBlock)s.getBlock();boolean right=s.getValue(CitDoorBlock.RIGHT);
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(180-s.getValue(CitDoorBlock.FACING).toYRot()));pose.translate(-.5,0,-.5);
        // Dibuja el marco antes de aplicar el giro de la hoja.
        for(var p:CitDoorGeometry.frame(b.width,b.height))CitGeometryRenderer.piece(p,pose,buffers,light);
        if(s.getValue(CitDoorBlock.OPEN)){
            double hinge=right?b.width-.12:.12;
            pose.translate(hinge,0,.5);pose.mulPose(Axis.YP.rotationDegrees(right?-90:90));pose.translate(-hinge,0,-.5);
        }
        for(var p:CitDoorGeometry.leaf(b.width,b.height,right))CitGeometryRenderer.piece(p,pose,buffers,light);
        if(!e.hasLabel()){pose.popPose();return;}
        String text="CIT-"+e.room();
        // Ajusta el texto para que quepa en la altura disponible.
        float scale=Math.min(.042F,(b.height-.6F)/font.width(text));
        double centerX=right?b.width-.44:.44;
        for(boolean back:new boolean[]{false,true}){
            pose.pushPose();pose.translate(centerX,b.height/2.0,back?.524:.476);
            // Same physical hinge edge, readable bottom-to-top from either side.
            if(!back)pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.mulPose(Axis.ZP.rotationDegrees(90));pose.scale(scale,-scale,scale);
            font.drawInBatch(text,-font.width(text)/2F,-font.lineHeight/2F,0xFF000000|CitDoorEntity.COLORS[e.color()],false,
                    pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
            pose.popPose();
        }
        pose.popPose();
    }
    // Permite dibujar piezas fuera de la celda principal.
    @Override public boolean shouldRenderOffScreen(CitDoorEntity e){return true;}
    // Fija la distancia máxima de renderizado.
    @Override public int getViewDistance(){return 96;}
}
