package gt.edu.uvg.universityguide.block;

import com.mojang.serialization.MapCodec;
import gt.edu.uvg.universityguide.block.entity.TourStopBlockEntity;
import gt.edu.uvg.universityguide.data.TourStopSavedData;
import gt.edu.uvg.universityguide.network.TourNetwork;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Operator-only destination marker. Its editable outline is separate from its invisible world rendering. */
public final class TourStopBlock extends BaseEntityBlock {
    public static final MapCodec<TourStopBlock> CODEC = simpleCodec(TourStopBlock::new);
    private static final VoxelShape EDITOR_SHAPE = box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

    // Configura las propiedades iniciales del bloque.
    public TourStopBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Minecraft uses this codec when reconstructing the block from registry data. */
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Only creative operators may place markers; reject locations without a sturdy floor or headroom. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.canUseGameMasterBlocks()) {
            return null;
        }
        BlockState floor = context.getLevel().getBlockState(pos.below());
        BlockState head = context.getLevel().getBlockState(pos.above());
        if (!floor.isFaceSturdy(context.getLevel(), pos.below(), Direction.UP)
                || !head.getCollisionShape(context.getLevel(), pos.above()).isEmpty()) {
            return null;
        }
        return defaultBlockState();
    }

    /** Expose a selection outline to authorized editors while ordinary visitors see no selectable shape. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && player.canUseGameMasterBlocks()) {
            return EDITOR_SHAPE;
        }
        return Shapes.empty();
    }

    /** The marker has no normal baked model; its editor-only outline is rendered separately. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Permissions are enforced before the server opens the stop editor. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.canUseGameMasterBlocks()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TourStopBlockEntity stop) {
            TourNetwork.openStopEditor(serverPlayer, stop);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Protect administrative markers from ordinary players, including attempted client-side removal. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return player.canUseGameMasterBlocks()
                && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Index the new marker immediately so destination menus can discover it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof TourStopBlockEntity stop) {
            TourStopSavedData.get(serverLevel).upsert(stop.getStopId(), pos, stop.getStopName());
        }
    }

    /** Delete the UUID entry before the block entity disappears, but not for same-block state updates. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof TourStopBlockEntity stop) {
            TourStopSavedData.get(serverLevel).remove(stop.getStopId());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Each placed marker owns independent text and a stable UUID. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TourStopBlockEntity(pos, state);
    }
}
