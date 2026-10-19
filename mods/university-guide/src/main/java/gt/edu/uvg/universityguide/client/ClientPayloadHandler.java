package gt.edu.uvg.universityguide.client;

import gt.edu.uvg.universityguide.client.screen.ArrivalScreen;
import gt.edu.uvg.universityguide.client.screen.DestinationScreen;
import gt.edu.uvg.universityguide.client.screen.StopEditorScreen;
import gt.edu.uvg.universityguide.network.TourPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opens server-requested screens on the client thread using immutable packet snapshots. */
public final class ClientPayloadHandler {
    // Evita crear instancias de esta clase de utilidades.
    private ClientPayloadHandler() {
    }

    // Abre la lista de destinos enviada por el servidor.
    public static void openDestinations(TourPayloads.OpenDestinations payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new DestinationScreen(payload));
    }

    // Abre el editor con los datos de la parada.
    public static void openStopEditor(TourPayloads.OpenStopEditor payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new StopEditorScreen(payload));
    }

    // Muestra la explicación al llegar al destino.
    public static void showArrival(TourPayloads.ShowArrival payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new ArrivalScreen(payload.title(), payload.description()));
    }
}
