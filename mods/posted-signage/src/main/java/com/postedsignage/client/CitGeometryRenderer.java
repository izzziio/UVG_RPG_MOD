package com.postedsignage.client;

import com.mojang.blaze3d.vertex.*;
import com.postedsignage.CitDoorGeometry;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Shared opaque frame / lightly tinted glass rendering, without glass edge textures. */
final class CitGeometryRenderer {
    private static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("posted_signage","textures/block/cit_glass_panel_white.png");
    // Dibuja una pieza opaca o transparente sin costuras.
    static void piece(CitDoorGeometry.Piece piece,PoseStack pose,MultiBufferSource buffers,int light){
        var b=piece.box();
        double[][] v={{b.minX,b.minY,b.minZ},{b.maxX,b.minY,b.minZ},{b.maxX,b.maxY,b.minZ},{b.minX,b.maxY,b.minZ},
                {b.minX,b.minY,b.maxZ},{b.maxX,b.minY,b.maxZ},{b.maxX,b.maxY,b.maxZ},{b.minX,b.maxY,b.maxZ}};
        int[][] faces={{0,3,2,1},{5,6,7,4},{1,2,6,5},{4,7,3,0},{3,7,6,2},{4,0,1,5}};
        float[][] normals={{0,0,-1},{0,0,1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
        VertexConsumer consumer=buffers.getBuffer(piece.glass()?RenderType.entityTranslucent(WHITE):RenderType.entitySolid(WHITE));
        int rgb=piece.glass()?0xC7E1E9:0x23272C,alpha=piece.glass()?38:255;
        for(int f=0;f<6;f++){
            // No thin end faces on transparent panels, so neighboring pieces leave no seams.
            if(piece.glass()&&(b.getXsize()<b.getZsize()?f!=2&&f!=3:f!=0&&f!=1))continue;
            for(int i=0;i<4;i++) {var p=v[faces[f][i]];
                consumer.addVertex(pose.last(),(float)p[0],(float)p[1],(float)p[2])
                        .setColor((rgb>>16)&255,(rgb>>8)&255,rgb&255,alpha)
                        .setUv(i<2?0:1,i==0||i==3?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose.last(),normals[f][0],normals[f][1],normals[f][2]);
            }
        }
    }
}
