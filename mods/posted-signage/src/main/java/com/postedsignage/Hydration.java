package com.postedsignage;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Hidratación administrada por el servidor y copiada solo dentro del mod. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID)
public final class Hydration {
    public static final int MAX = 20, DRAIN_TICKS = 1200, DRINK_AMOUNT = 6, COOLDOWN_TICKS = 60;
    private static final String KEY = "posted_signage_hydration";
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, PostedSignage.MOD_ID);
    // Los ID propios permiten retirar penalizaciones sin borrar efectos de pociones.
    public static final DeferredHolder<MobEffect, MobEffect> WEAKNESS = EFFECTS.register("dehydration_weakness",
            () -> new DehydrationEffect(0x9ba59a).addAttributeModifier(Attributes.ATTACK_DAMAGE,
                    ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID, "thirst_weakness"), -4,
                    AttributeModifier.Operation.ADD_VALUE));
    public static final DeferredHolder<MobEffect, MobEffect> SLOWNESS = EFFECTS.register("dehydration_slowness",
            () -> new DehydrationEffect(0x7699a6).addAttributeModifier(Attributes.MOVEMENT_SPEED,
                    ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID, "thirst_slowness"), -0.15,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private static final class DehydrationEffect extends MobEffect {
        DehydrationEffect(int color) { super(MobEffectCategory.HARMFUL, color); }
    }
    // Evita crear instancias de esta clase de utilidades.
    private Hydration() {}

    /** Inicializa bajo demanda el NBT persistente con espacio de nombres; sin datos, un jugador nuevo empieza con hidratación completa. */
    public static CompoundTag data(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(KEY)) {
            CompoundTag initial = new CompoundTag();
            initial.putInt("level", MAX);
            persistent.put(KEY, initial);
        }
        return persistent.getCompound(KEY);
    }
    /** Limita valores guardados y recibidos al intervalo 0..20. */
    public static int level(Player player) { return Math.clamp(data(player).getInt("level"), 0, MAX); }
    /** Comparte los límites de hidratación entre servidor y cliente. */
    public static void setLevel(Player player, int value) { data(player).putInt("level", Math.clamp(value, 0, MAX)); }
    /** Creativo y Espectador no pierden hidratación ni reciben penalizaciones. */
    public static boolean eligible(Player player) { return !player.isCreative() && !player.isSpectator(); }

    /** Avanza la espera sin depender de la sed; solo los jugadores vivos en Supervivencia o Aventura acumulan ticks de consumo. */
    public static void advance(Player player) {
        CompoundTag tag = data(player);
        tag.putInt("cooldown", Math.max(0, tag.getInt("cooldown") - 1));
        if (eligible(player) && player.isAlive()) {
            int elapsed = tag.getInt("elapsed") + 1;
            if (elapsed >= DRAIN_TICKS) {
                elapsed = 0;
                setLevel(player, level(player) - 1);
                sync(player);
            }
            tag.putInt("elapsed", elapsed);
        }
        updateEffects(player);
    }

    /** Aplica beneficios una vez por intervalo y jugador, desde el servidor. */
    public static boolean drink(Player player) {
        if (player.isSpectator() || !player.isAlive() || data(player).getInt("cooldown") > 0) return false;
        data(player).putInt("cooldown", COOLDOWN_TICKS);
        setLevel(player, level(player) + DRINK_AMOUNT);
        player.getFoodData().eat(2, 0.5F); // Dos puntos de comida y saturación, limitados por Minecraft.
        player.clearFire();
        updateEffects(player);
        sync(player);
        return true;
    }

    /** Actualiza solo los efectos propios y los retira al recuperarse. */
    private static void updateEffects(Player player) {
        if (eligible(player) && level(player) == 0 && player.isAlive()) {
            if (!player.hasEffect(WEAKNESS) || player.getEffect(WEAKNESS).getDuration() < 20)
                player.addEffect(new MobEffectInstance(WEAKNESS, 60, 0, true, false, true));
            if (!player.hasEffect(SLOWNESS) || player.getEffect(SLOWNESS).getDuration() < 20)
                player.addEffect(new MobEffectInstance(SLOWNESS, 60, 0, true, false, true));
        } else {
            player.removeEffect(WEAKNESS);
            player.removeEffect(SLOWNESS);
        }
    }
    /** Envía el valor oficial solo al dueño con conexión activa. */
    public static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null)
            PacketDistributor.sendToPlayer(serverPlayer, new HydrationPayload(level(player)));
    }
    /** Copia únicamente los datos del mod al jugador que reaparece. */
    public static void copy(Player oldPlayer, Player newPlayer) {
        newPlayer.getPersistentData().put(KEY, data(oldPlayer).copy());
    }
    /** Solo el servidor lógico actualiza hidratación persistente. */
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) advance(event.getEntity());
    }
    /** Transfiere los datos del mod al jugador creado tras reaparecer. */
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) { copy(event.getOriginal(), event.getEntity()); }
    // Sincroniza la hidratación al entrar al servidor.
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { sync(event.getEntity()); }
    // Sincroniza la hidratación después de reaparecer.
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { sync(event.getEntity()); }
    // Sincroniza la hidratación al cambiar de dimensión.
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event.getEntity()); }
}
