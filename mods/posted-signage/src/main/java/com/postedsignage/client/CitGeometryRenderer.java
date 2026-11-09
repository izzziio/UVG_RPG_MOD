package com.postedsignage.client;

import com.mojang.blaze3d.vertex.*;
import com.postedsignage.CitDoorGeometry;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Dibujo compartido de marco opaco y vidrio sin bordes internos. */
final class CitGeometryRenderer {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("posted_signage","block/cit_glass_panel_white");
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite whiteSprite;
    private static int generation;
    private static boolean uniform;
    static void clearCache(){whiteSprite=null;generation++;}

    // Solo se fusiona cuando todos los píxeles y frames tienen el mismo color.
    private static void sprite(){
        if(whiteSprite!=null)return;
        whiteSprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WHITE);
        var image=whiteSprite.contents().getOriginalImage();
        int pixel=image.getPixelRGBA(0,0);uniform=true;
        for(int y=0;y<image.getHeight()&&uniform;y++)for(int x=0;x<image.getWidth();x++)
            if(image.getPixelRGBA(x,y)!=pixel){uniform=false;break;}
    }
    // Una búsqueda de material y buffer por grupo, sin repetirlas por pieza.
    static final class Batch {
        private final java.util.List<CitDoorGeometry.Piece> pieces;
        private final boolean merge;
        private int builtGeneration=-1;
        private float[] opaque,glass;
        Batch(java.util.List<CitDoorGeometry.Piece> pieces,boolean merge){this.pieces=pieces;this.merge=merge;}
        private void prepare(){
            sprite();if(builtGeneration==generation)return;
            var source=merge&&uniform?CitDoorGeometry.mergeStraightGlass(pieces):pieces;
            opaque=com.postedsignage.CitGeometryMesh.compile(source,false);
            glass=com.postedsignage.CitGeometryMesh.compile(source,true);
            atlasUv(opaque);atlasUv(glass);builtGeneration=generation;
        }
        private void atlasUv(float[] mesh){
            float u0=whiteSprite.getU0(),v0=whiteSprite.getV0(),du=whiteSprite.getU1()-u0,dv=whiteSprite.getV1()-v0;
            for(int i=0;i<mesh.length;i+=8){mesh[i+3]=u0+du*mesh[i+3];mesh[i+4]=v0+dv*mesh[i+4];}
        }
        void draw(PoseStack pose,MultiBufferSource buffers,int light){
            prepare();
            if(opaque.length>0)emit(opaque,pose,buffers.getBuffer(Sheets.solidBlockSheet()),light,false);
            if(glass.length>0)emit(glass,pose,CitGlassRenderPass.vertices(),light,true);
        }
        // Los marcos inmóviles ya se encuentran en el terreno horneado.
        void drawGlass(PoseStack pose,int light){
            prepare();if(glass.length>0)emit(glass,pose,CitGlassRenderPass.vertices(),light,true);
        }
    }
    // La luz sigue siendo actual; las UV ya están convertidas al atlas vigente.
    static void emit(float[] mesh,PoseStack pose,VertexConsumer consumer,int light,boolean glass){
        int color=glass?0x26C7E1E9:0xFF23272C;
        var transform=pose.last();
        var point=new org.joml.Vector3f();var normal=new org.joml.Vector3f();
        for(int i=0;i<mesh.length;i+=8){
            transform.pose().transformPosition(mesh[i],mesh[i+1],mesh[i+2],point);
            transform.transformNormal(mesh[i+5],mesh[i+6],mesh[i+7],normal);
            // La API completa permite el envío rápido de BufferBuilder sin saltarse formatos.
            consumer.addVertex(point.x(),point.y(),point.z(),color,mesh[i+3],mesh[i+4],OverlayTexture.NO_OVERLAY,light,
                    normal.x(),normal.y(),normal.z());
        }
    }
}
