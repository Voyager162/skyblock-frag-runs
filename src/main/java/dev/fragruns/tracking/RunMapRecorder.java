package dev.fragruns.tracking;

import dev.fragruns.core.Session;
import dev.fragruns.core.Signals;
import dev.fragruns.data.DungeonLayout;
import dev.fragruns.data.RunMaps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.MapItem;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;

/** Sample world data even while the HUD is hidden. All file writes are ordered off the client thread. */
public final class RunMapRecorder implements AutoCloseable {
    private final Session session;
    private final RunMaps files;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Frag Runs map writer").factory());
    private StellaRooms stella;
    private boolean lookup=true;
    private UUID run;
    private DungeonLayout best;
    private long nextSample;
    private long lastGeneration=-1;
    private volatile UUID failed;
    public RunMapRecorder(Session session,RunMaps files) { this.session=session; this.files=files; }
    public void tick(Minecraft mc,boolean bossPhase) {
        UUID current=session.currentRunId();
        if (!Objects.equals(current,run)) { run=current; best=null; nextSample=0; }
        long now=System.nanoTime();
        if (run==null || !session.running() || bossPhase || mc.level==null || mc.player==null || now<nextSample) return;
        nextSample=now+250_000_000L;
        DungeonLayout layout=stella(mc);
        if (layout==null && (best==null || !best.schematic())) layout=serverMap(mc);
        if (layout==null || best!=null && best.schematic() && layout.tileCount()<best.tileCount()) return;
        long generation=files.generation();
        if (layout.equals(best) && lastGeneration==generation && !run.equals(failed)) return;
        best=layout; failed=null; lastGeneration=generation;
        UUID id=run; DungeonLayout snapshot=layout;
        writer.execute(()->{
            try { files.save(id,generation,snapshot); }
            catch (java.io.IOException e) { failed=id; LoggerFactory.getLogger("fragruns").warn("Could not save run layout",e); }
        });
    }
    private DungeonLayout stella(Minecraft mc) {
        try {
            if (lookup) {
                lookup=false;
                if (FabricLoader.getInstance().isModLoaded("stella")) stella=new StellaRooms();
            }
            return stella==null?null:stella.read(mc.level);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            stella=null;
            LoggerFactory.getLogger("fragruns").warn("StellaNav layout unavailable, using the server map",unavailable);
            return null;
        }
    }
    private DungeonLayout serverMap(Minecraft mc) {
        var inv=mc.player.getInventory();
        for (int slot=0;slot<inv.getContainerSize();slot++) {
            var stack=inv.getItem(slot);
            if (!stack.has(DataComponents.MAP_ID)) continue;
            String name=Signals.clean(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
            if (!name.contains("magical map") && !name.contains("dungeon map")) continue;
            var data=MapItem.getSavedData(stack,mc.level);
            if (data==null || data.colors.length!=128*128) continue;
            boolean hasRooms=false;
            for (byte tile:data.colors) if ((tile&255)>=4 && tile!=data.colors[0]) { hasRooms=true;break; }
            if (!hasRooms) continue;
            var markers=new ArrayList<DungeonLayout.Marker>();
            for (var m:data.getDecorations()) {
                if (markers.size()==256) break;
                markers.add(new DungeonLayout.Marker(m.x(),m.y(),m.rot()&15,m.type().value().assetId().toString()));
            }
            return new DungeonLayout(1,Base64.getEncoder().encodeToString(data.colors),List.of(),List.of(),markers);
        }
        return null;
    }
    @Override public void close() {
        writer.shutdown();
        try { writer.awaitTermination(2,TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
