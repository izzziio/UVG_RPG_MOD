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
    private static int resourceGeneration;
    private record Label(String room,int height,int generation,net.minecraft.util.FormattedCharSequence text,float left,float scale) {}
    private final java.util.Map<CitDoorEntity,Label> labels=new java.util.WeakHashMap<>();
    // Invalida medidas cuando cambian las fuentes del pack.
    static void clearCache(){resourceGeneration++;}
    private Label label(CitDoorEntity entity,int height){
        var cached=labels.get(entity);
        if(cached!=null&&cached.room().equals(entity.room())&&cached.height()==height&&cached.generation()==resourceGeneration)return cached;
        var text=net.minecraft.network.chat.Component.literal("CIT-"+entity.room()).getVisualOrderText();
        int width=font.width(text);
        var result=new Label(entity.room(),height,resourceGeneration,text,-width/2F,Math.min(.042F,(height-.6F)/Math.max(1,width)));
        labels.put(entity,result);return result;
    }
    private record Geometry(CitGeometryRenderer.Batch left,CitGeometryRenderer.Batch right) {}
    private static final java.util.Map<CitDoorBlock,Geometry> GEOMETRY=new java.util.IdentityHashMap<>();
    // Crea el renderizador del cliente.
    public CitDoorRenderer(BlockEntityRendererProvider.Context c){font=c.getFont();}
    // Dibuja el marco fijo y la hoja con su etiqueta giratoria.
    @Override public void render(CitDoorEntity e,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!e.isRoot())return;
        var s=e.getBlockState();var b=(CitDoorBlock)s.getBlock();boolean right=s.getValue(CitDoorBlock.RIGHT);
        var geometry=GEOMETRY.computeIfAbsent(b,block->new Geometry(
                new CitGeometryRenderer.Batch(CitDoorGeometry.leaf(block.width,block.height,false),false),new CitGeometryRenderer.Batch(CitDoorGeometry.leaf(block.width,block.height,true),false)));
        pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(180-s.getValue(CitDoorBlock.FACING).toYRot()));pose.translate(-.5,0,-.5);
        // El marco permanece horneado por celda; aquí solo gira la hoja.
        if(s.getValue(CitDoorBlock.OPEN)){
            double hinge=right?b.width-.12:.12;
            pose.translate(hinge,0,.5);pose.mulPose(Axis.YP.rotationDegrees(right?-90:90));pose.translate(-hinge,0,-.5);
        }
        (right?geometry.right():geometry.left()).draw(pose,buffers,light);
        if(!e.hasLabel()){pose.popPose();return;}
        if(!SignageClientConfig.textVisible(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(e.getBlockPos().getX()+.5,e.getBlockPos().getY()+.5,e.getBlockPos().getZ()+.5))){pose.popPose();return;}
        var label=label(e,b.height);
        // Ajusta el texto para que quepa en la altura disponible.
        float scale=label.scale();
        double centerX=right?b.width-.44:.44;
        double depth=CitDoorGeometry.leafDepth(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(),
                e.getBlockPos(),s.getValue(CitDoorBlock.FACING),b.width,right,s.getValue(CitDoorBlock.OPEN));
        for(int side=0;side<2;side++){
            boolean back=side==1;
            // Conserva ambas caras si la cámara queda al ras de la hoja.
            if(back?depth<.476-1e-6:depth>.524+1e-6)continue;
            pose.pushPose();pose.translate(centerX,b.height/2.0,back?.524:.476);
            // Lee de abajo hacia arriba desde ambos lados de la misma bisagra.
            if(!back)pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.mulPose(Axis.ZP.rotationDegrees(90));pose.scale(scale,-scale,scale);
            font.drawInBatch(label.text(),label.left(),-font.lineHeight/2F,0xFF000000|CitDoorEntity.COLORS[e.color()],false,
                    pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
            pose.popPose();
        }
        pose.popPose();
    }
    // Permite dibujar piezas fuera de la celda principal.
    @Override public boolean shouldRenderOffScreen(CitDoorEntity e){return e.isRoot();}
    // Descarta auxiliares antes de consultar luz y enviar geometría.
    @Override public boolean shouldRender(CitDoorEntity e,net.minecraft.world.phys.Vec3 camera){
        return e.isRoot()&&BlockEntityRenderer.super.shouldRender(e,camera);
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(CitDoorEntity e){return e.renderBounds();}
    // Fija la distancia máxima de renderizado.
    @Override public int getViewDistance(){return 96;}
}
