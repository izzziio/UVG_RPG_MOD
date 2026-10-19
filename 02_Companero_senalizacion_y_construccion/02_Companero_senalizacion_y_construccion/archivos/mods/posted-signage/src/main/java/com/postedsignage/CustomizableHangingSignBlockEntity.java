package com.postedsignage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores the independently editable text for both faces of the custom sign. */
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

    /** Limit editable text to the blank area of the CIT panel, measured in font pixels. */
    @Override
    public int getMaxTextLineWidth() {
        return 86;
    }

    /** Keep vanilla editor line spacing aligned with the custom renderer's ten-pixel layout. */
    @Override
    public int getTextLineHeight() {
        return 10;
    }
}
