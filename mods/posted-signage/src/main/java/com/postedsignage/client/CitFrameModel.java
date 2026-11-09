package com.postedsignage.client;

import com.postedsignage.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;

/** Hornea únicamente marcos; el cristal conserva su pasada transparente propia. */
final class CitFrameModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<List<BakedQuad>> FRAMES=new ModelProperty<>();
    record Key(BlockState state,BlockState above,BlockState below){}
    private final TextureAtlasSprite sprite;
    private final Map<Key,List<BakedQuad>> cache;
    CitFrameModel(BakedModel original,TextureAtlasSprite sprite,Map<Key,List<BakedQuad>> cache){super(original);this.sprite=sprite;this.cache=cache;}
    // Cada recarga crea una caché nueva, sin referencias a niveles ni entidades.
    static Map<Key,List<BakedQuad>> newCache(){return new ConcurrentHashMap<>();}
    @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData incoming){
        var key=new Key(state,state.getBlock() instanceof CitGlassBlock?level.getBlockState(pos.above()):null,
                state.getBlock() instanceof CitGlassBlock?level.getBlockState(pos.below()):null);
        if(cache.size()>2048)cache.clear();
        var quads=cache.computeIfAbsent(key,unused->bake(CitFrameGeometry.mesh(state,level,pos),sprite));
        return incoming.derive().with(FRAMES,quads).build();
    }
    // Los inventarios y las muestras fuera del terreno conservan el modelo original.
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType type){
        if(!data.has(FRAMES))return originalModel.getQuads(state,side,random,data,type);
        return side==null&&(type==null||type==RenderType.solid())?data.get(FRAMES):List.of();
    }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return ChunkRenderTypeSet.of(RenderType.solid());}
    @Override public boolean useAmbientOcclusion(){return false;}
    @Override public TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType type){return TriState.FALSE;}
    // Mantiene el color uniforme y las normales originales sin sombreado direccional.
    static List<BakedQuad> bake(float[] mesh,TextureAtlasSprite sprite){
        var result=new java.util.ArrayList<BakedQuad>();
        for(int start=0;start<mesh.length;start+=32){
            int[] packed=new int[32];
            for(int v=0;v<4;v++){
                int i=start+v*8,o=v*8;
                packed[o]=Float.floatToRawIntBits(mesh[i]);packed[o+1]=Float.floatToRawIntBits(mesh[i+1]);packed[o+2]=Float.floatToRawIntBits(mesh[i+2]);
                packed[o+3]=0xFF2C2723;
                packed[o+4]=Float.floatToRawIntBits(sprite.getU0()+(sprite.getU1()-sprite.getU0())*mesh[i+3]);
                packed[o+5]=Float.floatToRawIntBits(sprite.getV0()+(sprite.getV1()-sprite.getV0())*mesh[i+4]);
                packed[o+7]=((int)(mesh[i+5]*127)&255)|(((int)(mesh[i+6]*127)&255)<<8)|(((int)(mesh[i+7]*127)&255)<<16);
            }
            var direction=Direction.getNearest(mesh[start+5],mesh[start+6],mesh[start+7]);
            result.add(new BakedQuad(packed,-1,direction,sprite,false,false));
        }
        return List.copyOf(result);
    }
}
