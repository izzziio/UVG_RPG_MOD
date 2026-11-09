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

/** Pruebas de persistencia e índice de paradas por dimensión. */
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

    // El menú conserva un resumen sin recargar las paradas ni perder el texto completo.
    @GameTest(template="empty") public static void indexKeepsBriefDescriptionAndRefreshesOldEntries(GameTestHelper h){
        var pos=h.absolutePos(CENTER);var stop=placeStop(h,pos);var data=TourStopSavedData.get(h.getLevel());
        String description="Información de la biblioteca.\n"+"Servicios y horarios ".repeat(30);
        stop.update("Biblioteca",description);var entry=data.find(stop.getStopId()).orElseThrow();
        h.assertTrue(entry.summary().startsWith("Información de la biblioteca. Servicios"),"Menu receives actual description");
        h.assertTrue(entry.summary().length()<=TourStopSavedData.SUMMARY_LENGTH&&!entry.summary().contains("\n"),"Summary is bounded and single-line");
        h.assertTrue(stop.getDescription().equals(description.trim()),"Arrival keeps complete description");
        var saved=data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
        var row=saved.getList("Stops",net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                .map(tag->(net.minecraft.nbt.CompoundTag)tag).filter(tag->tag.getUUID("Id").equals(stop.getStopId())).findFirst().orElseThrow();
        h.assertTrue(row.getString("Summary").equals(entry.summary()),"Summary is stored with the index");
        data.upsert(stop.getStopId(),pos,"Biblioteca antigua");
        h.assertTrue(data.find(stop.getStopId()).orElseThrow().summary().equals(entry.summary()),"Legacy name-only update preserves description");
        stop.update("Biblioteca","Texto nuevo");
        h.assertTrue(data.find(stop.getStopId()).orElseThrow().summary().equals("Texto nuevo"),"Edits refresh summary");h.succeed();
    }

    // El cliente recibe el resumen y rechaza cantidades inválidas.
    @GameTest(template="empty") public static void destinationPacketPreservesDescriptions(GameTestHelper h){
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            var stop=new gt.edu.uvg.universityguide.network.TourPayloads.StopSummary(java.util.UUID.randomUUID(),"Biblioteca",CENTER,25,"Consulta de libros y salas de estudio.");
            var payload=new gt.edu.uvg.universityguide.network.TourPayloads.OpenDestinations(7,"Guía",java.util.List.of(stop));
            gt.edu.uvg.universityguide.network.TourPayloads.OpenDestinations.STREAM_CODEC.encode(buffer,payload);
            var received=gt.edu.uvg.universityguide.network.TourPayloads.OpenDestinations.STREAM_CODEC.decode(buffer);
            h.assertTrue(received.equals(payload),"Destination and description survive network encoding");
            buffer.clear();buffer.writeVarInt(7);buffer.writeUtf("Guía",64);buffer.writeVarInt(-1);
            boolean rejected=false;
            try{gt.edu.uvg.universityguide.network.TourPayloads.OpenDestinations.STREAM_CODEC.decode(buffer);}catch(IllegalArgumentException invalid){rejected=true;}
            h.assertTrue(rejected,"Negative destination count rejected");h.succeed();
        }finally{buffer.release();}
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
