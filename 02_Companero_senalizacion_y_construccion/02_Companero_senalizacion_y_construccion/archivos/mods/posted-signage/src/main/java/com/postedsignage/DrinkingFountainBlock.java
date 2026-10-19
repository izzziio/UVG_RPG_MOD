package com.postedsignage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two-cell wall fountain sharing one block ID. HALF selects geometry; only the lower half yields loot. */
public final class DrinkingFountainBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DrinkingFountainBlock> CODEC = simpleCodec(DrinkingFountainBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    // Configura las propiedades iniciales del bloque.
    public DrinkingFountainBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(ACTIVE, false));
    }
    /** Minecraft uses this codec when reconstructing the block from registry data. */
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    // Declara las propiedades guardadas en cada bloque.
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HALF, ACTIVE); }
    // Calcula la orientación y comprueba la colocación.
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        if (!facing.getAxis().isHorizontal() || pos.getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)
                || !context.getLevel().getFluidState(pos).isEmpty() || !context.getLevel().getFluidState(pos.above()).isEmpty()) return null;
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return supported(state, context.getLevel(), pos) && supported(state, context.getLevel(), pos.above()) ? state : null;
    }
    /** FACING points out from the wall; the support therefore lies in the opposite direction. */
    private boolean supported(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }
    /** Each half independently requires a sturdy attachment face behind its own cell. */
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return supported(state, level, pos); }
    // Completa el bebedero colocando su mitad superior.
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), UPDATE_ALL);
    }
    /** Removing support or the matching other half collapses the pair through vanilla neighbor updates. */
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (!supported(state, level, pos)) return Blocks.AIR.defaultBlockState();
        if (direction == (lower ? Direction.UP : Direction.DOWN)
                && (!neighbor.is(this) || neighbor.getValue(HALF) == state.getValue(HALF)
                || neighbor.getValue(FACING) != state.getValue(FACING))) return Blocks.AIR.defaultBlockState();
        return state;
    }
    /** Remove the lower half without loot when Creative breaks the upper half, avoiding an unintended drop. */
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            if (level.getBlockState(pos.below()).is(this)) level.destroyBlock(pos.below(), false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    /** Start from north-facing local bounds and rotate every box into the placed orientation. */
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? Block.box(1, 0, 13, 15, 12, 16)
                : Shapes.or(Block.box(2, 1, 4, 14, 10, 15), Block.box(0, 10, 1, 16, 14, 16),
                        Block.box(1, 14, 13, 15, 16, 16));
        int turns = switch (state.getValue(FACING)) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        for (int i = 0; i < turns; i++) {
            VoxelShape[] rotated = {Shapes.empty()};
            shape.forAllBoxes((x0,y0,z0,x1,y1,z1) -> rotated[0] = Shapes.or(rotated[0], Shapes.box(1-z1,y0,x0,1-z0,y1,x1)));
            shape = rotated[0];
        }
        return shape;
    }
    /** Canonical coordinates face north; the long front push bar is easy to target. */
    public static boolean isButtonHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER || hit.getDirection() != state.getValue(FACING)) return false;
        Vec3 point = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        double x = switch (state.getValue(FACING)) { case SOUTH -> 1-point.x; case EAST -> point.z; case WEST -> 1-point.z; default -> point.x; };
        return x >= 3.0/16 && x <= 13.0/16 && point.y >= 10.0/16 && point.y <= 13.0/16;
    }
    /** The client acknowledges a valid click; only the server awards water/food and changes the active state. */
    private InteractionResult activate(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!isButtonHit(state, pos, hit) || player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide() && Hydration.drink(player)) {
            level.setBlock(pos, state.setValue(ACTIVE, true), UPDATE_ALL);
            level.scheduleTick(pos, this, 40);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.5F, 1.3F);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.65F, 1F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    /** Empty-hand clicks share the same button hit test and cooldown as held-item clicks. */
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) { return activate(state, level, pos, player, hit); }
    // Activa el bebedero también con un objeto en la mano.
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return activate(state, level, pos, player, hit).consumesAction() ? ItemInteractionResult.sidedSuccess(level.isClientSide()) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    /** A scheduled tick releases the button after the two-second active interval. */
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, state.setValue(ACTIVE, false), UPDATE_ALL);
    }
    /** Client particles visualize the active state without placing fluid blocks or changing the world. */
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE) || state.getValue(HALF) != DoubleBlockHalf.LOWER) return;
        for (int i = 0; i <= 8; i++) {
            double t = i / 8.0;
            // Small arc from the right bubbler toward the drain, staying inside the basin.
            double x = 3.5/16 + t*0.33, z = 5.0/16 + t*0.12, y = 15.0/16 + 0.28*4*t*(1-t) - 0.16*t;
            Vec3 p = rotatePoint(state.getValue(FACING), x,y,z).add(Vec3.atLowerCornerOf(pos));
            level.addParticle(ParticleTypes.FALLING_WATER, p.x,p.y,p.z, 0,0,0);
        }
    }
    /** Use the same quarter-turn transform for particle origins as for collision/model orientation. */
    private static Vec3 rotatePoint(Direction facing, double x, double y, double z) {
        return switch(facing) { case EAST -> new Vec3(1-z,y,x); case SOUTH -> new Vec3(1-x,y,1-z); case WEST -> new Vec3(z,y,1-x); default -> new Vec3(x,y,z); };
    }
    /** Rotate the stored facing so structure rotations preserve the attachment orientation. */
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    // Refleja la orientación del bloque.
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
}
