package gt.edu.uvg.universityguide.network;

import gt.edu.uvg.universityguide.block.entity.TourStopBlockEntity;
import gt.edu.uvg.universityguide.client.ClientPayloadHandler;
import gt.edu.uvg.universityguide.data.TourStopSavedData;
import gt.edu.uvg.universityguide.entity.GuideEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registra paquetes y valida solicitudes antes de modificar recorridos. */
public final class TourNetwork {
    // Evita crear instancias de esta clase de utilidades.
    private TourNetwork() {
    }

    /** Registra direcciones y versión del protocolo antes de intercambiar datos. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(TourPayloads.OpenDestinations.TYPE, TourPayloads.OpenDestinations.STREAM_CODEC,
                TourNetwork::handleOpenDestinationsClient);
        registrar.playToServer(TourPayloads.SelectDestination.TYPE, TourPayloads.SelectDestination.STREAM_CODEC,
                TourNetwork::selectDestination);
        registrar.playToClient(TourPayloads.OpenStopEditor.TYPE, TourPayloads.OpenStopEditor.STREAM_CODEC,
                TourNetwork::handleOpenStopEditorClient);
        registrar.playToServer(TourPayloads.UpdateStop.TYPE, TourPayloads.UpdateStop.STREAM_CODEC,
                TourNetwork::updateStop);
        registrar.playToClient(TourPayloads.ShowArrival.TYPE, TourPayloads.ShowArrival.STREAM_CODEC,
                TourNetwork::handleShowArrivalClient);
    }

    /** Carga las pantallas únicamente cuando se ejecuta en el cliente. */
    private static void handleOpenDestinationsClient(TourPayloads.OpenDestinations payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandler.openDestinations(payload, context);
        }
    }

    /** Evita crear pantallas al iniciar un servidor dedicado. */
    private static void handleOpenStopEditorClient(TourPayloads.OpenStopEditor payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandler.openStopEditor(payload, context);
        }
    }

    /** Abre la explicación únicamente en el cliente físico. */
    private static void handleShowArrivalClient(TourPayloads.ShowArrival payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandler.showArrival(payload, context);
        }
    }

    /** Genera una lista limitada y ordenada, filtrada desde el guía. */
    public static void openDestinations(ServerPlayer player, GuideEntity guide) {
        if (!(guide.level() instanceof ServerLevel level)) {
            return;
        }
        List<TourPayloads.StopSummary> summaries = new ArrayList<>();
        double maximumSquared = GuideEntity.MAX_DESTINATION_DISTANCE * GuideEntity.MAX_DESTINATION_DISTANCE;
        for (TourStopSavedData.Entry entry : TourStopSavedData.get(level).sortedEntries()) {
            double distanceSquared = guide.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(entry.pos()));
            if (distanceSquared <= maximumSquared && summaries.size() < 512) {
                summaries.add(new TourPayloads.StopSummary(
                        entry.id(), entry.name(), entry.pos(), (int)Math.round(Math.sqrt(distanceSquared)),entry.summary()));
            }
        }
        String guideName = guide.getCustomName() == null
                ? Component.translatable("entity.universityguide.guide").getString()
                : guide.getCustomName().getString();
        PacketDistributor.sendToPlayer(player,
                new TourPayloads.OpenDestinations(guide.getId(), guideName, List.copyOf(summaries)));
    }

    /** Envía el texto guardado sin exponer una entidad mutable. */
    public static void openStopEditor(ServerPlayer player, TourStopBlockEntity stop) {
        PacketDistributor.sendToPlayer(player,
                new TourPayloads.OpenStopEditor(stop.getBlockPos(), stop.getStopName(), stop.getDescription()));
    }

    /** Envía título y explicación en un paquete de pantalla. */
    public static void showArrival(ServerPlayer player, String title, String description) {
        PacketDistributor.sendToPlayer(player, new TourPayloads.ShowArrival(title, description));
    }

    /** Trata ID y UUID recibidos como datos no fiables y revalida distancia. */
    private static void selectDestination(TourPayloads.SelectDestination payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.guideEntityId());
        if (!(entity instanceof GuideEntity guide) || player.distanceToSqr(guide) > 64.0) {
            player.displayClientMessage(Component.translatable("message.universityguide.guide_missing"), false);
            return;
        }
        guide.startTour(player, payload.stopId());
    }

    /** Valida permisos, cercanía y límites de texto en el servidor. */
    private static void updateStop(TourPayloads.UpdateStop payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !player.canUseGameMasterBlocks()
                || player.distanceToSqr(payload.pos().getCenter()) > 64.0) {
            return;
        }
        if (payload.name().length() > TourStopBlockEntity.MAX_NAME_LENGTH
                || payload.description().length() > TourStopBlockEntity.MAX_DESCRIPTION_LENGTH) {
            player.displayClientMessage(Component.translatable("message.universityguide.text_too_long"), false);
            return;
        }
        if (player.level().getBlockEntity(payload.pos()) instanceof TourStopBlockEntity stop) {
            stop.update(payload.name(), payload.description());
            player.displayClientMessage(Component.translatable("message.universityguide.stop_saved"), true);
        }
    }
}
