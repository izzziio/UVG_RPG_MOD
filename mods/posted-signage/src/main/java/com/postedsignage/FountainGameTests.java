package com.postedsignage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Development-only integration checks executed in isolated Minecraft arenas; excluded from release JARs. */
@GameTestHolder(PostedSignage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FountainGameTests {
    /** Creative removal must clear both cells without spawning a Survival loot drop. */
    @GameTest(template="empty") public static void creativeUpperBreakNoDrop(GameTestHelper helper) {
        place(helper,Direction.NORTH);
        BlockPos upper=helper.absolutePos(BASE.above());
        block().playerWillDestroy(helper.getLevel(),upper,helper.getLevel().getBlockState(upper),helper.makeMockPlayer(GameType.CREATIVE));
        helper.getLevel().destroyBlock(upper,false);
        helper.assertBlockPresent(Blocks.AIR,BASE);
        helper.assertBlockPresent(Blocks.AIR,BASE.above());
        helper.assertItemEntityNotPresent(PostedSignage.DRINKING_FOUNTAIN.get());
        helper.succeed();
    }
    /** Exercise separate player state/cooldowns and the hydration packet's encode/decode contract. */
    @GameTest(template="empty") public static void independentPlayersAndPacket(GameTestHelper helper) {
        Player first=helper.makeMockPlayer(GameType.SURVIVAL), second=helper.makeMockPlayer(GameType.SURVIVAL);
        Hydration.setLevel(first,2); Hydration.setLevel(second,7);
        helper.assertTrue(Hydration.drink(first)&&Hydration.drink(second),"Player cooldowns must be independent");
        helper.assertTrue(Hydration.level(first)==8 && Hydration.level(second)==13,"Players retain independent thirst");
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            HydrationPayload.CODEC.encode(buf,new HydrationPayload(13));
            helper.assertTrue(HydrationPayload.CODEC.decode(buf).value()==13,"Hydration packet round trip");
        } finally { buf.release(); }
        helper.succeed();
    }
    private static final BlockPos BASE = new BlockPos(3,1,3);
    // Obtiene el bloque de la prueba desde el registro.
    private static DrinkingFountainBlock block() { return PostedSignage.DRINKING_FOUNTAIN_BLOCK.get(); }
    /** Build a temporary wall and use the real placement rules before constructing the upper half. */
    private static BlockState place(GameTestHelper helper, Direction facing) {
        BlockPos pos = helper.absolutePos(BASE);
        BlockPos wall = pos.relative(facing.getOpposite());
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(PostedSignage.DRINKING_FOUNTAIN.get());
        BlockHitResult hit = new BlockHitResult(wall.getCenter().add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5)), facing, wall, false);
        BlockState state = block().getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertTrue(state != null, "Supported wall placement must succeed");
        helper.getLevel().setBlock(pos, state, Block.UPDATE_ALL);
        block().setPlacedBy(helper.getLevel(),pos,state,player,stack);
        return state;
    }
    /** Each horizontal wall face must produce a matching upper half and clean paired removal. */
    @GameTest(template="empty") public static void allOrientations(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state=place(helper,facing);
            helper.assertTrue(state.getValue(DrinkingFountainBlock.FACING)==facing,"Facing follows wall face");
            helper.assertBlockProperty(BASE.above(),DrinkingFountainBlock.HALF,DoubleBlockHalf.UPPER);
            helper.getLevel().destroyBlock(helper.absolutePos(BASE),false);
            helper.assertBlockPresent(Blocks.AIR,BASE.above());
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(BASE.relative(facing.getOpposite())),Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(BASE.relative(facing.getOpposite()).above()),Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }
    /** Reject missing upper support and occupied headroom before any structure is created. */
    @GameTest(template="empty") public static void unsupportedAndBlockedPlacement(GameTestHelper helper) {
        Player player=helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack=new ItemStack(PostedSignage.DRINKING_FOUNTAIN.get());
        BlockPos wall=helper.absolutePos(BASE.south());
        helper.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        BlockHitResult hit=new BlockHitResult(wall.getCenter(),Direction.NORTH,wall,false);
        BlockPlaceContext context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit);
        helper.assertTrue(block().getStateForPlacement(context)==null,"Missing upper wall must reject placement");
        helper.getLevel().setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(helper.absolutePos(BASE.above()),Blocks.STONE.defaultBlockState());
        helper.assertTrue(block().getStateForPlacement(context)==null,"Occupied upper cell must reject placement");
        helper.succeed();
    }
    /** Upper-half destruction must collapse the base and yield exactly one fountain item. */
    @GameTest(template="empty") public static void upperBreakSingleDrop(GameTestHelper helper) {
        place(helper,Direction.NORTH);
        helper.getLevel().destroyBlock(helper.absolutePos(BASE.above()),true);
        helper.assertBlockPresent(Blocks.AIR,BASE);
        helper.assertItemEntityCountIs(PostedSignage.DRINKING_FOUNTAIN.get(),BASE,3,1);
        helper.succeed();
    }
    /** Lower-half destruction must remove the top without duplicating its loot. */
    @GameTest(template="empty") public static void lowerBreakSingleDrop(GameTestHelper helper) {
        place(helper,Direction.NORTH);
        helper.getLevel().destroyBlock(helper.absolutePos(BASE),true);
        helper.assertBlockPresent(Blocks.AIR,BASE.above());
        helper.assertItemEntityCountIs(PostedSignage.DRINKING_FOUNTAIN.get(),BASE,3,1);
        helper.succeed();
    }
    /** Removing upper wall support must collapse the whole fountain with one item drop. */
    @GameTest(template="empty") public static void supportLossSingleDrop(GameTestHelper helper) {
        place(helper,Direction.NORTH);
        helper.getLevel().destroyBlock(helper.absolutePos(BASE.south().above()),false);
        helper.assertBlockPresent(Blocks.AIR,BASE);
        helper.assertBlockPresent(Blocks.AIR,BASE.above());
        helper.assertItemEntityCountIs(PostedSignage.DRINKING_FOUNTAIN.get(),BASE,3,1);
        helper.succeed();
    }
    /** Check miss versus hit, immediate hydration, and scheduled button release. */
    @GameTest(template="empty") public static void buttonActivationAndTimeout(GameTestHelper helper) {
        BlockState state=place(helper,Direction.NORTH);
        Player player=helper.makeMockPlayer(GameType.SURVIVAL);
        Hydration.setLevel(player,4);
        BlockPos pos=helper.absolutePos(BASE);
        BlockHitResult wrong=new BlockHitResult(Vec3.atLowerCornerOf(pos).add(0.05,0.72,0.0625),Direction.NORTH,pos,false);
        block().useWithoutItem(state,helper.getLevel(),pos,player,wrong);
        helper.assertTrue(Hydration.level(player)==4,"Click outside button must not drink");
        BlockHitResult button=new BlockHitResult(Vec3.atLowerCornerOf(pos).add(0.5,0.72,0.0625),Direction.NORTH,pos,false);
        block().useWithoutItem(state,helper.getLevel(),pos,player,button);
        helper.assertTrue(Hydration.level(player)==10,"Button restores six hydration");
        helper.assertBlockProperty(BASE,DrinkingFountainBlock.ACTIVE,true);
        helper.runAfterDelay(42,()->{helper.assertBlockProperty(BASE,DrinkingFountainBlock.ACTIVE,false);helper.succeed();});
    }
    /** The hit region must follow FACING and reject activation through the back of the block. */
    @GameTest(template="empty") public static void rotatedButtons(GameTestHelper helper) {
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            BlockState state=block().defaultBlockState().setValue(DrinkingFountainBlock.FACING,facing);
            Vec3 center=new Vec3(0.5,0.72,0.5).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.4375));
            helper.assertTrue(DrinkingFountainBlock.isButtonHit(state,BlockPos.ZERO,new BlockHitResult(center,facing,BlockPos.ZERO,false)),"Rotated button must hit");
            helper.assertFalse(DrinkingFountainBlock.isButtonHit(state,BlockPos.ZERO,new BlockHitResult(center,facing.getOpposite(),BlockPos.ZERO,false)),"Back face must not activate");
        }
        helper.succeed();
    }
    /** Cover drain timing, dehydration effects, drinking, cooldown, cloning and saved NBT. */
    @GameTest(template="empty") public static void hydrationLifecycle(GameTestHelper helper) {
        Player player=helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(Hydration.level(player)==20,"New player starts full");
        for(int i=0;i<1200;i++) Hydration.advance(player);
        helper.assertTrue(Hydration.level(player)==19,"One point drains per 1200 ticks");
        Hydration.setLevel(player,0);
        Hydration.advance(player);
        helper.assertTrue(player.hasEffect(Hydration.WEAKNESS)&&player.hasEffect(Hydration.SLOWNESS),"Zero hydration applies penalties");
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,400,1));
        player.getFoodData().setFoodLevel(10);
        player.igniteForSeconds(10);
        helper.assertTrue(Hydration.drink(player),"First drink succeeds");
        helper.assertTrue(Hydration.level(player)==6 && player.getFoodData().getFoodLevel()==12 && !player.isOnFire(),"Drink hydrates, feeds and extinguishes");
        helper.assertFalse(player.hasEffect(Hydration.WEAKNESS)||player.hasEffect(Hydration.SLOWNESS),"Drink clears dehydration");
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS),"Drink preserves unrelated potion effects");
        helper.assertFalse(Hydration.drink(player),"Immediate repeated use is blocked");
        for(int i=0;i<60;i++) Hydration.advance(player);
        helper.assertTrue(Hydration.drink(player),"Cooldown expires after 60 ticks");
        Player clone=helper.makeMockPlayer(GameType.SURVIVAL);
        Hydration.copy(player,clone);
        helper.assertTrue(Hydration.level(clone)==Hydration.level(player),"Death clone keeps thirst");
        CompoundTag saved=new CompoundTag();
        player.saveWithoutId(saved);
        Player loaded=helper.makeMockPlayer(GameType.SURVIVAL);
        loaded.load(saved);
        helper.assertTrue(Hydration.level(loaded)==Hydration.level(player),"Hydration persists through entity serialization");
        helper.succeed();
    }
    /** Creative/Spectator stay exempt and drinking never exceeds the defined maximum values. */
    @GameTest(template="empty") public static void excludedModesAndCaps(GameTestHelper helper) {
        for(GameType type:new GameType[]{GameType.CREATIVE,GameType.SPECTATOR}) {
            Player player=helper.makeMockPlayer(type);
            for(int i=0;i<1200;i++) Hydration.advance(player);
            helper.assertTrue(Hydration.level(player)==20,"Excluded modes do not drain");
            Hydration.setLevel(player,0);Hydration.advance(player);
            helper.assertFalse(player.hasEffect(Hydration.WEAKNESS)||player.hasEffect(Hydration.SLOWNESS),"Excluded modes have no penalties");
        }
        Player player=helper.makeMockPlayer(GameType.SURVIVAL);
        Hydration.drink(player);
        helper.assertTrue(Hydration.level(player)==20 && player.getFoodData().getFoodLevel()==20,"Drinking never overfills");
        helper.succeed();
    }
}
