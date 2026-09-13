package com.postedsignage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores the independently editable text for both faces of the custom sign. */
public final class CustomizableHangingSignBlockEntity extends SignBlockEntity {
    public CustomizableHangingSignBlockEntity(BlockPos pos, BlockState state) {
        super(PostedSignage.CUSTOMIZABLE_HANGING_SIGN_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public int getMaxTextLineWidth() {
        return 86;
    }

    @Override
    public int getTextLineHeight() {
        return 10;
    }
}
