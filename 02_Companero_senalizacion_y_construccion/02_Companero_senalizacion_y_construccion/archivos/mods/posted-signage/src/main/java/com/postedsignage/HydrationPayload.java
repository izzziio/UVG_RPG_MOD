package com.postedsignage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-to-client snapshot of the local player's hydration; clients do not submit hydration values. */
public record HydrationPayload(int value) implements CustomPacketPayload {
    public static final Type<HydrationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID, "hydration"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HydrationPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.value), buf -> new HydrationPayload(buf.readVarInt()));
    /** Return the registered packet identifier used to route this payload. */
    @Override public Type<HydrationPayload> type() { return TYPE; }
    // Conecta los registros y manejadores correspondientes.
    public static void register(RegisterPayloadHandlersEvent event) {
        // Uses only common Player APIs, so no client classes are linked on dedicated servers.
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) ->
                context.enqueueWork(() -> Hydration.setLevel(context.player(), payload.value)));
    }
}
