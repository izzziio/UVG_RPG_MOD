package gt.edu.uvg.universityguide.network;

import gt.edu.uvg.universityguide.UniversityGuideMod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Wire-format records for tour screens and actions. Each decoder must match its writer's field order. */
public final class TourPayloads {
    // Evita crear instancias de esta clase de utilidades.
    private TourPayloads() {
    }

    // Crea un identificador de red dentro del mod.
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(UniversityGuideMod.MOD_ID, path);
    }

    // Resume una parada para mostrarla en la lista.
    public record StopSummary(UUID id, String name, BlockPos pos, int distance) {
        /** Write fields in the exact order expected by the paired decoder. */
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeUtf(name, 64);
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(distance);
        }

        /** Read fields in wire order and apply the declared string limits. */
        private static StopSummary read(RegistryFriendlyByteBuf buffer) {
            return new StopSummary(buffer.readUUID(), buffer.readUtf(64), buffer.readBlockPos(), buffer.readVarInt());
        }
    }

    // Transporta los destinos disponibles para un guía.
    public record OpenDestinations(int guideEntityId, String guideName, List<StopSummary> stops)
            implements CustomPacketPayload {
        public static final Type<OpenDestinations> TYPE = new Type<>(id("open_destinations"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDestinations> STREAM_CODEC =
                StreamCodec.ofMember(OpenDestinations::write, OpenDestinations::read);

        /** Write fields in the exact order expected by the paired decoder. */
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(guideEntityId);
            buffer.writeUtf(guideName, 64);
            buffer.writeVarInt(stops.size());
            stops.forEach(stop -> stop.write(buffer));
        }

        /** Read fields in wire order and apply the declared string limits. */
        private static OpenDestinations read(RegistryFriendlyByteBuf buffer) {
            int entityId = buffer.readVarInt();
            String name = buffer.readUtf(64);
            int size = Math.min(buffer.readVarInt(), 512);
            List<StopSummary> stops = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                stops.add(StopSummary.read(buffer));
            }
            return new OpenDestinations(entityId, name, List.copyOf(stops));
        }

        /** Return the registered packet identifier used to route this payload. */
        @Override
        public Type<OpenDestinations> type() {
            return TYPE;
        }
    }

    // Solicita iniciar el recorrido hacia una parada.
    public record SelectDestination(int guideEntityId, UUID stopId) implements CustomPacketPayload {
        public static final Type<SelectDestination> TYPE = new Type<>(id("select_destination"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectDestination> STREAM_CODEC =
                StreamCodec.ofMember(SelectDestination::write, SelectDestination::read);

        /** Write fields in the exact order expected by the paired decoder. */
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(guideEntityId);
            buffer.writeUUID(stopId);
        }

        /** Read fields in wire order and apply the declared string limits. */
        private static SelectDestination read(RegistryFriendlyByteBuf buffer) {
            return new SelectDestination(buffer.readVarInt(), buffer.readUUID());
        }

        /** Return the registered packet identifier used to route this payload. */
        @Override
        public Type<SelectDestination> type() {
            return TYPE;
        }
    }

    // Transporta el contenido inicial del editor.
    public record OpenStopEditor(BlockPos pos, String name, String description) implements CustomPacketPayload {
        public static final Type<OpenStopEditor> TYPE = new Type<>(id("open_stop_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenStopEditor> STREAM_CODEC =
                StreamCodec.ofMember(OpenStopEditor::write, OpenStopEditor::read);

        /** Write fields in the exact order expected by the paired decoder. */
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeUtf(name, 64);
            buffer.writeUtf(description, 4096);
        }

        /** Read fields in wire order and apply the declared string limits. */
        private static OpenStopEditor read(RegistryFriendlyByteBuf buffer) {
            return new OpenStopEditor(buffer.readBlockPos(), buffer.readUtf(64), buffer.readUtf(4096));
        }

        /** Return the registered packet identifier used to route this payload. */
        @Override
        public Type<OpenStopEditor> type() {
            return TYPE;
        }
    }

    // Solicita guardar el nombre y la descripción.
    public record UpdateStop(BlockPos pos, String name, String description) implements CustomPacketPayload {
        public static final Type<UpdateStop> TYPE = new Type<>(id("update_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateStop> STREAM_CODEC =
                StreamCodec.ofMember(UpdateStop::write, UpdateStop::read);

        /** Write fields in the exact order expected by the paired decoder. */
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeUtf(name, 64);
            buffer.writeUtf(description, 4096);
        }

        /** Read fields in wire order and apply the declared string limits. */
        private static UpdateStop read(RegistryFriendlyByteBuf buffer) {
            return new UpdateStop(buffer.readBlockPos(), buffer.readUtf(64), buffer.readUtf(4096));
        }

        /** Return the registered packet identifier used to route this payload. */
        @Override
        public Type<UpdateStop> type() {
            return TYPE;
        }
    }

    // Transporta el texto mostrado al terminar el recorrido.
    public record ShowArrival(String title, String description) implements CustomPacketPayload {
        public static final Type<ShowArrival> TYPE = new Type<>(id("show_arrival"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowArrival> STREAM_CODEC =
                StreamCodec.ofMember(ShowArrival::write, ShowArrival::read);

        /** Write fields in the exact order expected by the paired decoder. */
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(title, 64);
            buffer.writeUtf(description, 4096);
        }

        /** Read fields in wire order and apply the declared string limits. */
        private static ShowArrival read(RegistryFriendlyByteBuf buffer) {
            return new ShowArrival(buffer.readUtf(64), buffer.readUtf(4096));
        }

        /** Return the registered packet identifier used to route this payload. */
        @Override
        public Type<ShowArrival> type() {
            return TYPE;
        }
    }
}
