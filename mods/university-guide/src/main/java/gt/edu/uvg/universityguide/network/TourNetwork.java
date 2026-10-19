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

/** Registers packet directions and validates client requests before changing stops or starting tours. */
public final class TourNetwork {
    // Evita crear instancias de esta clase de utilidades.
    private TourNetwork() {
    }

    /** Register both packet direction and protocol version before a connection can exchange tour data. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
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

    /** Defer client-only screen linkage until this handler actually executes on a client. */
    private static void handleOpenDestinationsClient(TourPayloads.OpenDestinations payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandler.openDestinations(payload, context);
        }
    }

    /** Keep client screen construction out of dedicated-server initialization. */
    private static void handleOpenStopEditorClient(TourPayloads.OpenStopEditor payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandler.openStopEditor(payload, context);
        }
    }

    /** Dispatch the explanation screen only in the physical client environment. */
    private static void handleShowArrivalClient(TourPayloads.ShowArrival payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientPayloadHandler.showArrival(payload, context);
        }
    }

    /** Build a bounded, alphabetically sorted snapshot; distance filtering uses the guide's current position. */
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
                        entry.id(), entry.name(), entry.pos(), (int)Math.round(Math.sqrt(distanceSquared))));
            }
        }
        String guideName = guide.getCustomName() == null
                ? Component.translatable("entity.universityguide.guide").getString()
                : guide.getCustomName().getString();
        PacketDistributor.sendToPlayer(player,
                new TourPayloads.OpenDestinations(guide.getId(), guideName, List.copyOf(summaries)));
    }

    /** Send the stored text to the requesting player instead of exposing a mutable block entity. */
    public static void openStopEditor(ServerPlayer player, TourStopBlockEntity stop) {
        PacketDistributor.sendToPlayer(player,
                new TourPayloads.OpenStopEditor(stop.getBlockPos(), stop.getStopName(), stop.getDescription()));
    }

    /** Send the title and explanation as a dedicated client screen payload. */
    public static void showArrival(ServerPlayer player, String title, String description) {
        PacketDistributor.sendToPlayer(player, new TourPayloads.ShowArrival(title, description));
    }

    /** Treat entity IDs and stop UUIDs as untrusted input; check the guide and interaction distance again. */
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

    /** Check operator privileges, proximity and text limits on the server, even when the UI already validates them. */
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
