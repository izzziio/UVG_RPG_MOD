package com.postedsignage;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Comparte consultas dinámicas solo durante un fotograma del cliente. */
public final class TextureSkinFrameCache {
    private record Key(BlockPos pos,BlockState state,Direction half){}
    private final Map<Key,List<AABB>> shapes=new HashMap<>();
    private WeakReference<Level> world=new WeakReference<>(null);
    // Ninguna consulta sobrevive al siguiente frame o a una recarga.
    public void clear(){shapes.clear();world.clear();}
    public List<AABB> boxes(TextureSkinEntity skin){
        var level=skin.level();var state=level.getBlockState(skin.support());
        // El vidrio depende de vecinos; otras entidades pueden tener geometría propia mutable.
        if((state.hasBlockEntity()&&!(state.getBlock() instanceof CitGlassBlock))||!state.getBlock().hasDynamicShape())return skin.surfaceBoxes();
        if(world.get()!=level){shapes.clear();world=new WeakReference<>(level);}
        var key=new Key(skin.support(),state,skin.half());
        if(shapes.size()>=2048&&!shapes.containsKey(key))shapes.clear();
        return shapes.computeIfAbsent(key,unused->skin.surfaceBoxes());
    }
}
