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

/** Marcador administrativo invisible, con contorno de edición independiente. */
public final class TourStopBlock extends BaseEntityBlock {
    public static final MapCodec<TourStopBlock> CODEC = simpleCodec(TourStopBlock::new);
    private static final VoxelShape EDITOR_SHAPE = box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

    // Configura las propiedades iniciales del bloque.
    public TourStopBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Minecraft reconstruye el bloque mediante este códec. */
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Solo operadores creativos colocan marcadores con suelo y espacio suficiente. */
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

    /** Muestra contorno seleccionable solo a editores autorizados. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && player.canUseGameMasterBlocks()) {
            return EDITOR_SHAPE;
        }
        return Shapes.empty();
    }

    /** El marcador no tiene modelo normal; su contorno se dibuja aparte. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Comprueba permisos antes de abrir el editor de paradas. */
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

    /** Protege marcadores frente a jugadores sin permiso, también desde el cliente. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return player.canUseGameMasterBlocks()
                && super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Indexa el marcador nuevo para mostrarlo entre los destinos. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof TourStopBlockEntity stop) {
            TourStopSavedData.get(serverLevel).upsert(stop.getStopId(), pos, stop.getStopName(),stop.getDescription());
        }
    }

    /** Elimina el UUID antes de retirar la entidad, salvo cambios del mismo bloque. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof TourStopBlockEntity stop) {
            TourStopSavedData.get(serverLevel).remove(stop.getStopId());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Cada marcador conserva texto independiente y UUID estable. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TourStopBlockEntity(pos, state);
    }
}
