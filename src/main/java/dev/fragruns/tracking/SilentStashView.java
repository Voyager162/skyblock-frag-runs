package dev.fragruns.tracking;

import dev.fragruns.core.Giant;
import dev.fragruns.core.StashMessages;
import dev.fragruns.core.Signals;
import dev.fragruns.data.ItemCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.HashedStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import net.minecraft.world.inventory.*;
import java.util.*;
import java.util.function.BiConsumer;

/** Owns only the response to our material-stash query. A vanilla container model receives the server's slot syncs;
 * no Screen is constructed, installed, rendered or dismissed for that query. */
public final class SilentStashView {
    private final ItemCatalog catalog;
    private final BiConsumer<Map<Giant,Integer>,Boolean> accept;
    private final EnumMap<Giant,Integer> pages=new EnumMap<>(Giant.class);
    private final Set<String> signatures=new HashSet<>();
    private final Map<Integer,Long> recentCloses=new HashMap<>();
    private ClientPacketListener connection;
    private ClientLevel world;
    private ChestMenu model;
    private String title="";
    private boolean waiting,late,sending,full,hub;
    private long deadline,quietUntil,eligibleAt,retryAt;
    private int attempts,expectedPage=1;
    SilentStashView(ItemCatalog catalog,BiConsumer<Map<Giant,Integer>,Boolean> accept) { this.catalog=catalog;this.accept=accept; }
    private static long now() {return System.nanoTime()/1_000_000;}
    private int stateId;
    void tick(Minecraft mc,boolean inHub,boolean needed,long time) {
        if (connection!=mc.getConnection()) {reset(mc);connection=mc.getConnection();}
        if (world!=mc.level) {cancel(mc);world=mc.level;hub=false;}
        if (hub!=inHub) {hub=inHub;eligibleAt=time+700;attempts=0;retryAt=0;}
        if (!inHub || !needed || mc.gui.screen()!=null || mc.gui.overlay()!=null) {
            if (waiting || model!=null) cancel(mc);
            if (!needed) attempts=0;
            return;
        }
        if (model!=null) {
            if (mc.player==null || mc.player.containerMenu!=mc.player.inventoryMenu || time>=deadline) {fail(mc,time);return;}
            if (!full || time<quietUntil) return;
            var page=MaterialStashPage.read(model,title,catalog);
            if (page==null) return; // The server may still be replacing filler slots.
            if (page.number()!=expectedPage && (expectedPage>1 || page.number()>0)) return;
            if (!signatures.add(page.signature())) return;
            if (page.counts().keySet().stream().anyMatch(pages::containsKey)) {fail(mc,time);return;}
            pages.putAll(page.counts());
            if (page.nextSlot()>=0) {
                if (expectedPage>=128 || mc.gameMode==null) {fail(mc,time);return;}
                expectedPage++; waiting=true;deadline=time+4_000;
                // Only a verified navigation arrow in the server-defined footer can reach this input.
                connection.send(new ServerboundContainerClickPacket(model.containerId,stateId,(short)page.nextSlot(),(byte)0,
                        ContainerInput.PICKUP,Int2ObjectMaps.emptyMap(),HashedStack.EMPTY));
                return;
            }
            var snapshot=Map.copyOf(pages); finish(mc); accept.accept(snapshot,false);return;
        }
        if (waiting) {if(time>=deadline) fail(mc,time);return;}
        if (attempts>=3 || time<eligibleAt || time<retryAt || mc.player==null || connection==null
                || mc.player.containerMenu!=mc.player.inventoryMenu || !mc.player.inventoryMenu.getCarried().isEmpty()) return;
        pages.clear();signatures.clear();expectedPage=1;attempts++;waiting=true;late=false;deadline=time+5_000;
        sending=true;
        try {connection.sendCommand("viewstash material");}
        finally {sending=false;}
    }
    public void command(String command) {
        if (sending) return;
        String c=command.toLowerCase(Locale.ROOT).strip();
        if (c.equals("viewstash") || c.startsWith("viewstash ")) {
            cancel(Minecraft.getInstance());late=false;retryAt=now()+5_000;
        } else if (StashMessages.pickupCommand(c)) {cancel(Minecraft.getInstance());retryAt=now()+2_000;}
    }
    public boolean open(ClientboundOpenScreenPacket packet) {
        var mc=Minecraft.getInstance(); String name=packet.getTitle().getString();
        if (connection==null || mc.getConnection()!=connection || mc.player==null) return false;
        recentCloses.remove(packet.getContainerId()); // Vanilla reuses IDs.
        if (model!=null && model.containerId!=packet.getContainerId()) recentCloses.put(model.containerId,now()+8_000);
        if (!MaterialStashPage.stashResponse(name)) {
            // The server has already replaced its menu. A close packet here can dismiss the player's new menu.
            if (waiting || model!=null) {model=null;cancel(mc);}
            return false;
        }
        if (!waiting && !late) return false;
        if (!MaterialStashPage.title(name)) {model=null;fail(mc,now());closeId(mc,packet.getContainerId());late=false;return true;}
        if (!waiting || !hub || world!=mc.level || mc.gui.screen()!=null || mc.gui.overlay()!=null
                || mc.player.containerMenu!=mc.player.inventoryMenu) {
            model=null;cancel(mc);closeId(mc,packet.getContainerId());late=false;return true;
        }
        if (!(packet.getType().create(packet.getContainerId(),mc.player.getInventory()) instanceof ChestMenu chest)) {
            model=null;fail(mc,now());closeId(mc,packet.getContainerId());return true;
        }
        model=chest;title=name;waiting=false;late=false;full=false;deadline=now()+4_000;
        return true;
    }
    public void contents(ClientboundContainerSetContentPacket packet) {
        if (model==null || packet.containerId()!=model.containerId || packet.items().size()!=model.slots.size()) return;
        var top=model.getContainer();
        for(int i=0;i<top.getContainerSize();i++) top.setItem(i,packet.items().get(i));
        stateId=packet.stateId(); full=true;quietUntil=now()+350;
    }
    public void slot(ClientboundContainerSetSlotPacket packet) {
        if (model==null || packet.getContainerId()!=model.containerId) return;
        int slot=packet.getSlot(); stateId=packet.getStateId();
        if (slot>=0 && slot<model.getContainer().getContainerSize()) {
            model.getContainer().setItem(slot,packet.getItem());quietUntil=now()+350;
        }
    }
    public boolean closed(int id) {
        var mc=Minecraft.getInstance();
        if (mc.getConnection()!=connection) return false;
        if (model!=null && model.containerId==id) {
            recentCloses.put(id,now()+8_000);
            model=null;full=false;
            if (expectedPage<=1 || !waiting) {waiting=false;retryAt=now()+4_000;}
            return true;
        }
        return now()<recentCloses.getOrDefault(id,0L);
    }
    public boolean reply(String raw) {
        if ((!waiting && !late) || !StashMessages.emptyView(Signals.clean(raw))) return false;
        var mc=Minecraft.getInstance();
        boolean valid=waiting && expectedPage==1 && hub && mc.level==world && mc.getConnection()==connection;
        finish(mc);
        if (valid) accept.accept(Map.of(),true);
        return true;
    }
    void cancel(Minecraft mc) {
        boolean outstanding=waiting||late;
        finish(mc);late=outstanding;
    }
    private void fail(Minecraft mc,long time) {cancel(mc);retryAt=time+Math.min(20_000,4_000L*Math.max(1,attempts));}
    private void closeId(Minecraft mc,int id) {
        if (connection==null || mc.getConnection()!=connection) return;
        recentCloses.entrySet().removeIf(e->e.getValue()<=now());
        recentCloses.put(id,now()+8_000);connection.send(new ServerboundContainerClosePacket(id));
    }
    private void finish(Minecraft mc) {
        if (model!=null) closeId(mc,model.containerId);
        model=null;waiting=false;full=false;late=false;pages.clear();signatures.clear();
    }
    void reset(Minecraft mc) {
        cancel(mc);late=false;hub=false;recentCloses.clear();connection=null;world=null;attempts=0;retryAt=0;
    }
}
