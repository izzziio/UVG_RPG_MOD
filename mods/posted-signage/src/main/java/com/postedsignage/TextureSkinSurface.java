package com.postedsignage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Selección y recorte de cada media losa, compartidos por cliente y servidor. */
public final class TextureSkinSurface {
    public static final float SURFACE_OFFSET = .00125F;
    private TextureSkinSurface() {}

    // Omite una capa solo si ninguna de sus caras apunta hacia la cámara.
    public static boolean facesCamera(Direction face, List<AABB> boxes, double x, double y, double z) {
        double camera = face.getAxis().choose(x,y,z);
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        for (AABB box : boxes) {
            float plane = (float)(positive ? box.max(face.getAxis()) : box.min(face.getAxis()));
            plane += positive ? SURFACE_OFFSET : -SURFACE_OFFSET;
            // El margen conserva caras casi coplanares por redondeo del renderizado.
            if (positive ? camera > plane-1e-6 : camera < plane+1e-6) return true;
        }
        return false;
    }

    // La posición pulsada distingue las mitades incluso en las caras superior e inferior.
    public static Direction halfAt(BlockState state, BlockPos pos, Vec3 hit) {
        if (!(state.getBlock() instanceof VerticalSlabBlock)) return null;
        Direction first = state.getValue(VerticalSlabBlock.FACING);
        if (state.getValue(VerticalSlabBlock.FILL) == VerticalSlabBlock.Fill.NONE) return first;
        double side = (hit.x-pos.getX()-.5)*first.getStepX() + (hit.z-pos.getZ()-.5)*first.getStepZ();
        return side >= 0 ? first : first.getOpposite();
    }

    // Al copiar una pareja se toma el material de la mitad seleccionada.
    public static BlockState sample(BlockState state, Direction half) {
        if (half == null || !(state.getBlock() instanceof VerticalSlabBlock)) return state;
        var fill = state.getValue(VerticalSlabBlock.FILL);
        if (fill == VerticalSlabBlock.Fill.NONE || half == state.getValue(VerticalSlabBlock.FACING))
            return state.setValue(VerticalSlabBlock.FILL, VerticalSlabBlock.Fill.NONE);
        var block = fill == VerticalSlabBlock.Fill.BRICK ? PostedSignage.BRICK_VERTICAL_SLAB_BLOCK.get()
                : PostedSignage.SMOOTH_QUARTZ_VERTICAL_SLAB_BLOCK.get();
        return block.defaultBlockState().setValue(VerticalSlabBlock.FACING, half);
    }

    // Recorta la capa visual sin modificar el bloque ni su hitbox.
    public static List<AABB> boxes(BlockState state, BlockGetter level, BlockPos pos, Direction half) {
        var boxes = state.getShape(level, pos).toAabbs();
        if (half == null) return boxes;
        AABB region = switch (half) {
            case NORTH -> new AABB(0,0,0,1,1,.5);
            case SOUTH -> new AABB(0,0,.5,1,1,1);
            case WEST -> new AABB(0,0,0,.5,1,1);
            case EAST -> new AABB(.5,0,0,1,1,1);
            default -> new AABB(0,0,0,1,1,1);
        };
        List<AABB> result = new ArrayList<>();
        for (AABB box : boxes) if (box.intersects(region)) result.add(box.intersect(region));
        return result;
    }
    public record UV(float u, float v) {}
    // Recorta la textura con la misma escala; dividir una capa antigua no la estira.
    public static UV uv(Direction face, double x, double y, double z, int rotation) {
        double u=switch(face) {case NORTH -> 1-x;case SOUTH,UP,DOWN -> x;case WEST -> z;case EAST -> 1-z;};
        double v=switch(face) {case UP -> z;case DOWN -> 1-z;default -> 1-y;};
        return switch(Math.floorMod(rotation,4)) {
            case 1 -> new UV((float)v,(float)(1-u));
            case 2 -> new UV((float)(1-u),(float)(1-v));
            case 3 -> new UV((float)(1-v),(float)u);
            default -> new UV((float)u,(float)v);
        };
    }

}
