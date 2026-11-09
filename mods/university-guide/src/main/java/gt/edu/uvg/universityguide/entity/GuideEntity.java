package gt.edu.uvg.universityguide.entity;

import com.mojang.authlib.GameProfile;
import gt.edu.uvg.universityguide.entity.navigation.*;
import com.mojang.serialization.DataResult;
import gt.edu.uvg.universityguide.block.entity.TourStopBlockEntity;
import gt.edu.uvg.universityguide.data.TourStopSavedData;
import gt.edu.uvg.universityguide.network.TourNetwork;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Controla el recorrido desde el servidor: reúne, navega, espera y anuncia llegada. */
public final class GuideEntity extends PathfinderMob {
    public static final double MAX_DESTINATION_DISTANCE = 250.0;
    public static final double GROUP_JOIN_DISTANCE = 16.0;
    public static final double GROUP_FOLLOW_DISTANCE = 12.0;
    public static final double ARRIVAL_DISTANCE = 1.5;

    private static final int PATH_RETRY_INTERVAL_TICKS = 50;
    private static final int PATH_STALL_TIMEOUT_TICKS = 20 * 10;
    private static final int MAX_PATH_RETRIES = 3;

    private static final EntityDataAccessor<Integer> TOUR_STATE =
            SynchedEntityData.defineId(GuideEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SKIN_ACCOUNT =
            SynchedEntityData.defineId(GuideEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> SKIN_PROFILE =
            SynchedEntityData.defineId(GuideEntity.class, EntityDataSerializers.COMPOUND_TAG);

    @Nullable
    private UUID targetStopId;
    @Nullable
    private BlockPos targetPos;
    // Identificadores de los jugadores que siguen este recorrido.
    private final Set<UUID> participants = new HashSet<>();
    private int noParticipantTicks;
    private int stalledPathTicks;
    private int pathRetries;
    private Vec3 lastProgressPosition = Vec3.ZERO;

    // Inicializa los datos de esta entidad.
    public GuideEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        moveControl = new GuideMoveControl(this);
        setPersistenceRequired();
        setInvulnerable(true);
        if (getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanPassDoors(true);
            groundNavigation.setCanFloat(false);
        }
    }

    /** Agrupa velocidad y alcance para ajustar la navegación coherentemente. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.STEP_HEIGHT, 0.6)
                .add(Attributes.FOLLOW_RANGE, 256.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Navegación terrestre compatible con puertas de madera. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GuideNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(false);
        return navigation;
    }

    /** Las metas controlan mirada y puertas; el recorrido decide el destino. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new OpenDoorGoal(this, true));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** Sincroniza estado y apariencia; destinos y participantes permanecen en el servidor. */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TOUR_STATE, TourState.IDLE.ordinal());
        builder.define(SKIN_ACCOUNT, "Steve");
        builder.define(SKIN_PROFILE, new CompoundTag());
    }

    /** Ignora la mano secundaria y rechaza recorridos si el guía está ocupado. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (getTourState() == TourState.IDLE) {
                TourNetwork.openDestinations(serverPlayer, this);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.universityguide.busy"), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Usa IDLE si el estado sincronizado contiene un ordinal desconocido. */
    public TourState getTourState() {
        int raw = entityData.get(TOUR_STATE);
        return raw >= 0 && raw < TourState.values().length ? TourState.values()[raw] : TourState.IDLE;
    }

    // Sincroniza el estado actual del recorrido.
    private void setTourState(TourState state) {
        entityData.set(TOUR_STATE, state.ordinal());
    }

    // Devuelve la cuenta elegida para la apariencia.
    public String getSkinAccount() {
        return entityData.get(SKIN_ACCOUNT);
    }

    /** Lee el perfil sincronizado; usa un UUID determinista si falta la apariencia. */
    public GameProfile getSkinProfile() {
        CompoundTag encoded = entityData.get(SKIN_PROFILE);
        Optional<ResolvableProfile> decoded = ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, encoded).result();
        if (decoded.isPresent()) {
            return decoded.get().gameProfile();
        }
        String account = getSkinAccount();
        UUID fallbackId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + account).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(fallbackId, account);
    }

    /** Publica una apariencia provisional y resuelve la cuenta de forma asíncrona. */
    public void setSkinAccount(String requestedAccount) {
        String account = requestedAccount == null || requestedAccount.isBlank() ? "Steve" : requestedAccount.trim();
        if (account.length() > 16) {
            account = account.substring(0, 16);
        }
        entityData.set(SKIN_ACCOUNT, account);

        UUID fallbackId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + account).getBytes(StandardCharsets.UTF_8));
        setResolvedSkin(new GameProfile(fallbackId, account));

        if (!level().isClientSide && level().getServer() != null) {
            SkullBlockEntity.fetchGameProfile(account).thenAcceptAsync(profile -> {
                if (!isRemoved()) {
                    profile.ifPresent(this::setResolvedSkin);
                }
            }, level().getServer());
        }
    }

    /** Sincroniza propiedades del perfil para obtener la apariencia correcta. */
    private void setResolvedSkin(GameProfile profile) {
        DataResult<Tag> result = ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, new ResolvableProfile(profile));
        result.result().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast)
                .ifPresent(tag -> entityData.set(SKIN_PROFILE, tag));
    }

    /** Revalida el bloque de destino antes de reunir participantes cercanos. */
    public boolean startTour(ServerPlayer owner, UUID stopId) {
        if (getTourState() != TourState.IDLE || !(level() instanceof ServerLevel serverLevel)) {
            owner.displayClientMessage(Component.translatable("message.universityguide.busy"), true);
            return false;
        }

        Optional<TourStopSavedData.Entry> indexed = TourStopSavedData.get(serverLevel).find(stopId);
        if (indexed.isEmpty()) {
            owner.displayClientMessage(Component.translatable("message.universityguide.stop_missing"), false);
            return false;
        }

        BlockPos destination = indexed.get().pos();
        if (distanceToSqr(Vec3.atCenterOf(destination)) > MAX_DESTINATION_DISTANCE * MAX_DESTINATION_DISTANCE) {
            owner.displayClientMessage(Component.translatable("message.universityguide.too_far"), false);
            return false;
        }

        serverLevel.getChunkAt(destination);
        if (!(serverLevel.getBlockEntity(destination) instanceof TourStopBlockEntity stop)
                || !stop.getStopId().equals(stopId)) {
            TourStopSavedData.get(serverLevel).remove(stopId);
            owner.displayClientMessage(Component.translatable("message.universityguide.stop_missing"), false);
            return false;
        }
        if (!isDestinationWalkable(serverLevel, destination)) {
            owner.displayClientMessage(Component.translatable("message.universityguide.stop_invalid"), false);
            return false;
        }

        participants.clear();
        double joinDistanceSquared = GROUP_JOIN_DISTANCE * GROUP_JOIN_DISTANCE;
        for (ServerPlayer player : serverLevel.players()) {
            if (!player.isSpectator() && player.distanceToSqr(this) <= joinDistanceSquared) {
                participants.add(player.getUUID());
            }
        }
        participants.add(owner.getUUID());

        targetStopId = stopId;
        targetPos = destination.immutable();
        noParticipantTicks = 0;
        stalledPathTicks = 0;
        pathRetries = 0;
        lastProgressPosition = position();
        setTourState(TourState.WALKING);
        requestPath();
        owner.displayClientMessage(Component.translatable("message.universityguide.started", stop.getStopName()), true);
        return true;
    }

    /** Exige espacio para pies y cabeza y suelo firme antes de buscar ruta. */
    private static boolean isDestinationWalkable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    /** Valida destino, espera participantes, detecta llegada y reintenta navegación. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel serverLevel) || getTourState() == TourState.IDLE) {
            return;
        }
        if (targetStopId == null || targetPos == null) {
            cancelTour(Component.translatable("message.universityguide.stop_missing"));
            return;
        }

        TourStopBlockEntity stop = getTargetStop(serverLevel);
        if (stop == null) {
            cancelTour(Component.translatable("message.universityguide.stop_invalid"));
            return;
        }

        // Una barrera sobre el guía pausa el recorrido sin agotar reintentos.
        if (GuideRouteRules.blocked(level(), getBoundingBox())) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            return;
        }

        if (!hasParticipantInDimension(serverLevel)) {
            noParticipantTicks++;
            getNavigation().stop();
            setTourState(TourState.WAITING);
            if (noParticipantTicks >= 20 * 60) {
                cancelTour(Component.translatable("message.universityguide.group_left"));
            }
            return;
        }
        noParticipantTicks = 0;

        if (!hasNearbyParticipant(serverLevel)) {
            getNavigation().stop();
            setTourState(TourState.WAITING);
            return;
        }

        if (getTourState() == TourState.WAITING) {
            setTourState(TourState.WALKING);
            stalledPathTicks = 0;
            pathRetries = 0;
            requestPath();
        }

        if (distanceToSqr(Vec3.atCenterOf(targetPos)) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE
                && GuideRouteRules.canTraverse(level(), position(), Vec3.atBottomCenterOf(targetPos), getBbWidth(), getBbHeight())) {
            arrive(stop);
            return;
        }

        stalledPathTicks++;
        // Rebotar verticalmente en un escalón no reinicia el detector de atasco.
        if (position().subtract(lastProgressPosition).horizontalDistanceSqr() >= 0.25) {
            lastProgressPosition = position();
            stalledPathTicks = 0;
            pathRetries = 0;
        }

        if (stalledPathTicks > 0
                && (stalledPathTicks % PATH_RETRY_INTERVAL_TICKS == 0
                    || (getNavigation().isDone() && stalledPathTicks==10))
                && pathRetries < MAX_PATH_RETRIES) {
            pathRetries++;
            requestPath();
        }

        if (stalledPathTicks >= PATH_STALL_TIMEOUT_TICKS) {
            cancelTour(Component.translatable("message.universityguide.no_path"));
        }
    }

    /** El UUID impide dirigir un recorrido antiguo a un marcador reemplazado. */
    @Nullable
    private TourStopBlockEntity getTargetStop(ServerLevel level) {
        if (targetPos == null || targetStopId == null
                || !level.hasChunk(SectionPos.blockToSectionCoord(targetPos.getX()),
                        SectionPos.blockToSectionCoord(targetPos.getZ()))) {
            return null;
        }
        return level.getBlockEntity(targetPos) instanceof TourStopBlockEntity stop
                && stop.getStopId().equals(targetStopId) ? stop : null;
    }

    // Recalcula al editar barreras, conservando destino y participantes.
    public void routeChanged(BlockPos changed) {
        if (getTourState() == TourState.IDLE) return;
        if (!getNavigation().isDone() && !getNavigation().shouldRecomputePath(changed)
                && !getBoundingBox().intersects(new net.minecraft.world.phys.AABB(changed))) return;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        stalledPathTicks = 0;
        pathRetries = 0;
        lastProgressPosition = position();
        if (getTourState() == TourState.WALKING && !GuideRouteRules.blocked(level(), getBoundingBox())) requestPath();
    }

    /** Apunta al centro de destino con los pies en la base. */
    private boolean requestPath() {
        if (targetPos == null) return false;
        var path = getNavigation().createPath(targetPos, 0);
        // No avanza por rutas parciales que terminan ante una barrera.
        if (path == null || !path.canReach()) { getNavigation().stop(); return false; }
        // El multiplicador también se aplica a guías existentes con atributos guardados.
        return getNavigation().moveTo(path, 1.10);
    }

    /** La dimensión controla el abandono, independientemente de la distancia de seguimiento. */
    private boolean hasParticipantInDimension(ServerLevel level) {
        for (UUID id : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.serverLevel() == level) {
                return true;
            }
        }
        return false;
    }

    /** Un participante cercano basta para reanudar el movimiento. */
    private boolean hasNearbyParticipant(ServerLevel level) {
        double maximum = GROUP_FOLLOW_DISTANCE * GROUP_FOLLOW_DISTANCE;
        for (UUID id : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.serverLevel() == level && player.distanceToSqr(this) <= maximum) {
                return true;
            }
        }
        return false;
    }

    /** Detiene el movimiento antes de explicar y libera el guía al terminar. */
    private void arrive(TourStopBlockEntity stop) {
        getNavigation().stop();
        forEachParticipant(player -> TourNetwork.showArrival(player, stop.getStopName(), stop.getDescription()));
        clearTour();
    }

    /** Avisa al grupo antes de borrar participantes y destino. */
    public void cancelTour(Component reason) {
        if (getTourState() != TourState.IDLE) {
            forEachParticipant(player -> player.displayClientMessage(reason, false));
        }
        clearTour();
    }

    /** Reinicia navegación y reintentos para comenzar en IDLE. */
    private void clearTour() {
        getNavigation().stop();
        targetStopId = null;
        targetPos = null;
        participants.clear();
        noParticipantTicks = 0;
        stalledPathTicks = 0;
        pathRetries = 0;
        setTourState(TourState.IDLE);
    }

    /** Resolve UUIDs at delivery time so disconnected players are skipped safely. */
    private void forEachParticipant(java.util.function.Consumer<ServerPlayer> action) {
        if (level().getServer() == null) {
            return;
        }
        for (UUID id : Set.copyOf(participants)) {
            ServerPlayer player = level().getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    /** El guía pertenece al mapa y no desaparece cuando se alejan visitantes. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Conserva el guía al descargar chunks aunque no esté en recorrido. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** Guarda apariencia y recorrido; reconstruye la ruta al cargar. */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinAccount", getSkinAccount());
        tag.put("SkinProfile", entityData.get(SKIN_PROFILE).copy());
        if (targetStopId != null && targetPos != null) {
            tag.putUUID("TargetStop", targetStopId);
            tag.put("TargetPos", NbtUtils.writeBlockPos(targetPos));
            ListTag participantList = new ListTag();
            for (UUID id : participants) {
                CompoundTag participantTag = new CompoundTag();
                participantTag.putUUID("Id", id);
                participantList.add(participantTag);
            }
            tag.put("Participants", participantList);
        }
    }

    /** Resume saved tours in WAITING so participant proximity is checked before movement restarts. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SKIN_ACCOUNT, tag.getString("SkinAccount").isBlank() ? "Steve" : tag.getString("SkinAccount"));
        if (tag.contains("SkinProfile", Tag.TAG_COMPOUND)) {
            entityData.set(SKIN_PROFILE, tag.getCompound("SkinProfile"));
        }

        participants.clear();
        if (tag.hasUUID("TargetStop")) {
            targetStopId = tag.getUUID("TargetStop");
            targetPos = NbtUtils.readBlockPos(tag, "TargetPos").orElse(null);
            ListTag participantList = tag.getList("Participants", Tag.TAG_COMPOUND);
            for (int index = 0; index < participantList.size(); index++) {
                CompoundTag participantTag = participantList.getCompound(index);
                if (participantTag.hasUUID("Id")) {
                    participants.add(participantTag.getUUID("Id"));
                }
            }
            setTourState(targetPos == null ? TourState.IDLE : TourState.WAITING);
        } else {
            targetStopId = null;
            targetPos = null;
            setTourState(TourState.IDLE);
        }
        lastProgressPosition = position();
    }

    public enum TourState {
        IDLE,
        WALKING,
        WAITING
    }
}
