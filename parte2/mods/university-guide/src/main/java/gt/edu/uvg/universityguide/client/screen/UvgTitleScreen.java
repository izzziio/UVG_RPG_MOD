package gt.edu.uvg.universityguide.client.screen;

import gt.edu.uvg.universityguide.UniversityGuideMod;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Inicio UVG con acciones vanilla y fondo estático de bajo coste. */
@EventBusSubscriber(modid=UniversityGuideMod.MOD_ID,value=Dist.CLIENT)
public final class UvgTitleScreen extends TitleScreen {
    private static boolean classic;
    private int left,menuWidth,menuTop;
    private final String version=ModList.get().getModContainerById(UniversityGuideMod.MOD_ID)
            .map(mod->mod.getModInfo().getVersion().toString()).orElse("");
    public UvgTitleScreen(){super(false);}

    @SubscribeEvent public static void opening(ScreenEvent.Opening event){
        if(!classic&&event.getNewScreen()!=null&&event.getNewScreen().getClass()==TitleScreen.class)
            event.setNewScreen(new UvgTitleScreen());
    }

    // Etiquetas de inicio en español, conservando las acciones originales.
    private static Component spanishLabel(Component message){
        if(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text){
            return switch(text.getKey()){
                case "menu.singleplayer" -> Component.literal("Un jugador");
                case "menu.multiplayer" -> Component.literal("Multijugador");
                case "menu.online" -> Component.literal("Minecraft Realms");
                case "menu.options" -> Component.literal("Opciones...");
                case "menu.quit" -> Component.literal("Salir del juego");
                default -> message;
            };
        }
        return message;
    }

    @Override protected void init(){
        super.init();left=width>=600?width/9:24;menuWidth=Math.min(width<500?200:240,width-48);
        // Conserva callbacks, restricciones multijugador y pantallas de opciones.
        var buttons=new ArrayList<Button>();
        for(var widget:new ArrayList<>(renderables))if(widget instanceof Button button&&!(button instanceof PlainTextButton)&&button.getWidth()>24)buttons.add(button);
        menuTop=Math.max(80,(height-buttons.size()*23)/2+16);
        for(int i=0;i<buttons.size();i++){
            var original=buttons.get(i);removeWidget(original);
            var button=addRenderableWidget(new MaterialGuideTheme.M3Button(left,menuTop+i*23,menuWidth,20,
                    spanishLabel(original.getMessage()),unused->original.onPress(),i==0));
            button.active=original.active;button.setTooltip(original.getTooltip());
        }
        int icon=0;
        for(var widget:renderables)if(widget instanceof AbstractWidget button&&button.getWidth()<=24){
            button.setPosition(width-54+icon++*24,height-38);
        }
        addRenderableWidget(new PlainTextButton(width-100,10,90,12,Component.translatable("screen.universityguide.classic_menu"),
                button->{classic=true;minecraft.setScreen(new TitleScreen(false));},font));
    }

    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float tick){
        g.fillGradient(0,0,width,height,0xFF071511,0xFF19352B);
        int scene=left+menuWidth+32,available=width-scene-26;
        if(available>80){
            int base=height*3/4;
            building(g,scene+available/3,base,available*2/3,height/3,0xFF263B36);
            building(g,scene,base+14,available*3/5,height*2/5,0xFF334C44);
            g.fill(scene-12,base+15,width,base+18,0xFF76C89E);
            g.fillGradient(scene-12,base+18,width,height,0xFF233F33,0xFF10241D);
            for(int i=0;i<7;i++)g.fill(scene+8+i*available/8,base+35,width,base+36,0x182FFE9E);
            g.drawString(font,"CAMPUS UVG",scene,base-height*2/5-12,0xFF9ABBAA,false);
        }
        int top=Math.max(14,menuTop-75);
        g.fill(left-12,top-10,left+menuWidth+12,menuTop+6*23+5,0x99101D17);
        g.fill(left-12,top-10,left-10,menuTop+6*23+5,0xFF70D8A0);
        g.drawString(font,"UNIVERSIDAD DEL VALLE",left,top,0xFF89A99A,false);
        g.pose().pushPose();g.pose().translate(left,top+17,0);g.pose().scale(1.8F,1.8F,1);
        g.drawString(font,MaterialGuideTheme.fit(font,Component.translatable("screen.universityguide.home_title").getString(),menuWidth/2),0,0,0xFFEAF8EE,false);
        g.pose().popPose();
        g.drawString(font,MaterialGuideTheme.fit(font,Component.translatable("screen.universityguide.home_subtitle").getString(),menuWidth),left,top+45,0xFFA4C0B0,false);
        g.drawString(font,"UVG · "+version,10,height-11,0xFF8AA697,false);
    }

    // Fachadas dibujadas con pocos rectángulos; no hay mundo 3D ni animación adicional.
    private void building(GuiGraphics g,int x,int base,int w,int h,int color){
        g.fill(x,base-h,x+w,base,color);g.fill(x-3,base-h-4,x+w+3,base-h,0xFF57786A);
        int cell=Math.max(9,w/6),row=Math.max(13,h/4);
        for(int xx=x+6;xx<x+w-5;xx+=cell)for(int yy=base-h+8;yy<base-8;yy+=row){
            g.fillGradient(xx,yy,Math.min(xx+cell-5,x+w-5),Math.min(yy+row-6,base-5),0xFF87BDB7,0xFF365A55);
            g.fill(xx,yy,Math.min(xx+cell-5,x+w-5),yy+1,0xFFB1D3BD);
        }
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float tick){
        renderBackground(g,mouseX,mouseY,tick);
        for(var widget:renderables)widget.render(g,mouseX,mouseY,tick);
    }
}
