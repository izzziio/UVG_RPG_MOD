package gt.edu.uvg.universityguide;

import gt.edu.uvg.universityguide.command.GuideCommands;
import gt.edu.uvg.universityguide.entity.GuideEntity;
import gt.edu.uvg.universityguide.network.TourNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/** Entrada NeoForge que enlaza contenido, red, comandos y atributos. */
@Mod(UniversityGuideMod.MOD_ID)
public final class UniversityGuideMod {
    public static final String MOD_ID = "universityguide";

    // Conecta los registros y eventos del mod.
    public UniversityGuideMod(IEventBus modBus, ModContainer container) {
        ModContent.register(modBus);
        modBus.addListener(TourNetwork::register);
        modBus.addListener(this::registerAttributes);
        NeoForge.EVENT_BUS.addListener(GuideCommands::register);
        NeoForge.EVENT_BUS.addListener(InstructionBook::onLogin);
        NeoForge.EVENT_BUS.addListener(InstructionBook::onClone);
    }

    // Registra los atributos básicos del guía.
    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModContent.GUIDE.get(), GuideEntity.createAttributes().build());
    }
}
