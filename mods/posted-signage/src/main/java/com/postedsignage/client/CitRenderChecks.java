package com.postedsignage.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.postedsignage.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Verificación local del envío rápido; excluida del JAR distribuible. */
@EventBusSubscriber(modid=PostedSignage.MOD_ID,value=Dist.CLIENT)
public final class CitRenderChecks {
    private static boolean checked;
    private static boolean framesChecked;
    private CitRenderChecks(){}
    @SubscribeEvent public static void check(RenderFrameEvent.Post event){
        if(!Boolean.getBoolean("postedsignage.renderChecks"))return;
        if(!framesChecked&&net.minecraft.client.Minecraft.getInstance().level!=null){checkTerrain();framesChecked=true;}
        if(checked)return;checked=true;
        int cases=0;
        for(var format:new VertexFormat[]{DefaultVertexFormat.BLOCK,DefaultVertexFormat.NEW_ENTITY})
            for(boolean glass:new boolean[]{false,true})for(int variant=0;variant<3;variant++){
                var pose=new PoseStack();pose.translate(-123.5,72.25,300.75);
                pose.mulPose(Axis.YP.rotationDegrees(37));pose.mulPose(Axis.XP.rotationDegrees(-19));
                if(variant==1)pose.scale(2,2,2);if(variant==2)pose.scale(.6F,1.3F,-.8F);
                var mesh=CitGeometryMesh.compile(CitDoorGeometry.leaf(2,3,true),glass);
                byte[] before=vertices(format,mesh,pose,glass,false),after=vertices(format,mesh,pose,glass,true);
                // El byte de relleno no forma parte de los atributos del vértice.
                for(int i=0;i<before.length;i++)if(i%format.getVertexSize()!=format.getVertexSize()-1&&before[i]!=after[i])
                    throw new IllegalStateException("Vertex path differs: case="+cases+" byte="+i);
                cases++;
            }
        org.apache.logging.log4j.LogManager.getLogger().info("CIT render checks passed: {} vertex comparisons (BLOCK/NEW_ENTITY, transforms, normals, UV, color, light)",cases);
    }
    // Comprueba los modelos instalados en el cliente, sin cambiar ningún bloque del mundo.
    private static void checkTerrain(){
        var mc=net.minecraft.client.Minecraft.getInstance();var pos=new net.minecraft.core.BlockPos(-17,79,15);
        int cases=0;
        for(var block:new net.minecraft.world.level.block.Block[]{PostedSignage.CIT_GLASS.get(),PostedSignage.CIT_SHORT_DOOR.get(),
                PostedSignage.CIT_WIDE_DOOR.get(),PostedSignage.CLEAR_SHORT_DOOR.get(),PostedSignage.CLEAR_WIDE_DOOR.get()}){
            for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL){
                var state=block.defaultBlockState();
                state=block instanceof CitGlassBlock?state.setValue(CitGlassBlock.AXIS,facing.getAxis()):state.setValue(CitDoorBlock.FACING,facing);
                var model=mc.getBlockRenderer().getBlockModel(state);
                if(!(model instanceof CitFrameModel))throw new IllegalStateException("Missing terrain frame model: "+state);
                var data=model.getModelData(mc.level,pos,state,net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
                var quads=model.getQuads(state,null,net.minecraft.util.RandomSource.create(42),data,net.minecraft.client.renderer.RenderType.solid());
                float[] source=CitFrameGeometry.mesh(state,mc.level,pos);
                if(quads.size()!=source.length/32)throw new IllegalStateException("Terrain frame has unexpected geometry");
                for(var quad:quads){
                    if(quad.isShade()||quad.hasAmbientOcclusion())throw new IllegalStateException("Frame shading changed");
                    for(int i=3;i<32;i+=8)if(quad.getVertices()[i]!=0xFF2C2723)throw new IllegalStateException("Frame tint changed");
                }
                // El modelo de muestra conserva las superficies completas fuera del terreno.
                var original=model.getQuads(state,null,net.minecraft.util.RandomSource.create(42));
                if(original.isEmpty())throw new IllegalStateException("Inventory/sample fallback was lost");
                cases++;
            }
        }
        org.apache.logging.log4j.LogManager.getLogger().info("CIT terrain model checks passed: {} model/geometry/tint/shading/fallback cases",cases);
    }
    private static byte[] vertices(VertexFormat format,float[] mesh,PoseStack pose,boolean glass,boolean fast){
        try(var storage=new ByteBufferBuilder(8192)){
            var builder=new BufferBuilder(storage,VertexFormat.Mode.QUADS,format);
            int light=0x00D00090;
            if(fast)CitGeometryRenderer.emit(mesh,pose,builder,light,glass);
            else for(int i=0;i<mesh.length;i+=8)
                builder.addVertex(pose.last(),mesh[i],mesh[i+1],mesh[i+2])
                        .setColor(glass?199:35,glass?225:39,glass?233:44,glass?38:255)
                        .setUv(mesh[i+3],mesh[i+4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose.last(),mesh[i+5],mesh[i+6],mesh[i+7]);
            try(var result=builder.buildOrThrow()){
                var buffer=result.vertexBuffer();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;
            }
        }
    }
}
