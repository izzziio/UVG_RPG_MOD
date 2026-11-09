package com.postedsignage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.postedsignage.TextureSkinEntity;
import com.postedsignage.TextureSkinSurface;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/** Usa la textura de la cara muestreada, incluidas animaciones y paquetes. */
public final class TextureSkinRenderer extends EntityRenderer<TextureSkinEntity> {
    // Sesgo de profundidad para recubrimientos: evita el tramado al verlos de lejos.
    private static final RenderType PAINT=RenderType.create("posted_signage_paint",com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,1536,false,false,RenderType.CompositeState.builder()
                    .setShaderState(RenderType.RENDERTYPE_CUTOUT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,false,false))
                    .setLightmapState(RenderType.LIGHTMAP)
                    .setLayeringState(RenderType.POLYGON_OFFSET_LAYERING).createCompositeState(false));
    // Crea el renderizador del cliente.
    public TextureSkinRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(TextureSkinEntity skin) { return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS; }
    private static final java.util.Map<TextureSkinEntity,Visual> CACHE=new java.util.WeakHashMap<>();
    private static final com.postedsignage.TextureSkinFrameCache SHAPES=new com.postedsignage.TextureSkinFrameCache();
    static void beginFrame(){SHAPES.clear();}
    private record Visual(net.minecraft.world.level.block.state.BlockState sample,Direction source,Direction face,Direction half,
            int rotation,net.minecraft.core.BlockPos support,java.util.List<net.minecraft.world.phys.AABB> boxes,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,int tint,float[] vertices,net.minecraft.core.BlockPos lightPos) {}
    static void clearCache(){CACHE.clear();SHAPES.clear();}

    // La consulta al modelo y el recorte solo se repiten si cambian sus datos.
    private static Visual visual(TextureSkinEntity cover,java.util.List<net.minecraft.world.phys.AABB> boxes){
        var cached=CACHE.get(cover);var state=cover.sample();var support=cover.support();
        if(cached!=null&&cached.sample()==state&&cached.source()==cover.sampleFace()&&cached.face()==cover.face()
                &&cached.half()==cover.half()&&cached.rotation()==cover.rotation()&&(cached.boxes()==boxes||cached.boxes().equals(boxes))&&cached.support().equals(support))return cached;
        var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        var quads=model.getQuads(state,cover.sampleFace(),RandomSource.create(42),net.neoforged.neoforge.client.model.data.ModelData.EMPTY,null);
        if(quads.isEmpty())quads=model.getQuads(state,null,RandomSource.create(42),net.neoforged.neoforge.client.model.data.ModelData.EMPTY,null)
                .stream().filter(q->q.getDirection()==cover.sampleFace()).toList();
        var sprite=quads.isEmpty()?model.getParticleIcon(net.neoforged.neoforge.client.model.data.ModelData.EMPTY):quads.getFirst().getSprite();
        int tint=!quads.isEmpty()&&quads.getFirst().isTinted()?quads.getFirst().getTintIndex():-1;
        float[] vertices=new float[boxes.size()*4*5];int offset=0;float e=TextureSkinSurface.SURFACE_OFFSET;
        for(var box:boxes){
            float x0=(float)box.minX,x1=(float)box.maxX,y0=(float)box.minY,y1=(float)box.maxY,z0=(float)box.minZ,z1=(float)box.maxZ;
            float[][] points=switch(cover.face()){
                case NORTH->new float[][]{{x1,y0,z0-e},{x0,y0,z0-e},{x0,y1,z0-e},{x1,y1,z0-e}};
                case SOUTH->new float[][]{{x0,y0,z1+e},{x1,y0,z1+e},{x1,y1,z1+e},{x0,y1,z1+e}};
                case WEST->new float[][]{{x0-e,y0,z0},{x0-e,y0,z1},{x0-e,y1,z1},{x0-e,y1,z0}};
                case EAST->new float[][]{{x1+e,y0,z1},{x1+e,y0,z0},{x1+e,y1,z0},{x1+e,y1,z1}};
                case UP->new float[][]{{x0,y1+e,z1},{x1,y1+e,z1},{x1,y1+e,z0},{x0,y1+e,z0}};
                case DOWN->new float[][]{{x0,y0-e,z0},{x1,y0-e,z0},{x1,y0-e,z1},{x0,y0-e,z1}};
            };
            for(int i=0;i<4;i++){
                int rotated=(i+cover.rotation())%4;
                float u=rotated==0||rotated==3?0:1,v=rotated<2?1:0;
                if(cover.half()!=null){
                    var uv=TextureSkinSurface.uv(cover.face(),Math.max(0,Math.min(1,points[i][0])),
                            Math.max(0,Math.min(1,points[i][1])),Math.max(0,Math.min(1,points[i][2])),cover.rotation());
                    u=uv.u();v=uv.v();
                }
                vertices[offset++]=points[i][0];vertices[offset++]=points[i][1];vertices[offset++]=points[i][2];
                vertices[offset++]=sprite.getU0()+(sprite.getU1()-sprite.getU0())*u;
                vertices[offset++]=sprite.getV0()+(sprite.getV1()-sprite.getV0())*v;
            }
        }
        var value=new Visual(state,cover.sampleFace(),cover.face(),cover.half(),cover.rotation(),support,boxes,sprite,tint,vertices,support.relative(cover.face()));
        CACHE.put(cover,value);return value;
    }
    // La luz y los tintes siguen actualizándose; la malla y la textura se reutilizan.
    @Override public void render(TextureSkinEntity cover,float yaw,float partialTick,PoseStack pose,MultiBufferSource buffers,int light){
        var minecraft=Minecraft.getInstance();var boxes=SHAPES.boxes(cover);var support=cover.support();
        var camera=minecraft.gameRenderer.getMainCamera().getPosition();
        // Evita consultar modelos, luz y tintes de caras vistas por detrás.
        if(!TextureSkinSurface.facesCamera(cover.face(),boxes,camera.x-support.getX(),camera.y-support.getY(),camera.z-support.getZ()))return;
        var data=visual(cover,boxes);
        int tint=data.tint()<0?0xFFFFFF:minecraft.getBlockColors().getColor(data.sample(),cover.level(),cover.support(),data.tint());
        Direction face=data.face();light=LevelRenderer.getLightColor(cover.level(),data.lightPos());
        float shade=cover.level().getShade(face,true);
        int red=(int)(((tint>>16)&255)*shade),green=(int)(((tint>>8)&255)*shade),blue=(int)((tint&255)*shade);
        pose.pushPose();pose.translate(-.5,-.5,-.5);
        var transform=pose.last();var consumer=buffers.getBuffer(PAINT);var vertices=data.vertices();
        var point=new org.joml.Vector3f();var normal=new org.joml.Vector3f();
        transform.transformNormal(face.getStepX(),face.getStepY(),face.getStepZ(),normal);
        int color=0xFF000000|(red<<16)|(green<<8)|blue;
        // La normal es común a toda la cara; envía cada vértice en una sola operación.
        for(int i=0;i<vertices.length;i+=5){
            transform.pose().transformPosition(vertices[i],vertices[i+1],vertices[i+2],point);
            consumer.addVertex(point.x(),point.y(),point.z(),color,vertices[i+3],vertices[i+4],
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,light,normal.x(),normal.y(),normal.z());
        }
        pose.popPose();
    }
}
