package com.postedsignage.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.postedsignage.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
// Dibuja el vidrio y los marcos según sus vecinos.
public final class CitGlassRenderer implements BlockEntityRenderer<CitGlassEntity>{
    // Crea el renderizador del cliente.
    public CitGlassRenderer(BlockEntityRendererProvider.Context c){}
    // Dibuja el panel conectado y sus bordes expuestos.
    @Override public void render(CitGlassEntity e,float tick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(e.getLevel()!=null)for(var piece:CitGlassBlock.pieces(e.getBlockState(),e.getLevel(),e.getBlockPos()))CitGeometryRenderer.piece(piece,pose,buffers,light);
    }
}
