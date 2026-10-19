package com.postedsignage.client;
import com.postedsignage.*;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// Permite elegir el código y el color de la puerta.
public final class CitDoorScreen extends Screen {
    private final CitDoorPayloads.Open data;
    private EditBox room;
    private int selected;
    private Button save;
    private static final String[] NAMES={"blue","green","red","yellow","light_blue","purple","orange"};
    // Inicializa la pantalla con sus datos de apertura.
    public CitDoorScreen(CitDoorPayloads.Open data){super(Component.translatable("screen.posted_signage.cit_door"));this.data=data;selected=data.color();}
    // Abre la pantalla con los datos recibidos.
    public static void open(CitDoorPayloads.Open p){Minecraft.getInstance().setScreen(new CitDoorScreen(p));}
    // Crea y coloca los controles de la pantalla.
    @Override protected void init(){
        String value=room==null?data.room():room.getValue();
        int left=width/2-126,top=height/2-83;
        room=new EditBox(font,left+46,top+39,190,20,Component.translatable("screen.posted_signage.room_number"));
        room.setMaxLength(5);room.setValue(value);
        room.setFilter(v->v.isEmpty()||v.toUpperCase(Locale.ROOT).matches("[0-9]{1,4}[A-Z]?"));
        addRenderableWidget(room);
        for(int i=0;i<7;i++){final int index=i;
            addRenderableWidget(Button.builder(Component.literal("■").withColor(CitDoorEntity.COLORS[i]),b->selected=index)
                    .bounds(left+9+i*34,top+78,30,22).tooltip(Tooltip.create(Component.translatable("color.minecraft."+NAMES[i]))).build());
        }
        save=addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->{
            PacketDistributor.sendToServer(new CitDoorPayloads.Save(data.pos(),room.getValue().toUpperCase(Locale.ROOT),selected,data.token()));onClose();
        }).bounds(left+8,top+126,116,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(left+130,top+126,116,20).build());
        room.setResponder(v->save.active=CitDoorEntity.validRoom(v.toUpperCase(Locale.ROOT)));
        save.active=CitDoorEntity.validRoom(value.toUpperCase(Locale.ROOT));setInitialFocus(room);
    }
    // Dibuja el contenido usando el estado actual.
    @Override public void render(GuiGraphics g,int x,int y,float tick){
        int left=width/2-126,top=height/2-83;
        g.fill(left,top,left+252,top+160,0xF0202927);
        super.render(g,x,y,tick);
        g.drawCenteredString(font,title,width/2,top+12,0xFFFFFF);
        g.drawString(font,"CIT-",left+10,top+45,0xFFFFFF,false);
        g.drawCenteredString(font,"CIT-"+room.getValue().toUpperCase(Locale.ROOT),width/2,top+109,CitDoorEntity.COLORS[selected]);
    }
    // Mantiene el mundo activo con esta pantalla abierta.
    @Override public boolean isPauseScreen(){return false;}
}
