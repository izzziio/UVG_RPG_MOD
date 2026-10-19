package gt.edu.uvg.universityguide.block.entity;

import gt.edu.uvg.universityguide.ModContent;
import gt.edu.uvg.universityguide.data.TourStopSavedData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Owns a stop's stable UUID and editable text; keeps the dimension-wide lookup index in sync. */
public final class TourStopBlockEntity extends BlockEntity {
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 4096;

    private UUID stopId = UUID.randomUUID();
    private String stopName = "Nueva parada";
    private String description = "Escribe aquí la explicación de esta ubicación.";

    // Inicializa los datos de esta entidad.
    public TourStopBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.TOUR_STOP_ENTITY.get(), pos, state);
    }

    /** This identity survives renaming and distinguishes a replaced marker from the original stop. */
    public UUID getStopId() {
        return stopId;
    }

    // Devuelve el nombre visible de la parada.
    public String getStopName() {
        return stopName;
    }

    // Devuelve la explicación de la parada.
    public String getDescription() {
        return description;
    }

    /** Normalize incoming text, mark the chunk dirty, and refresh the lightweight stop index. */
    public void update(String name, String newDescription) {
        this.stopName = normalize(name, MAX_NAME_LENGTH, "Nueva parada");
        this.description = normalize(newDescription, MAX_DESCRIPTION_LENGTH, "");
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            TourStopSavedData.get(serverLevel).upsert(stopId, worldPosition, stopName);
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Strip NUL characters, trim, clamp length and supply a fallback for blank values. */
    private static String normalize(String value, int maximum, String fallback) {
        String cleaned = value == null ? "" : value.replace('\u0000', ' ').trim();
        if (cleaned.length() > maximum) {
            cleaned = cleaned.substring(0, maximum);
        }
        return cleaned.isBlank() ? fallback : cleaned;
    }

    /** Re-register loaded markers so the dimension index reflects the block entity's saved identity. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            TourStopSavedData.get(serverLevel).upsert(stopId, worldPosition, stopName);
        }
    }

    /** Write UUID and text into the chunk's block-entity NBT alongside vanilla fields. */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("StopId", stopId);
        tag.putString("StopName", stopName);
        tag.putString("Description", description);
    }

    /** Retain a fresh UUID if old data lacks one and apply current text limits to loaded values. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("StopId")) {
            stopId = tag.getUUID("StopId");
        }
        stopName = normalize(tag.getString("StopName"), MAX_NAME_LENGTH, "Nueva parada");
        description = normalize(tag.getString("Description"), MAX_DESCRIPTION_LENGTH, "");
    }
}
