package gt.edu.uvg.universityguide.entity;

import com.mojang.authlib.GameProfile;
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

/** Server-driven tour state machine: gathers participants, navigates, waits for the group and reports arrival. */
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
        setPersistenceRequired();
        setInvulnerable(true);
        if (getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanPassDoors(true);
            groundNavigation.setCanFloat(false);
        }
    }

    /** Keep movement and follow-range settings together so navigation tuning stays consistent. */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 256.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Door-capable ground navigation lets the guide follow campus routes through wooden doors. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(false);
        return navigation;
    }

    /** Ambient goals control looking and doors; the tour state machine chooses movement destinations. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new OpenDoorGoal(this, true));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** Only state and skin data are synchronized here; destination and participant bookkeeping stays server-side. */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TOUR_STATE, TourState.IDLE.ordinal());
        builder.define(SKIN_ACCOUNT, "Steve");
        builder.define(SKIN_PROFILE, new CompoundTag());
    }

    /** Ignore the off hand so one click cannot open two menus; busy guides reject new tours. */
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

    /** Fall back to IDLE when synchronized data contains an unknown enum ordinal. */
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

    /** Decode the synchronized profile, using a deterministic offline UUID when no resolved skin is available. */
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

    /** Publish a fallback immediately, then resolve the account asynchronously and apply it on the server executor. */
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

    /** Encode profile properties into synchronized NBT so clients can obtain the correct skin texture. */
    private void setResolvedSkin(GameProfile profile) {
        DataResult<Tag> result = ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, new ResolvableProfile(profile));
        result.result().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast)
                .ifPresent(tag -> entityData.set(SKIN_PROFILE, tag));
    }

    /** Revalidate the indexed destination against the actual block before capturing the nearby participant group. */
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

    /** Require clear feet/head collision space and a sturdy floor before asking the pathfinder to reach a stop. */
    private static boolean isDestinationWalkable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    /** Order matters: validate the target, wait for participants, detect arrival, then retry stalled navigation. */
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

        if (distanceToSqr(Vec3.atCenterOf(targetPos)) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE) {
            arrive(stop);
            return;
        }

        stalledPathTicks++;
        if (position().distanceToSqr(lastProgressPosition) >= 0.25) {
            lastProgressPosition = position();
            stalledPathTicks = 0;
            pathRetries = 0;
        }

        if (stalledPathTicks > 0
                && stalledPathTicks % PATH_RETRY_INTERVAL_TICKS == 0
                && pathRetries < MAX_PATH_RETRIES) {
            pathRetries++;
            requestPath();
        }

        if (stalledPathTicks >= PATH_STALL_TIMEOUT_TICKS) {
            cancelTour(Component.translatable("message.universityguide.no_path"));
        }
    }

    /** A matching UUID prevents an old tour from targeting a replacement marker at the same coordinates. */
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

    /** Aim at the center of the destination cell, with feet at its base rather than its vertical center. */
    private boolean requestPath() {
        return targetPos != null && getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                1.0
        );
    }

    /** Dimension presence controls the abandonment timer; it is distinct from following distance. */
    private boolean hasParticipantInDimension(ServerLevel level) {
        for (UUID id : participants) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.serverLevel() == level) {
                return true;
            }
        }
        return false;
    }

    /** One nearby registered participant is enough to resume the guide's movement. */
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

    /** Stop movement before opening explanations, then release the guide for another tour. */
    private void arrive(TourStopBlockEntity stop) {
        getNavigation().stop();
        forEachParticipant(player -> TourNetwork.showArrival(player, stop.getStopName(), stop.getDescription()));
        clearTour();
    }

    /** Notify the captured group before clearing the participant set and destination. */
    public void cancelTour(Component reason) {
        if (getTourState() != TourState.IDLE) {
            forEachParticipant(player -> player.displayClientMessage(reason, false));
        }
        clearTour();
    }

    /** Reset navigation and retry counters together so the next tour starts from a clean IDLE state. */
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

    /** Tour guides are map content and must not despawn when visitors leave the area. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Keep the NPC across chunk unloads even when no tour is active. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** Persist skin and active-tour identity; transient pathfinder state is rebuilt after loading. */
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
