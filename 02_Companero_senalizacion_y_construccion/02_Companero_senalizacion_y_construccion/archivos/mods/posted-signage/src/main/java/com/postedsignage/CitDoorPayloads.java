package com.postedsignage;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Define los mensajes para abrir y guardar el editor CIT.
public final class CitDoorPayloads {
    // Evita crear instancias de esta clase de utilidades.
    private CitDoorPayloads(){}
    // Transporta la etiqueta y el permiso temporal de edición.
    public record Open(BlockPos pos,String room,int color,UUID token) implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID,"cit_door_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeUtf(p.room,5);b.writeVarInt(p.color);b.writeUUID(p.token);},b->new Open(b.readBlockPos(),b.readUtf(5),b.readVarInt(),b.readUUID()));
        // Identifica este mensaje de red.
        @Override public Type<Open> type(){return TYPE;}
    }
    // Transporta los cambios y el identificador de sesión.
    public record Save(BlockPos pos,String room,int color,UUID token) implements CustomPacketPayload {
        public static final Type<Save> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(PostedSignage.MOD_ID,"cit_door_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Save> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeUtf(p.room,5);b.writeVarInt(p.color);b.writeUUID(p.token);},b->new Save(b.readBlockPos(),b.readUtf(5),b.readVarInt(),b.readUUID()));
        // Identifica este mensaje de red.
        @Override public Type<Save> type(){return TYPE;}
    }
    // Conecta los registros y manejadores correspondientes.
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToClient(Open.TYPE,Open.CODEC,(p,c)->c.enqueueWork(()->com.postedsignage.client.CitDoorScreen.open(p)));
        registrar.playToServer(Save.TYPE,Save.CODEC,(p,c)->c.enqueueWork(()->{
            var player=c.player();
            if(player.level().hasChunkAt(p.pos)&&player.level().getBlockEntity(p.pos) instanceof CitDoorEntity e)
                e.saveEdit(player,p.token,p.room,p.color);
        }));
    }
}
