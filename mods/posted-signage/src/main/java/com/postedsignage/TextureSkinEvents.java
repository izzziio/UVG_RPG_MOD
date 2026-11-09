package com.postedsignage;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Evita que una pintura sobreviva a romper y recolocar el mismo bloque. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID)
public final class TextureSkinEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide)
            for (var skin : level.getEntitiesOfClass(TextureSkinEntity.class, new AABB(event.getPos()).inflate(.1),
                    skin -> skin.support().equals(event.getPos()))) skin.discard();
    }
}
