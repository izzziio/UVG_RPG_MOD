package gt.edu.uvg.universityguide.block;

import com.mojang.serialization.MapCodec;
import gt.edu.uvg.universityguide.block.entity.GuideBarrierBlockEntity;
import gt.edu.uvg.universityguide.entity.GuideEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Celda prohibida al guía; solo los editores autorizados pueden verla. */
public final class GuideBarrierBlock extends BaseEntityBlock {
    public GuideBarrierBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<GuideBarrierBlock> codec() { return simpleCodec(GuideBarrierBlock::new); }

    // La misma regla controla colocación, selección y retirada.
    public static boolean canEdit(Player player) { return player != null && player.isCreative() && player.hasPermissions(2); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return canEdit(context.getPlayer()) ? defaultBlockState() : null;
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            boolean harvest, FluidState fluid) {
        return canEdit(player) && super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context instanceof EntityCollisionContext entities && entities.getEntity() instanceof Player player && canEdit(player)
                ? Shapes.block() : Shapes.empty();
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GuideBarrierBlockEntity(pos,state); }

    // Revisa rutas cercanas sin cargar entidades de otros chunks.
    private static void refresh(Level level, BlockPos pos) {
        if (!level.isClientSide) for (GuideEntity guide : level.getEntitiesOfClass(GuideEntity.class,
                new AABB(pos).inflate(GuideEntity.MAX_DESTINATION_DISTANCE))) guide.routeChanged(pos);
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state,level,pos,previous,moving);
        if (!previous.is(this)) refresh(level,pos);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state,level,pos,next,moving);
        if (!next.is(this)) refresh(level,pos);
    }
}
