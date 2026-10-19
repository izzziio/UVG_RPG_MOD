package gt.edu.uvg.universityguide;

import gt.edu.uvg.universityguide.block.TourStopBlock;
import gt.edu.uvg.universityguide.block.entity.TourStopBlockEntity;
import gt.edu.uvg.universityguide.data.TourStopSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Persistence and dimension-index coverage for tour stops. */
@GameTestHolder(UniversityGuideMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TourStopGameTests {
    private static final BlockPos CENTER = new BlockPos(3, 2, 3);

    // Evita crear instancias de esta clase de utilidades.
    private TourStopGameTests() {
    }

    // Verifica que la parada conserva sus datos al recargar.
    @GameTest(template = "empty")
    public static void blockEntityPersistenceRoundTrip(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(CENTER);
        TourStopBlockEntity stop = placeStop(helper, pos);
        stop.update("  Biblioteca Central  ", "Descripción persistente");

        var saved = stop.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = new TourStopBlockEntity(pos, stop.getBlockState());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(restored.getStopId().equals(stop.getStopId()), "Stop UUID survives serialization");
        helper.assertTrue(restored.getStopName().equals("Biblioteca Central"), "Stop name is normalized and persisted");
        helper.assertTrue(restored.getDescription().equals("Descripción persistente"), "Stop description is persisted");
        helper.succeed();
    }

    // Verifica el índice al renombrar y retirar paradas.
    @GameTest(template = "empty")
    public static void dimensionIndexTracksRenameAndRemoval(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(CENTER);
        TourStopBlockEntity stop = placeStop(helper, pos);
        var data = TourStopSavedData.get(helper.getLevel());

        helper.assertTrue(data.find(stop.getStopId()).isPresent(), "Placed stop is indexed");
        stop.update("Laboratorio", "Información");
        var renamed = data.find(stop.getStopId());
        helper.assertTrue(renamed.isPresent() && renamed.get().name().equals("Laboratorio"), "Renaming refreshes the index");
        helper.assertTrue(renamed.get().pos().equals(pos), "Index stores the world position");

        helper.getLevel().destroyBlock(pos, false);
        helper.assertTrue(data.find(stop.getStopId()).isEmpty(), "Removing the marker deletes its index entry");
        helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.AIR), "Marker block was removed");
        helper.succeed();
    }

    // Coloca una parada para las comprobaciones.
    private static TourStopBlockEntity placeStop(GameTestHelper helper, BlockPos pos) {
        BlockState state = ModContent.TOUR_STOP.get().defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos, state);
        ((TourStopBlock) ModContent.TOUR_STOP.get()).setPlacedBy(helper.getLevel(), pos, state, null, ItemStack.EMPTY);
        var blockEntity = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(blockEntity instanceof TourStopBlockEntity, "Tour stop block entity exists");
        return (TourStopBlockEntity) blockEntity;
    }
}
