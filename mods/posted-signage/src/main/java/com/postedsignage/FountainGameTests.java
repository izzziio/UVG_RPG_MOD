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

/** Pruebas de integración aisladas; no se incluyen en los JAR de distribución. */
@GameTestHolder(PostedSignage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FountainGameTests {
    /** Retirarlo en Creativo debe vaciar ambas celdas sin soltar el objeto de Supervivencia. */
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
    /** Prueba datos separados por jugador y codificación del paquete de hidratación. */
    @GameTest(template="empty") public static void independentPlayersAndPacket(GameTestHelper helper) {
        Player first=helper.makeMockPlayer(GameType.SURVIVAL), second=helper.makeMockPlayer(GameType.SURVIVAL);
        Hydration.setLevel(first,2); Hydration.setLevel(second,7);
        helper.assertTrue(Hydration.drink(first)&&Hydration.drink(second),"Las esperas de los jugadores deben ser independientes");
        helper.assertTrue(Hydration.level(first)==8 && Hydration.level(second)==13,"Los jugadores conservan una sed independiente");
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            HydrationPayload.CODEC.encode(buf,new HydrationPayload(13));
            helper.assertTrue(HydrationPayload.CODEC.decode(buf).value()==13,"Ida y vuelta del paquete de hidratación");
        } finally { buf.release(); }
        helper.succeed();
    }
    private static final BlockPos BASE = new BlockPos(3,1,3);
    // Obtiene el bloque de la prueba desde el registro.
    private static DrinkingFountainBlock block() { return PostedSignage.DRINKING_FOUNTAIN_BLOCK.get(); }
    /** Prueba la colocación real con una pared temporal antes de crear la mitad superior. */
    private static BlockState place(GameTestHelper helper, Direction facing) {
        BlockPos pos = helper.absolutePos(BASE);
        BlockPos wall = pos.relative(facing.getOpposite());
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(PostedSignage.DRINKING_FOUNTAIN.get());
        BlockHitResult hit = new BlockHitResult(wall.getCenter().add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5)), facing, wall, false);
        BlockState state = block().getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertTrue(state != null, "La colocación en una pared con soporte debe funcionar");
        helper.getLevel().setBlock(pos, state, Block.UPDATE_ALL);
        block().setPlacedBy(helper.getLevel(),pos,state,player,stack);
        return state;
    }
    /** Prueba las mitades y su retirada conjunta en cada orientación. */
    @GameTest(template="empty") public static void allOrientations(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state=place(helper,facing);
            helper.assertTrue(state.getValue(DrinkingFountainBlock.FACING)==facing,"La orientación sigue la cara de la pared");
            helper.assertBlockProperty(BASE.above(),DrinkingFountainBlock.HALF,DoubleBlockHalf.UPPER);
            helper.getLevel().destroyBlock(helper.absolutePos(BASE),false);
            helper.assertBlockPresent(Blocks.AIR,BASE.above());
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(BASE.relative(facing.getOpposite())),Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(BASE.relative(facing.getOpposite()).above()),Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }
    /** Rechaza falta de soporte superior o espacio ocupado antes de construir. */
    @GameTest(template="empty") public static void unsupportedAndBlockedPlacement(GameTestHelper helper) {
        Player player=helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack=new ItemStack(PostedSignage.DRINKING_FOUNTAIN.get());
        BlockPos wall=helper.absolutePos(BASE.south());
        helper.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        BlockHitResult hit=new BlockHitResult(wall.getCenter(),Direction.NORTH,wall,false);
        BlockPlaceContext context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit);
        helper.assertTrue(block().getStateForPlacement(context)==null,"Sin pared superior se debe rechazar la colocación");
        helper.getLevel().setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(helper.absolutePos(BASE.above()),Blocks.STONE.defaultBlockState());
        helper.assertTrue(block().getStateForPlacement(context)==null,"La celda superior ocupada debe rechazar la colocación");
        helper.succeed();
    }
    /** Romper arriba desmonta abajo y entrega exactamente un bebedero. */
    @GameTest(template="empty") public static void upperBreakSingleDrop(GameTestHelper helper) {
        place(helper,Direction.NORTH);
        helper.getLevel().destroyBlock(helper.absolutePos(BASE.above()),true);
        helper.assertBlockPresent(Blocks.AIR,BASE);
        helper.assertItemEntityCountIs(PostedSignage.DRINKING_FOUNTAIN.get(),BASE,3,1);
        helper.succeed();
    }
    /** Romper la mitad inferior retira la superior sin duplicar objetos. */
    @GameTest(template="empty") public static void lowerBreakSingleDrop(GameTestHelper helper) {
        place(helper,Direction.NORTH);
        helper.getLevel().destroyBlock(helper.absolutePos(BASE),true);
        helper.assertBlockPresent(Blocks.AIR,BASE.above());
        helper.assertItemEntityCountIs(PostedSignage.DRINKING_FOUNTAIN.get(),BASE,3,1);
        helper.succeed();
    }
    /** Perder el soporte superior desmonta el bebedero con un solo objeto. */
    @GameTest(template="empty") public static void supportLossSingleDrop(GameTestHelper helper) {
        place(helper,Direction.NORTH);
        helper.getLevel().destroyBlock(helper.absolutePos(BASE.south().above()),false);
        helper.assertBlockPresent(Blocks.AIR,BASE);
        helper.assertBlockPresent(Blocks.AIR,BASE.above());
        helper.assertItemEntityCountIs(PostedSignage.DRINKING_FOUNTAIN.get(),BASE,3,1);
        helper.succeed();
    }
    /** Comprueba impactos, hidratación inmediata y liberación del botón. */
    @GameTest(template="empty") public static void buttonActivationAndTimeout(GameTestHelper helper) {
        BlockState state=place(helper,Direction.NORTH);
        Player player=helper.makeMockPlayer(GameType.SURVIVAL);
        Hydration.setLevel(player,4);
        BlockPos pos=helper.absolutePos(BASE);
        BlockHitResult wrong=new BlockHitResult(Vec3.atLowerCornerOf(pos).add(0.05,0.72,0.0625),Direction.NORTH,pos,false);
        block().useWithoutItem(state,helper.getLevel(),pos,player,wrong);
        helper.assertTrue(Hydration.level(player)==4,"Un clic fuera del botón no debe beber");
        BlockHitResult button=new BlockHitResult(Vec3.atLowerCornerOf(pos).add(0.5,0.72,0.0625),Direction.NORTH,pos,false);
        block().useWithoutItem(state,helper.getLevel(),pos,player,button);
        helper.assertTrue(Hydration.level(player)==10,"El botón restaura seis puntos de hidratación");
        helper.assertBlockProperty(BASE,DrinkingFountainBlock.ACTIVE,true);
        helper.runAfterDelay(42,()->{helper.assertBlockProperty(BASE,DrinkingFountainBlock.ACTIVE,false);helper.succeed();});
    }
    /** La zona de pulsación sigue FACING y no se activa por detrás. */
    @GameTest(template="empty") public static void rotatedButtons(GameTestHelper helper) {
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            BlockState state=block().defaultBlockState().setValue(DrinkingFountainBlock.FACING,facing);
            Vec3 center=new Vec3(0.5,0.72,0.5).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.4375));
            helper.assertTrue(DrinkingFountainBlock.isButtonHit(state,BlockPos.ZERO,new BlockHitResult(center,facing,BlockPos.ZERO,false)),"El botón rotado debe recibir el golpe");
            helper.assertFalse(DrinkingFountainBlock.isButtonHit(state,BlockPos.ZERO,new BlockHitResult(center,facing.getOpposite(),BlockPos.ZERO,false)),"La cara trasera no debe activarse");
        }
        helper.succeed();
    }
    /** Prueba consumo, deshidratación, bebida, espera, clonación y persistencia NBT. */
    @GameTest(template="empty") public static void hydrationLifecycle(GameTestHelper helper) {
        Player player=helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(Hydration.level(player)==20,"Un jugador nuevo empieza con hidratación completa");
        for(int i=0;i<1200;i++) Hydration.advance(player);
        helper.assertTrue(Hydration.level(player)==19,"Se consume un punto cada 1200 ticks");
        Hydration.setLevel(player,0);
        Hydration.advance(player);
        helper.assertTrue(player.hasEffect(Hydration.WEAKNESS)&&player.hasEffect(Hydration.SLOWNESS),"Con hidratación en cero se aplican penalizaciones");
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,400,1));
        player.getFoodData().setFoodLevel(10);
        player.igniteForSeconds(10);
        helper.assertTrue(Hydration.drink(player),"El primer trago funciona");
        helper.assertTrue(Hydration.level(player)==6 && player.getFoodData().getFoodLevel()==12 && !player.isOnFire(),"Beber hidrata, alimenta y apaga el fuego");
        helper.assertFalse(player.hasEffect(Hydration.WEAKNESS)||player.hasEffect(Hydration.SLOWNESS),"Beber elimina la deshidratación");
        helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS),"Beber conserva los efectos de pociones no relacionados");
        helper.assertFalse(Hydration.drink(player),"Se bloquea el uso inmediato repetido");
        for(int i=0;i<60;i++) Hydration.advance(player);
        helper.assertTrue(Hydration.drink(player),"La espera termina tras 60 ticks");
        Player clone=helper.makeMockPlayer(GameType.SURVIVAL);
        Hydration.copy(player,clone);
        helper.assertTrue(Hydration.level(clone)==Hydration.level(player),"El clon tras morir conserva la sed");
        CompoundTag saved=new CompoundTag();
        player.saveWithoutId(saved);
        Player loaded=helper.makeMockPlayer(GameType.SURVIVAL);
        loaded.load(saved);
        helper.assertTrue(Hydration.level(loaded)==Hydration.level(player),"La hidratación persiste al serializar la entidad");
        helper.succeed();
    }
    /** Comprueba las exenciones y el límite máximo al beber. */
    @GameTest(template="empty") public static void excludedModesAndCaps(GameTestHelper helper) {
        for(GameType type:new GameType[]{GameType.CREATIVE,GameType.SPECTATOR}) {
            Player player=helper.makeMockPlayer(type);
            for(int i=0;i<1200;i++) Hydration.advance(player);
            helper.assertTrue(Hydration.level(player)==20,"Los modos excluidos no consumen hidratación");
            Hydration.setLevel(player,0);Hydration.advance(player);
            helper.assertFalse(player.hasEffect(Hydration.WEAKNESS)||player.hasEffect(Hydration.SLOWNESS),"Los modos excluidos no tienen penalizaciones");
        }
        Player player=helper.makeMockPlayer(GameType.SURVIVAL);
        Hydration.drink(player);
        helper.assertTrue(Hydration.level(player)==20 && player.getFoodData().getFoodLevel()==20,"Beber nunca excede el máximo");
        helper.succeed();
    }
}
