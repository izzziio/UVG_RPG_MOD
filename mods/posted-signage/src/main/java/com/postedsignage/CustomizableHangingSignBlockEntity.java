package com.postedsignage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Guarda texto independiente para ambas caras del letrero. */
public final class CustomizableHangingSignBlockEntity extends SignBlockEntity {
    // Rechaza cambios de texto sin autorización del servidor.
    @Override public void updateSignText(net.minecraft.world.entity.player.Player player, boolean front,
            java.util.List<net.minecraft.server.network.FilteredText> text) {
        if (CitAccess.canEditAt(player, worldPosition)) super.updateSignText(player, front, text);
    }
    // Inicializa los datos de esta entidad.
    public CustomizableHangingSignBlockEntity(BlockPos pos, BlockState state) {
        super(PostedSignage.CUSTOMIZABLE_HANGING_SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    /** Limita el texto al área vacía del panel CIT, en píxeles. */
    @Override
    public int getMaxTextLineWidth() {
        return 86;
    }

    /** Alinea el espacio entre líneas del editor con los diez píxeles del renderizador. */
    @Override
    public int getTextLineHeight() {
        return 10;
    }
}
