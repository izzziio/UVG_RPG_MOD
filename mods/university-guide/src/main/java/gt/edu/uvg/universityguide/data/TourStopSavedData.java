package gt.edu.uvg.universityguide.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Índice persistente de UUID por dimensión; los menús pueden listar paradas sin cargar cada chunk de destino. */
public final class TourStopSavedData extends SavedData {
    public static final int SUMMARY_LENGTH=160;
    private static final String FILE_NAME = "universityguide_stops";
    private static final Factory<TourStopSavedData> FACTORY =
            new Factory<>(TourStopSavedData::new, TourStopSavedData::load);

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    /** Obtiene el índice propio de la dimensión, sin compartir paradas entre dimensiones. */
    public static TourStopSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    /** Omite los registros sin UUID o posición válida en lugar de construir destinos inutilizables. */
    private static TourStopSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TourStopSavedData data = new TourStopSavedData();
        ListTag list = tag.getList("Stops", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag stopTag = list.getCompound(index);
            if (!stopTag.hasUUID("Id")) {
                continue;
            }
            Optional<BlockPos> pos = NbtUtils.readBlockPos(stopTag, "Pos");
            pos.ifPresent(blockPos -> {
                UUID id = stopTag.getUUID("Id");
                data.entries.put(id, new Entry(id, blockPos, stopTag.getString("Name"),brief(stopTag.getString("Summary"))));
            });
        }
        return data;
    }

    /** Copia posiciones mutables y marca cambios solo si varía el registro. */
    public void upsert(UUID id, BlockPos pos, String name) {
        upsert(id,pos,name,entries.containsKey(id)?entries.get(id).summary():"");
    }

    // Guarda un resumen sin cargar cada chunk al abrir el menú.
    public void upsert(UUID id,BlockPos pos,String name,String description){
        Entry replacement = new Entry(id, pos.immutable(), name,brief(description));
        if (!replacement.equals(entries.put(id, replacement))) {
            setDirty();
        }
    }
    private static String brief(String text){
        String clean=text==null?"":text.replace('\u0000',' ').replaceAll("\\s+"," ").trim();
        if(clean.length()<=SUMMARY_LENGTH)return clean;
        int end=Character.isHighSurrogate(clean.charAt(SUMMARY_LENGTH-1))?SUMMARY_LENGTH-1:SUMMARY_LENGTH;
        return clean.substring(0,end);
    }

    /** Solicita guardar solo cuando se elimina un registro existente. */
    public void remove(UUID id) {
        if (entries.remove(id) != null) {
            setDirty();
        }
    }

    /** Buscar por UUID evita confundir paradas con igual nombre. */
    public Optional<Entry> find(UUID id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** Ordena los nombres sin distinguir mayúsculas para que la paginación de destinos sea predecible. */
    public List<Entry> sortedEntries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Devuelve una copia para impedir modificar el índice original. */
    public Collection<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /** Guarda el índice compacto; las descripciones permanecen en cada entidad. */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag stopTag = new CompoundTag();
            stopTag.putUUID("Id", entry.id());
            stopTag.put("Pos", NbtUtils.writeBlockPos(entry.pos()));
            stopTag.putString("Name", entry.name());
            stopTag.putString("Summary",entry.summary());
            list.add(stopTag);
        }
        tag.put("Stops", list);
        return tag;
    }

    // Resume una parada persistente por identificador y posición.
    public record Entry(UUID id, BlockPos pos, String name,String summary) {
    }
}
