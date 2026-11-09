package com.postedsignage.client;

import com.postedsignage.PostedSignage;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Registro local de las pruebas; esta clase se excluye del JAR distribuible. */
@EventBusSubscriber(modid=PostedSignage.MOD_ID,value=Dist.CLIENT)
public final class FpsProbe {
    private static final boolean ENABLED=Boolean.getBoolean("postedsignage.fpsProbe");
    private static final long[] FRAMES=new long[8192];
    private static long lastFrame,lastSample,sum,max;
    private static int count;
    private static BufferedWriter writer;
    private static boolean failed;
    private FpsProbe() {}

    // Mide periodos entre fotogramas; escribe una muestra por segundo.
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!ENABLED||failed)return;
        long now=System.nanoTime();
        if(lastFrame!=0){
            long delta=now-lastFrame;
            sum+=delta;max=Math.max(max,delta);
            if(count<FRAMES.length)FRAMES[count++]=delta;
        }
        lastFrame=now;
        if(lastSample==0)lastSample=now;
        if(now-lastSample<1_000_000_000L||count==0)return;
        try {
            if(writer==null){
                Path path=Path.of(System.getProperty("postedsignage.fpsProbeFile"));
                Files.createDirectories(path.getParent());
                boolean header=!Files.exists(path)||Files.size(path)==0;
                writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                if(header)writer.write("utc,world,fps,mean_fps,p99_ms,max_ms,x,y,z,yaw,pitch,entities,chunks,focused,paused,screen,width,height,render_chunks,vsync,frame_cap\n");
            }
            var mc=Minecraft.getInstance();var player=mc.player;
            var server=mc.getSingleplayerServer();
            String world=server!=null?server.getWorldData().getLevelName():mc.level==null?"MENU":"MULTIPLAYER";
            long[] sorted=Arrays.copyOf(FRAMES,count);Arrays.sort(sorted);
            double p99=sorted[Math.min(count-1,(int)Math.ceil(count*.99)-1)]/1e6;
            String screen=mc.screen==null?"GAME":mc.screen.getClass().getSimpleName();
            writer.write(String.format(Locale.ROOT,
                    "%s,%s,%d,%.2f,%.3f,%.3f,%.3f,%.3f,%.3f,%.2f,%.2f,%d,%d,%s,%s,%s,%d,%d,%d,%s,%d%n",
                    Instant.now(),world.replace(',',' '),mc.getFps(),count*1e9/sum,p99,max/1e6,
                    player==null?0:player.getX(),player==null?0:player.getY(),player==null?0:player.getZ(),
                    player==null?0:player.getYRot(),player==null?0:player.getXRot(),
                    mc.level==null?0:mc.level.getEntityCount(),mc.level==null?0:mc.level.getChunkSource().getLoadedChunksCount(),
                    mc.isWindowActive(),mc.isPaused(),screen,mc.getWindow().getWidth(),mc.getWindow().getHeight(),
                    mc.options.getEffectiveRenderDistance(),mc.options.enableVsync().get(),mc.options.framerateLimit().get()));
            writer.flush();
            // Cambia solo entre los mundos de prueba tras una petición local.
            Path request=Path.of(System.getProperty("postedsignage.fpsProbeFile")).resolveSibling("cambiar-mundo.txt");
            if(Files.exists(request)){
                String destination=Files.readString(request,StandardCharsets.UTF_8).trim();Files.delete(request);
                if(destination.matches("FPS_[0-9]{2}_[A-Za-z]+_1610")&&server!=null&&world.startsWith("FPS ")
                        &&Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(destination).resolve("level.dat"))){
                    mc.tell(()->{
                        if(mc.level==null||mc.getSingleplayerServer()==null)return;
                        mc.level.disconnect();
                        mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen());
                        mc.createWorldOpenFlows().openWorld(destination,()->mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
                    });
                }
            }
        } catch(Exception error){
            // Un fallo del archivo no interrumpe el juego.
            failed=true;System.err.println("Medición de FPS desactivada: "+error);
        }
        count=0;sum=0;max=0;lastSample=now;
    }
}
