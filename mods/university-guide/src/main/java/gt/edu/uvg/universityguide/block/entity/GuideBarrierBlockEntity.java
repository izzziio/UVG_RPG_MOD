package gt.edu.uvg.universityguide.block.entity;

import gt.edu.uvg.universityguide.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Permite dibujar la textura únicamente para el editor local autorizado. */
public final class GuideBarrierBlockEntity extends BlockEntity {
    public GuideBarrierBlockEntity(BlockPos pos, BlockState state) { super(ModContent.GUIDE_BARRIER_ENTITY.get(),pos,state); }
}
