package dev.fragruns.tracking;

import dev.fragruns.core.Giant;
import dev.fragruns.core.EssenceBalances;
import dev.fragruns.core.PickupReconciler;
import dev.fragruns.core.PurseTracker;
import dev.fragruns.core.Session;
import dev.fragruns.core.Signals;
import dev.fragruns.data.ItemCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameTracker {
    private final Session session;
    private final ItemCatalog catalog;
    private final PickupReconciler pickups = new PickupReconciler();
    private final PurseTracker purse = new PurseTracker();
    private final EssenceBalances essenceBalances = new EssenceBalances();
    private Map<String,Integer> previousInventory = new HashMap<>();
    private final Set<String> knownUuids = new HashSet<>();
    private final Map<Integer,Giant> giantEntities = new HashMap<>();
    private ClientLevel lastLevel;
    private boolean inventoryReady;
    private long containerUntil, lastF7, salvageUntil;
    private boolean bossPhase;
    private String location = "Waiting for Hypixel SkyBlock";
    private boolean precisePurse;
    private long leftAt;
    private int tickCount;

    public GameTracker(Session session, ItemCatalog catalog) { this.session=session; this.catalog=catalog; }
    public String location() { return location; }
    public boolean precisePurse() { return precisePurse; }
    public boolean essenceBaseline() { return essenceBalances.hasBaseline(); }
    private static long now() { return System.nanoTime()/1_000_000; }

    public void reset() {
        previousInventory.clear(); knownUuids.clear(); giantEntities.clear(); pickups.reset(); purse.reset();
        inventoryReady=false; bossPhase=false; salvageUntil=0; containerUntil=0; lastF7=0;
        essenceBalances.invalidate();
    }
    public void disconnected() {
        endRun("Disconnected",true); essenceBalances.invalidate(); lastLevel=null; location="Disconnected"; inventoryReady=false;
    }
    public void finishSession() { if(session.inDungeon()) purse.flush(now(),true,session.loot()::addCoins); }

    public void tick(Minecraft client) {
        long now=now();
        if (lastLevel != client.level) {
            if (lastLevel != null) endRun("Left dungeon",false);
            lastLevel=client.level; inventoryReady=false; purse.reset(); giantEntities.clear(); bossPhase=false;
        }
        if (client.level==null || client.player==null || client.getConnection()==null) return;
        var server=client.getCurrentServer();
        String address=server==null ? "" : server.ip.toLowerCase(java.util.Locale.ROOT).split(":")[0];
        boolean hypixel=address.equals("hypixel.net") || address.endsWith(".hypixel.net");
        var board=client.level.getScoreboard();
        var objective=board.getDisplayObjective(DisplaySlot.SIDEBAR);
        List<String> lines=new ArrayList<>();
        String title=objective==null ? "" : objective.getDisplayName().getString();
        if(objective!=null) for(var entry:board.listPlayerScores(objective)) {
            if(entry.owner().startsWith("#")) continue;
            Component display=entry.display()==null ? Component.literal(entry.owner()) : entry.display();
            lines.add(PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()),display).getString());
        }
        boolean f7=hypixel && Signals.isF7(title,lines);
        location=f7 ? "The Catacombs • Floor VII" : hypixel ? "Outside Floor VII" : "Waiting for Hypixel SkyBlock";
        if(hypixel && !f7 && now-leftAt>2_000) {
            List<String> tab=new ArrayList<>();
            for(var info:client.getConnection().getOnlinePlayers()) if(info.getTabListDisplayName()!=null) tab.add(info.getTabListDisplayName().getString());
            if(tab.stream().anyMatch(line->Signals.clean(line).contains("Dungeon Hub")) || lines.stream().anyMatch(line->Signals.clean(line).contains("Dungeon Hub")))
                essenceBalances.observeHub(EssenceBalances.parse(tab),(id,count)->{if(session.running()) session.loot().addEssence(id,count);});
        }
        if (!session.running()) return;
        if(f7) {
            lastF7=now;
            if(!session.inDungeon()) {
                session.enterDungeon(); essenceBalances.enter(); purse.reset(); pickups.reset(); giantEntities.clear(); bossPhase=false;
                inventoryReady=false;
            }
        } else if(session.inDungeon() && (objective!=null && !lines.isEmpty() || now-lastF7>2_000)) {
            // A single temporarily blank scoreboard must not split one run into two.
            endRun("Left Floor VII",false);
        }
        if(!session.inDungeon()) return;
        var balance=Signals.purse(lines);
        precisePurse=balance.isPresent();
        if(balance.isPresent()) purse.observe(balance.getAsDouble(),now);
        purse.flush(now,false,session.loot()::addCoins);
        tickCount++;
        if(!inventoryReady || tickCount%2==0) scanInventory(client,now);
        if(!bossPhase && tickCount%2==0) scanGiants(client);
    }

    private void endRun(String reason,boolean interrupted) {
        if(!session.inDungeon()) return;
        purse.flush(now(),true,session.loot()::addCoins);
        session.leaveDungeon(reason,interrupted);
        leftAt=now();essenceBalances.leave();
        inventoryReady=false; purse.reset(); pickups.reset(); giantEntities.clear();
    }

    private void scanInventory(Minecraft client,long now) {
        boolean container=client.gui.screen() instanceof AbstractContainerScreen<?> && !(client.gui.screen() instanceof InventoryScreen);
        if(container) containerUntil=now+500;
        Map<String,Integer> current=new HashMap<>();
        Map<String,SkyblockItems.Drop> samples=new HashMap<>();
        var inv=client.player.getInventory();
        for(int slot=0;slot<inv.getContainerSize();slot++) {
            var drop=SkyblockItems.read(inv.getItem(slot),catalog);
            if(drop==null) continue;
            // Aggregate across slots: inventory sorting and hotbar changes are not pickups.
            String key=drop.id()+"|"+drop.tier()+"|"+drop.quality()+"|"+drop.stars()+"|"+drop.uuid();
            current.merge(key,drop.count(),Integer::sum); samples.put(key,drop);
        }
        if(inventoryReady && !container && now>containerUntil) {
            for(var entry:current.entrySet()) {
                int gain=entry.getValue()-previousInventory.getOrDefault(entry.getKey(),0);
                if(gain>0) collect(samples.get(entry.getKey()),gain,PickupReconciler.Source.INVENTORY,now);
            }
        } else {
            if(!inventoryReady) pickups.reset();
            for(var d:samples.values()) if(!d.uuid().isBlank()) knownUuids.add(d.uuid());
        }
        previousInventory=current; inventoryReady=true;
    }

    public void pickup(ItemEntity entity,int count) {
        Minecraft client=Minecraft.getInstance();
        if(!session.inDungeon() || client.player==null || entity.getOwner()==client.player) return;
        var drop=SkyblockItems.read(entity.getItem(),catalog);
        if(drop!=null) collect(drop,Math.min(count,drop.count()),PickupReconciler.Source.PACKET,now());
    }

    private void collect(SkyblockItems.Drop drop,int count,PickupReconciler.Source source,long now) {
        String identity=drop.id()+"|"+drop.tier()+"|"+drop.quality()+"|"+drop.stars()+"|"+drop.uuid();
        int fresh=pickups.observe(identity,count,source,now);
        if(fresh<=0) return;
        if(!drop.uuid().isBlank() && !knownUuids.add(drop.uuid())) return;
        ItemCatalog.Item definition=catalog.get(drop.id());
        String essence=definition==null ? null : definition.essenceId();
        int salvage=definition==null ? 0 : definition.salvageAmount(drop.tier(),drop.quality(),drop.stars());
        session.loot().addItem(drop.id(),drop.name(),fresh,essence,salvage,definition==null ? 0 : definition.npcPrice());
        Giant giant=Giant.fromItem(drop.id());
        if(giant!=null) session.collectFragment(giant,fresh);
    }

    private void scanGiants(Minecraft client) {
        for(Entity entity:client.level.entitiesForRendering()) {
            // A fragment on the ground (or a player with an unusual name) is not a live giant.
            if(!(entity instanceof LivingEntity living) || entity==client.player || !entity.hasCustomName()) continue;
            String name=Signals.clean(entity.getCustomName().getString());
            Giant type=Giant.fromName(name);
            if(type==null || name.contains("Handle") || name.contains("Eye") || name.contains("Rock") || name.contains("Bola")) continue;
            if(!(entity instanceof ArmorStand) && !(entity instanceof net.minecraft.world.entity.monster.Giant)) continue;
            session.observeGiant(type);
            boolean newlySeen=giantEntities.put(entity.getId(),type)==null;
            if(entity instanceof ArmorStand) {
                // Hypixel often uses a separate armor stand for the name/health tag.
                Entity nearest=null; double distance=144;
                for(Entity candidate:client.level.entitiesForRendering()) if(newlySeen && candidate instanceof net.minecraft.world.entity.monster.Giant) {
                    double d=candidate.distanceToSqr(entity);
                    if(d<distance) { distance=d; nearest=candidate; }
                }
                if(nearest!=null) giantEntities.put(nearest.getId(),type);
                if(name.matches(".*(?:\\s|^)(?:0|0/[^ ]+)\\s*[❤♥].*")) session.giantDefeated(type);
            } else if(living.isDeadOrDying()) session.giantDefeated(type);
        }
    }

    public void entityDeath(Entity entity) {
        if(!session.inDungeon() || entity==null || bossPhase) return;
        Giant type=giantEntities.get(entity.getId());
        if(type==null && entity instanceof net.minecraft.world.entity.monster.Giant && entity.hasCustomName()) type=Giant.fromName(entity.getCustomName().getString());
        if(type!=null) session.giantDefeated(type);
    }

    public void chat(String text,boolean overlay) {
        if(!session.running() || overlay) return;
        long now=now();
        String clean=Signals.clean(text);
        if(Signals.externalMoney(clean)) purse.excludeTransaction(now);
        if(clean.startsWith("[Bazaar]") && clean.contains("Essence")) essenceBalances.invalidate();
        if(clean.startsWith("You salvaged ")) { salvageUntil=now+2_000; return; }
        if(now<=salvageUntil) {
            var salvaged=Signals.essence(clean,false);
            if(salvaged!=null) essenceBalances.salvaged(salvaged.id(),salvaged.count());
            return;
        }
        if(!session.inDungeon()) return;
        if(clean.startsWith("[BOSS] Maxor:") || clean.startsWith("[BOSS] Storm:")) bossPhase=true;
        if(Signals.watcherCleared(clean)) session.watcherCleared();
        var essence=Signals.essence(clean,now<=salvageUntil);
        if(essence!=null) { session.loot().addEssence(essence.id(),essence.count());essenceBalances.found(essence.id(),essence.count()); }
    }
}
