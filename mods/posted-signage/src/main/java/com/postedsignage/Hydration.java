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

/** Server-owned hydration. Only this namespace is copied when a player is cloned. */
@EventBusSubscriber(modid = PostedSignage.MOD_ID)
public final class Hydration {
    public static final int MAX = 20, DRAIN_TICKS = 1200, DRINK_AMOUNT = 6, COOLDOWN_TICKS = 60;
    private static final String KEY = "posted_signage_hydration";
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, PostedSignage.MOD_ID);
    // Separate effect IDs let drinking remove our penalties without erasing potion effects.
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

    /** Lazily initialize namespaced persistent NBT; absent data means a new player starts fully hydrated. */
    public static CompoundTag data(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(KEY)) {
            CompoundTag initial = new CompoundTag();
            initial.putInt("level", MAX);
            persistent.put(KEY, initial);
        }
        return persistent.getCompound(KEY);
    }
    /** Clamp values loaded from saves or packets to the HUD's supported 0..20 range. */
    public static int level(Player player) { return Math.clamp(data(player).getInt("level"), 0, MAX); }
    /** Share the same range guard between server updates and client hydration snapshots. */
    public static void setLevel(Player player, int value) { data(player).putInt("level", Math.clamp(value, 0, MAX)); }
    /** Creative and Spectator are exempt from draining and dehydration penalties. */
    public static boolean eligible(Player player) { return !player.isCreative() && !player.isSpectator(); }

    /** Advance cooldown independently of thirst; only living Survival/Adventure players accumulate drain ticks. */
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

    /** Apply all benefits once per player cooldown; this method is called by server-side interaction handling. */
    public static boolean drink(Player player) {
        if (player.isSpectator() || !player.isAlive() || data(player).getInt("cooldown") > 0) return false;
        data(player).putInt("cooldown", COOLDOWN_TICKS);
        setLevel(player, level(player) + DRINK_AMOUNT);
        player.getFoodData().eat(2, 0.5F); // Two hunger points and two saturation points, capped by vanilla.
        player.clearFire();
        updateEffects(player);
        sync(player);
        return true;
    }

    /** Refresh only this mod's dehydration effects and remove them immediately when the player recovers. */
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
    /** Send the authoritative value only to its owning player; mock players without a connection need no packet. */
    public static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null)
            PacketDistributor.sendToPlayer(serverPlayer, new HydrationPayload(level(player)));
    }
    /** Copy only this mod's compound into the replacement player, preserving death/respawn hydration progress. */
    public static void copy(Player oldPlayer, Player newPlayer) {
        newPlayer.getPersistentData().put(KEY, data(oldPlayer).copy());
    }
    /** The tick event fires on both sides; only the logical server may advance persistent hydration. */
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) advance(event.getEntity());
    }
    /** Minecraft creates a new player entity on respawn, so transfer the namespaced state explicitly. */
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) { copy(event.getOriginal(), event.getEntity()); }
    // Sincroniza la hidratación al entrar al servidor.
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { sync(event.getEntity()); }
    // Sincroniza la hidratación después de reaparecer.
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { sync(event.getEntity()); }
    // Sincroniza la hidratación al cambiar de dimensión.
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event.getEntity()); }
}
