package dev.fragruns.tracking;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.fragruns.FragRunsClient;
import dev.fragruns.core.Session;
import dev.fragruns.data.RunMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import org.joml.Vector2f;
import org.slf4j.LoggerFactory;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.*;

/** Reads only the map rectangle reported by a renderer actually drawing this run's HUD. */
public final class RunMapRecorder implements AutoCloseable {
    private record Crop(UUID run,long generation,int x,int y,int width,int height) {}
    private final Session session;
    private final RunMaps files;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Frag Runs map writer").factory());
    private UUID run;
    private long nextHud, nextItem;
    private Crop pending;
    private boolean capturedHud, warned;
    private volatile boolean busy;
    private UUID writtenRun;
    private int[] lastPixels;
    private int lastWidth;
    private long lastGeneration;

    public RunMapRecorder(Session session,RunMaps files) { this.session=session; this.files=files; }
    public static void mapRendered(GuiGraphicsExtractor g,int start,int end) {
        var mod=FragRunsClient.INSTANCE;
        if (mod!=null && mod.maps()!=null && !mod.tracker().inBossPhase()) mod.maps().locate(g,start,end);
    }
    private void locate(GuiGraphicsExtractor g,int start,int end) {
        if (run==null || !session.running() || busy || System.nanoTime()<nextHud) return;
        var mc=Minecraft.getInstance();
        if (mc.gui.screen()!=null || mc.gui.overlay()!=null || mc.gui.hud.isHidden() || !run.equals(session.currentRunId())) return;
        var first=g.pose().transformPosition(start,start,new Vector2f());
        var last=g.pose().transformPosition(end,end,new Vector2f());
        int scale=mc.getWindow().getGuiScale();
        int x=Math.round(first.x*scale), top=Math.round(first.y*scale);
        int w=Math.round(last.x*scale)-x, h=Math.round(last.y*scale)-top;
        var target=mc.gameRenderer.mainRenderTarget();
        if (w<8 || h<8 || w>2048 || h>2048 || x<0 || top<0 || x+w>target.width || top+h>target.height) return;
        pending=new Crop(run,files.generation(),x,target.height-top-h,w,h);
    }
    public void afterGui() {
        Crop crop=pending;
        pending=null;
        if (crop==null || busy || !session.running() || !crop.run.equals(session.currentRunId())) return;
        var texture=Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture();
        if (texture==null || texture.getFormat().blockSize()!=4) return;
        nextHud=System.nanoTime()+1_000_000_000L;
        GpuBuffer buffer=null;
        busy=true;
        try {
            var device=RenderSystem.getDevice();
            buffer=device.createBuffer(()->"Frag Runs dungeon map",GpuBuffer.USAGE_MAP_READ|GpuBuffer.USAGE_COPY_DST,4L*crop.width*crop.height);
            final var readback=buffer;
            device.createCommandEncoder().copyTextureToBuffer(texture,readback,0,()->{
                try (readback; var view=readback.map(true,false)) {
                    var rgba=view.data().order(ByteOrder.nativeOrder());
                    int w=Math.min(crop.width,RunMaps.MAX_SIZE), h=Math.min(crop.height,RunMaps.MAX_SIZE);
                    int[] pixels=new int[w*h];
                    for (int y=0;y<h;y++) {
                        int row=(crop.height-1-y*crop.height/h)*crop.width;
                        for (int x=0;x<w;x++) pixels[y*w+x]=ARGB.fromABGR(rgba.getInt(4*(row+x*crop.width/w)))|0xFF000000;
                    }
                    if (crop.run.equals(run)) capturedHud=true;
                    enqueue(crop.run,crop.generation,pixels,w,h);
                } catch (RuntimeException e) { busy=false; report(e); }
            },0,crop.x,crop.y,crop.width,crop.height);
        } catch (RuntimeException e) {
            if (buffer!=null) buffer.close();
            busy=false;
            report(e);
        }
    }
    public void tick(Minecraft mc,boolean bossPhase) {
        UUID current=session.currentRunId();
        long now=System.nanoTime();
        if (!java.util.Objects.equals(current,run)) {
            run=current; pending=null; capturedHud=false; nextHud=0; nextItem=now+500_000_000L;
        }
        if (run==null || !session.running() || bossPhase || capturedHud || busy || now<nextItem || mc.player==null || mc.level==null) return;
        nextItem=now+1_500_000_000L;
        var inv=mc.player.getInventory();
        for (int slot=0;slot<inv.getContainerSize();slot++) {
            var item=inv.getItem(slot);
            if (!item.has(DataComponents.MAP_ID)) continue;
            String name=item.getHoverName().getString();
            if (!name.contains("Magical Map") && !name.contains("Dungeon Map")) continue;
            var map=MapItem.getSavedData(item,mc.level);
            if (map==null || map.colors.length!=128*128) continue;
            int[] pixels=new int[map.colors.length];
            boolean hasRooms=false;
            for (int i=0;i<pixels.length;i++) {
                pixels[i]=MapColor.getColorFromPackedId(map.colors[i]&255);
                if (pixels[i]!=0 && map.colors[i]!=map.colors[0]) hasRooms=true;
            }
            if (hasRooms) { busy=true; enqueue(run,files.generation(),pixels,128,128); }
            return;
        }
    }
    private void enqueue(UUID id,long generation,int[] pixels,int w,int h) {
        writer.execute(()->{
            try {
                if (!id.equals(writtenRun) || generation!=lastGeneration || w!=lastWidth || !Arrays.equals(lastPixels,pixels)) {
                    files.save(id,generation,pixels,w,h);
                    writtenRun=id; lastWidth=w; lastPixels=pixels; lastGeneration=generation;
                }
            } catch (Exception e) { report(e); }
            finally { busy=false; }
        });
    }
    private synchronized void report(Exception e) {
        if (!warned) LoggerFactory.getLogger("fragruns").warn("Could not record dungeon map",e);
        warned=true;
    }
    @Override public void close() {
        writer.shutdown();
        try { writer.awaitTermination(2,TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
