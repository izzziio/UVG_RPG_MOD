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

/** Conserva UUID y texto de la parada y sincroniza el índice de dimensión. */
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

    /** La identidad sobrevive al cambio de nombre y distingue reemplazos. */
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

    /** Normaliza texto, marca el chunk y actualiza el índice de paradas. */
    public void update(String name, String newDescription) {
        this.stopName = normalize(name, MAX_NAME_LENGTH, "Nueva parada");
        this.description = normalize(newDescription, MAX_DESCRIPTION_LENGTH, "");
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            TourStopSavedData.get(serverLevel).upsert(stopId, worldPosition, stopName,description);
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Elimina NUL y espacios, limita longitud y sustituye valores vacíos. */
    private static String normalize(String value, int maximum, String fallback) {
        String cleaned = value == null ? "" : value.replace('\u0000', ' ').trim();
        if (cleaned.length() > maximum) {
            cleaned = cleaned.substring(0, maximum);
        }
        return cleaned.isBlank() ? fallback : cleaned;
    }

    /** Reindexa marcadores cargados con la identidad guardada. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            TourStopSavedData.get(serverLevel).upsert(stopId, worldPosition, stopName,description);
        }
    }

    /** Guarda UUID y texto en el NBT junto a campos de Minecraft. */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("StopId", stopId);
        tag.putString("StopName", stopName);
        tag.putString("Description", description);
    }

    /** Conserva un UUID nuevo si falta y limita el texto cargado. */
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
