package dev.fragruns.tracking;

import dev.fragruns.core.*;
import dev.fragruns.data.ItemCatalog;
import dev.fragruns.data.SessionStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Batches a player's stash receipts with the accompanying inventory sync, including after a session ends. */
final class StashTracker {
    private static final Pattern PROFILE=Pattern.compile("Profile ID: ([0-9a-fA-F-]{36})");
    private static final Pattern MENU=Pattern.compile("(?:\\(\\d+/\\d+\\) )?Material Stash(?: \\(.*\\))?",Pattern.CASE_INSENSITIVE);
    private static final Pattern STORED=Pattern.compile("(?:Amount(?: Stored)?|Stored(?: Amount)?|Quantity|In Stash): ([0-9][0-9,]*)",Pattern.CASE_INSENSITIVE);
    private final Session session;
    private final SessionStore store;
    private final ItemCatalog catalog;
    private final GiantDrops drops;
    private final EnumMap<Giant,Integer> receipts=new EnumMap<>(Giant.class),before=new EnumMap<>(Giant.class);
    private final EnumMap<Giant,Integer> balances=new EnumMap<>(Giant.class);
    private final EnumMap<Giant,Integer> oldStock=new EnumMap<>(Giant.class);
    private final EnumMap<Giant,Integer> menuBefore=new EnumMap<>(Giant.class);
    private int menuId=-1;
    private long menuUntil;
    private String profile="",claimScope,stockScope;
    private boolean empty,hasBaseline,knownEmpty;
    private long claimUntil,settleAt,nextMenu;

    StashTracker(Session session,SessionStore store,ItemCatalog catalog,GiantDrops drops) {
        this.session=session;this.store=store;this.catalog=catalog;this.drops=drops;
    }
    String scope(Minecraft mc) { return mc.player==null?null:mc.player.getUUID()+"/"+profile; }
    private void loadStock(Minecraft mc) {
        String scope=scope(mc);if (scope==null || scope.equals(stockScope)) return;
        var stock=store.loadStashStock(scope);
        balances.clear();balances.putAll(stock.balances());oldStock.clear();oldStock.putAll(stock.old());
        knownEmpty=stock.knownEmpty();stockScope=scope;menuId=-1;menuUntil=0;
    }
    private void saveStock() {
        if (stockScope!=null) store.saveStashStock(stockScope,new SessionStore.StashStock(balances,oldStock,knownEmpty));
    }
    boolean collecting(long now) { return claimScope!=null || now<claimUntil; }
    void profile(Minecraft mc,String text,long now) {
        var match=PROFILE.matcher(text);
        if (match.matches() && !profile.equals(match.group(1))) {
            finish(mc,now); profile=match.group(1);stockScope=null;
        }
    }
    void disconnect(Minecraft mc,long now) {
        finish(mc,now); profile="";stockScope=null;claimUntil=0;
    }
    void begin(Minecraft mc,long now) {
        if (mc.player==null) return;
        loadStock(mc);
        if (claimScope==null) {
            claimScope=scope(mc);before.clear(); before.putAll(held(mc)); hasBaseline=true;
        }
        claimUntil=now+3_000; settleAt=claimUntil;
    }
    boolean claimMessage(Minecraft mc,Component message,String text,long now) {
        var emptyNotice=StashMessages.materialEmptied(text);
        if (!StashMessages.withdrawal(text) && !emptyNotice) return false;
        loadStock(mc);
        if (claimScope==null) {
            claimScope=scope(mc);hasBaseline=now<menuUntil;
            if (hasBaseline) { before.clear();before.putAll(menuBefore); }
        }
        if (StashMessages.withdrawal(text)) {
            var named=StashMessages.item(text.substring("From stash: ".length()));
            StashChat.items(message,named,catalog).forEach((g,n)->{
                receipts.merge(g,n,Integer::sum);drops.withdrawal(g,n);
            });
        }
        empty|=emptyNotice; settleAt=now+650; claimUntil=now+1_500;
        return true;
    }
    void tick(Minecraft mc,long now) {
        if (claimScope!=null && now>=settleAt) finish(mc,now);
        if (now<nextMenu || collecting(now) || !(mc.gui.screen() instanceof AbstractContainerScreen<?> menu)
                || !MENU.matcher(Signals.clean(menu.getTitle().getString())).matches()) return;
        nextMenu=now+250;
        loadStock(mc);
        if (menuId!=menu.getMenu().containerId || now>=menuUntil) {
            menuId=menu.getMenu().containerId;menuBefore.clear();menuBefore.putAll(held(mc));
        }
        menuUntil=now+2_000;
        var observed=new EnumMap<Giant,Integer>(Giant.class);
        for (var slot:menu.getMenu().slots) {
            if (slot.container==mc.player.getInventory() || !slot.hasItem()) continue;
            var stack=slot.getItem();var item=SkyblockItems.read(stack,catalog);
            var g=item==null?null:Giant.fromItem(item.id());
            if (g==null) continue;
            int count=stack.getCount();var lore=stack.get(DataComponents.LORE);
            if (lore!=null) for (var line:lore.lines()) {
                var amount=STORED.matcher(Signals.clean(line.getString()));
                if (amount.matches()) try { count=Integer.parseInt(amount.group(1).replace(",",""));break; }
                catch (NumberFormatException ignored) { /* Use the visible stack count. */ }
            }
            observed.merge(g,count,Math::max);
        }
        var gains=new EnumMap<Giant,Integer>(Giant.class);
        boolean changed=observed.entrySet().stream().anyMatch(e->!e.getValue().equals(balances.get(e.getKey())));
        observed.forEach((g,n)->{
            Integer prior=balances.put(g,n);
            if (prior!=null && prior.equals(n)) return;
            oldStock.computeIfPresent(g,(k,old)->Math.min(old,n));
            try {
                if (!store.hasStashEvidence(session,scope(mc),g)) oldStock.put(g,n);
                else if (prior!=null || knownEmpty) {
                    int delta=n-(prior==null?0:prior);if (delta>0) gains.put(g,delta);
                }
            } catch (IOException e) { oldStock.put(g,n); }
        });
        if (!gains.isEmpty()) settle(mc,scope(mc),gains,false,true);
        if (changed) saveStock();
    }
    private void finish(Minecraft mc,long now) {
        if (claimScope==null) return;
        if (hasBaseline && mc.player!=null && claimScope.equals(scope(mc))) {
            var after=held(mc);
            receipts.replaceAll((g,n)->Math.max(n,after.getOrDefault(g,0)-before.getOrDefault(g,0)));
        }
        if (!receipts.isEmpty() || empty) {
            var earned=new EnumMap<Giant,Integer>(Giant.class);
            receipts.forEach((g,n)->{
                int prior=Math.min(n,oldStock.getOrDefault(g,0));
                if (prior>0) oldStock.put(g,oldStock.get(g)-prior);
                if (n>prior) earned.put(g,n-prior);
            });
            settle(mc,claimScope,earned,empty,false);
            receipts.forEach((g,n)->balances.computeIfPresent(g,(key,old)->Math.max(0,old-n)));
            if (empty) { balances.clear();oldStock.clear();knownEmpty=true; }
            saveStock();
            if (now<menuUntil && mc.player!=null) { menuBefore.clear();menuBefore.putAll(held(mc)); }
        }
        receipts.clear();before.clear();hasBaseline=false;empty=false;claimScope=null;
    }
    private void settle(Minecraft mc,String owner,Map<Giant,Integer> items,boolean emptied,boolean fromBalance) {
        try {
            var resolved=store.settleStash(session,owner,items,emptied,fromBalance,g->{
                var item=catalog.get(g.itemId);return item==null?0:item.npcPrice();
            });
            drops.alreadyCredited(session.currentGiant(),session.currentFragments());
            if (!resolved.choices().isEmpty() && mc.player!=null) mc.player.sendSystemMessage(Component.literal("Frag Runs: a stash fragment matches runs in different sessions. Open the run in F9 → Session log and choose Assign stash drop."));
        } catch (IOException e) { LoggerFactory.getLogger("fragruns").warn("Could not reconcile the fragment stash with saved runs",e); }
    }
    private Map<Giant,Integer> held(Minecraft mc) {
        var result=new EnumMap<Giant,Integer>(Giant.class);var inv=mc.player.getInventory();
        for(int slot=0;slot<inv.getContainerSize();slot++) {
            var item=SkyblockItems.read(inv.getItem(slot),catalog);
            Giant g=item==null?null:Giant.fromItem(item.id());
            if (g!=null) result.merge(g,item.count(),Integer::sum);
        }
        return result;
    }
}
