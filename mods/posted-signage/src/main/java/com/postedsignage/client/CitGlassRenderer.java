package com.postedsignage.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.postedsignage.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
// Dibuja el vidrio y los marcos según sus vecinos.
public final class CitGlassRenderer implements BlockEntityRenderer<CitGlassEntity>{
    private record Visual(java.util.List<CitDoorGeometry.Piece> source,CitGeometryRenderer.Batch batch){}
    private final java.util.Map<CitGlassEntity,Visual> visuals=new java.util.WeakHashMap<>();
    private final java.util.Map<java.util.List<CitDoorGeometry.Piece>,CitGeometryRenderer.Batch> shared=new java.util.HashMap<>();
    // Crea el renderizador del cliente.
    public CitGlassRenderer(BlockEntityRendererProvider.Context c){}
    // Dibuja el panel conectado y sus bordes expuestos.
    @Override public void render(CitGlassEntity e,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(e.getLevel()==null)return;
        var pieces=e.geometry();var visual=visuals.get(e);
        if(visual==null||visual.source()!=pieces){
            // Las conexiones iguales comparten malla sin retener entidades ni mundos.
            if(shared.size()>=512&&!shared.containsKey(pieces))shared.clear();
            var batch=shared.computeIfAbsent(pieces,p->new CitGeometryRenderer.Batch(p,true));
            visual=new Visual(pieces,batch);visuals.put(e,visual);
        }
        visual.batch().drawGlass(pose,light);
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(CitGlassEntity e){return e.renderBounds();}
}
