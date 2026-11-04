package dev.fragruns.tracking;

import dev.fragruns.core.*;
import dev.fragruns.data.ItemCatalog;
import dev.fragruns.data.SessionStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Read exact material balances in the hub. A generic inventory-full message contains no loot evidence. */
final class StashTracker {
    private static final Pattern PROFILE=Pattern.compile("Profile ID: ([0-9a-fA-F-]{36})");
    private final Session session;
    private final SessionStore store;
    private final ItemCatalog catalog;
    private final GiantDrops drops;
    private final HubStashAudit audit=new HubStashAudit();
    private final EnumMap<Giant,Integer> receipts=new EnumMap<>(Giant.class),before=new EnumMap<>(Giant.class);
    private String profile="",claimScope;
    private boolean empty,hasBaseline,lastHub,wasRunning;
    private long claimUntil,settleAt;
    private int manualId=-1;
    private long manualChanged;
    StashTracker(Session session,SessionStore store,ItemCatalog catalog,GiantDrops drops) {
        this.session=session;this.store=store;this.catalog=catalog;this.drops=drops;
    }
    String scope(Minecraft mc) { return mc.player==null || profile.isEmpty()?"":mc.player.getUUID()+"/"+profile; }
    boolean collecting(long now) { return claimScope!=null || now<claimUntil; }
    boolean pending() { return audit.pending(); }
    void profile(Minecraft mc,String text,long now) {
        var match=PROFILE.matcher(text);
        if (match.matches() && !profile.equals(match.group(1))) {
            finish(mc,now);profile=match.group(1);audit.scope(scope(mc));
            run(false);
        }
    }
    void run(boolean normalExit) {
        if (session.inDungeon()) audit.run(session.id(),session.currentRunId(),normalExit);
    }
    void entered(Minecraft mc) {
        run(false);
        String key=scope(mc);
        if (!key.isEmpty() && audit.hasBaseline()) session.stash().overflow(session.currentRunId(),key);
    }
    void booked(Giant giant,int quantity) { audit.booked(session.id(),session.currentRunId(),giant,quantity); }
    void untrackedActivity() { if (audit.hasBaseline()) audit.dirtyOutsideRun(); }
    void disconnect(Minecraft mc,long now) {
        finish(mc,now);profile="";audit.reset();claimUntil=0;lastHub=false;wasRunning=false;manualId=-1;
    }
    void begin(Minecraft mc,long now) {
        if(mc.player==null) return;
        if(claimScope==null) { claimScope=scope(mc);before.clear();before.putAll(held(mc));hasBaseline=true; }
        claimUntil=now+3_000;settleAt=claimUntil;
    }
    boolean claimMessage(Minecraft mc,Component message,String text,long now) {
        boolean empties=StashMessages.materialEmptied(text);
        if (!empties && !StashMessages.withdrawal(text)) return false;
        if (claimScope==null) { claimScope=scope(mc);hasBaseline=false; }
        if (StashMessages.withdrawal(text)) {
            var named=StashMessages.item(text.substring("From stash: ".length()));
            StashChat.items(message,named,catalog).forEach((g,n)->{receipts.merge(g,n,Integer::sum);drops.withdrawal(g,n);});
        }
        empty|=empties;settleAt=now+650;claimUntil=now+1_500;
        return true;
    }
    private void finish(Minecraft mc,long now) {
        if (claimScope==null) return;
        if (claimScope.equals(scope(mc)) && !claimScope.isEmpty()) {
            if (hasBaseline && mc.player!=null) {
                var after=held(mc);
                receipts.replaceAll((g,n)->Math.max(n,after.getOrDefault(g,0)-before.getOrDefault(g,0)));
            }
            audit.withdrawn(receipts);
            if (empty && lastHub && !session.inDungeon()) snapshot(Map.of(),true);
        }
        before.clear();receipts.clear();claimScope=null;empty=false;hasBaseline=false;
    }
    void tick(Minecraft mc,boolean inHub,long now) {
        lastHub=inHub;
        if (wasRunning!=session.running() && !audit.pending()) audit.dirtyOutsideRun();
        wasRunning=session.running();
        if (claimScope!=null && now>=settleAt) finish(mc,now);
        boolean allowed=inHub && !scope(mc).isEmpty() && !collecting(now);
        boolean needed=audit.needed(session.id(),session.running());
        // Only inspect menus the player explicitly opened. Never request or navigate a stash menu.
        if (allowed && needed && manualId>=0 && now-manualChanged>=400
                && mc.gui.screen() instanceof AbstractContainerScreen<?> screen && screen.getMenu() instanceof ChestMenu menu
                && menu.containerId==manualId && MaterialStashPage.title(screen.getTitle().getString())) {
            var page=MaterialStashPage.read(menu,screen.getTitle().getString(),catalog);
            if (page!=null && page.number()<=1 && page.total()<=1 && page.nextSlot()<0) { snapshot(page.counts(),false);manualId=-1; }
        }
    }
    void contents(int id,int size) {
        var mc=Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> s && MaterialStashPage.title(s.getTitle().getString())
                && s.getMenu().containerId==id && s.getMenu().slots.size()==size) {
            manualId=id;manualChanged=System.nanoTime()/1_000_000;
        }
    }
    void slot(int id,int slot) {
        if (id==manualId) manualChanged=System.nanoTime()/1_000_000;
    }
    private void snapshot(Map<Giant,Integer> balances,boolean emptyStash) {
        var mc=Minecraft.getInstance();if(scope(mc).isEmpty()) return;
        try {
            if (audit.pending()) {
                store.applyHubBalances(session,audit.intervalId(),audit.targets(),audit.gains(balances),g->{
                    var item=catalog.get(g.itemId);return item==null?0:item.npcPrice();
                });
                drops.alreadyCredited(session.currentGiant(),session.currentFragments());
            }
            audit.accept(session.id(),balances);
            store.saveStashStock(scope(mc),new SessionStore.StashStock(balances,Map.of(),emptyStash));
        } catch (IOException e) { LoggerFactory.getLogger("fragruns").warn("Could not save the verified stash balance",e); }
    }
    private Map<Giant,Integer> held(Minecraft mc) {
        var result=new EnumMap<Giant,Integer>(Giant.class);var inv=mc.player.getInventory();
        for(int i=0;i<inv.getContainerSize();i++) {
            var item=SkyblockItems.read(inv.getItem(i),catalog);Giant g=item==null?null:Giant.fromItem(item.id());
            if(g!=null) result.merge(g,item.count(),Integer::sum);
        }
        return result;
    }
}
