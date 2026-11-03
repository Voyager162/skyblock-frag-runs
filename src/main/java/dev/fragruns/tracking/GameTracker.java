package dev.fragruns.tracking;

import dev.fragruns.core.Giant;
import dev.fragruns.core.GiantDrops;
import dev.fragruns.core.HuntingBox;
import dev.fragruns.core.HuntingShards;
import dev.fragruns.core.DungeonPresence;
import dev.fragruns.core.EssenceBalances;
import dev.fragruns.core.PickupReconciler;
import dev.fragruns.core.PurseTracker;
import dev.fragruns.core.Session;
import dev.fragruns.core.Signals;
import dev.fragruns.core.StashMessages;
import dev.fragruns.data.ItemCatalog;
import dev.fragruns.data.SessionStore;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumMap;
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
    private final DungeonPresence presence = new DungeonPresence();
    private final GiantDrops fragments = new GiantDrops();
    private final StashTracker stash;
    private final HuntingBox huntingBox = new HuntingBox();
    private final PickupReconciler shardReceipts = new PickupReconciler();
    private long checkBoxUntil = Long.MIN_VALUE;
    private final EnumMap<Giant,Integer> carriedFragments = new EnumMap<>(Giant.class);
    private Map<String,Integer> previousInventory = new HashMap<>();
    private final Set<String> knownUuids = new HashSet<>();
    private final WatcherGiants giants;
    private ClientLevel lastLevel;
    private Connection network;
    private boolean transferring;
    private long transferUntil;
    private boolean inventoryReady;
    private long containerUntil, salvageUntil;
    private boolean bossPhase, watcherAwake;
    private String location = "Waiting for Hypixel SkyBlock";
    private boolean precisePurse;
    private long leftAt;
    private int tickCount;

    public GameTracker(Session session,ItemCatalog catalog,SessionStore store) {
        this.session=session; this.catalog=catalog; this.giants=new WatcherGiants(session);
        stash=new StashTracker(session,store,catalog,fragments);
    }
    public String location() { return location; }
    public boolean inBossPhase() { return bossPhase; }
    public boolean precisePurse() { return precisePurse; }
    public boolean essenceBaseline() { return essenceBalances.hasBaseline(); }
    private static long now() { return System.nanoTime()/1_000_000; }

    public void reset() {
        resetTrackingBaselines();
        knownUuids.clear();
        giants.reset();
        carriedFragments.clear();
        bossPhase=false;
        watcherAwake=false;
    }
    public void resetTrackingBaselines() {
        previousInventory.clear();
        inventoryReady = false;
        pickups.reset();
        fragments.reset();
        fragments.alreadyCredited(session.currentGiant(),session.currentFragments());
        clearShardBaseline();
        purse.reset();
        salvageUntil = 0;
        containerUntil = 0;
        essenceBalances.invalidate();
    }
    private void clearShardBaseline() {
        huntingBox.reset();
        shardReceipts.reset();
        checkBoxUntil = Long.MIN_VALUE;
    }
    public void disconnected(Connection connection) {
        // A proxy can reconfigure the same physical connection between SkyBlock instances.
        boolean warp = transferring && connection == network && connection.isConnected();
        endRun(warp ? "Left dungeon" : "Disconnected", !warp);
        if (!warp) {
            stash.disconnect(Minecraft.getInstance(),now());
            presence.reset();
            carriedFragments.clear();
            clearShardBaseline();
            essenceBalances.invalidate();
            purse.reset();
            network = null;
        }
        lastLevel = null;
        inventoryReady = false;
        location = "Disconnected";
    }
    public void finishSession() {
        settleFragments(now(),true);
        purse.flush(now(),true,session.loot()::addCoins);
        huntingBox.settle(now(), true, this::creditBoxShard);
    }

    public void tick(Minecraft client) {
        long now=now();
        if (client.level == null && lastLevel != null) {
            endRun(transferring ? "Left dungeon" : "Disconnected", !transferring);
            lastLevel = null;
        }
        if (client.level==null || client.player==null || client.getConnection()==null) return;
        syncWorld(client, now);
        if (!onHypixel(client)) {
            location = "Waiting for Hypixel SkyBlock";
            return;
        }
        stash.tick(client,now);
        List<String> lines = sidebarLines(client.level.getScoreboard(), client.player.getTeam());
        List<String> tab = new ArrayList<>();
        for (var info : client.getConnection().getOnlinePlayers()) {
            if (info.getTabListDisplayName() != null) tab.add(info.getTabListDisplayName().getString());
        }
        Signals.Area area = Signals.area(lines);
        if (area == Signals.Area.UNKNOWN || area == Signals.Area.DUNGEON) {
            Signals.Area tabArea = Signals.area(tab);
            if (tabArea != Signals.Area.UNKNOWN) area = tabArea;
        }
        if (transferring && now>=transferUntil) transferring=false;
        if (!transferring) presence.observe(area, now);
        boolean f7 = presence.inF7();
        location = f7 ? "The Catacombs • Floor VII" : "Outside Floor VII";
        if (!f7 && !session.inDungeon() && now-leftAt>2_000 && (tab.stream().anyMatch(GameTracker::inHub) || lines.stream().anyMatch(GameTracker::inHub))) {
            var owner = session.rewardOwner();
            essenceBalances.observeHub(EssenceBalances.parse(tab),(id,count)->session.credit(owner,ledger->ledger.addEssence(id,count)),
                    (id,count)->{ if (session.running()) session.loot().addEssence(id,count); });
        }
        if (!session.running()) {
            if (session.paused() && session.inDungeon() && !f7) endRun("Left while paused", true);
            return;
        }
        if (f7 && !transferring && !session.inDungeon()) {
            huntingBox.settle(now,true,this::creditBoxShard);
            session.enterDungeon(presence.confirmedAge(now));
            essenceBalances.enter();
            purse.enterDungeon(now);
            pickups.reset();
            fragments.reset();
            carriedFragments.forEach((giant, held) -> fragments.inventory(giant, held, false));
            carriedFragments.clear();
            inventoryReady = false;
            giants.reset();
            bossPhase = false;
            watcherAwake = false;
        } else if (!f7 && session.inDungeon()) {
            endRun("Left Floor VII", false);
        }
        observeHuntingBox(client, f7 || now <= checkBoxUntil, now);
        huntingBox.settle(now, false, this::creditBoxShard);
        var balance=Signals.purse(lines);
        if (balance.isEmpty()) balance=Signals.purse(tab);
        precisePurse=balance.isPresent();
        if (balance.isPresent()) {
            var owner = session.rewardOwner();
            java.util.function.DoubleConsumer credit = coins -> session.credit(owner,ledger -> ledger.addCoins(coins));
            if (f7) purse.observe(balance.getAsDouble(), now,credit);
            else if (area == Signals.Area.OUTSIDE) purse.observeOutside(balance.getAsDouble(), now,credit);
        }
        purse.flush(now,false,session.loot()::addCoins);
        if(!session.inDungeon()) return;
        tickCount++;
        if(!bossPhase && !transferring && tickCount%2==0) for (Entity entity:client.level.entitiesForRendering()) giants.observe(client,entity,now,false);
        if(!inventoryReady || tickCount%2==0) scanInventory(client,now);
        settleFragments(now,false);
    }

    private void observeHuntingBox(Minecraft client, boolean eligible, long now) {
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)
                || !HuntingShards.isBox(screen.getTitle().getString())) return;
        for (var slot : screen.getMenu().slots) {
            if (slot.container == client.player.getInventory() || !slot.hasItem()) continue;
            var stack = slot.getItem();
            var shard = HuntingShards.byLabel(stack.getHoverName().getString());
            var lore = stack.get(DataComponents.LORE);
            if (shard == null || lore == null) continue;
            for (var line : lore.lines()) {
                int owned = HuntingShards.owned(line.getString());
                if (owned >= 0) {
                    var owner = session.rewardOwner();
                    huntingBox.observe(shard,owned,eligible,now,(reward,count)->creditShard(owner,reward,count,PickupReconciler.Source.REWARD));
                    break;
                }
            }
        }
    }
    private void creditBoxShard(HuntingShards.Shard shard, int count) {
        creditShard(shard, count, PickupReconciler.Source.REWARD);
    }
    private void creditShard(HuntingShards.Shard shard, int count, PickupReconciler.Source source) {
        creditShard(session.rewardOwner(),shard,count,source);
    }
    private void creditShard(java.util.UUID owner,HuntingShards.Shard shard,int count,PickupReconciler.Source source) {
        int fresh = shardReceipts.observe(shard.productId(), count, source, now());
        if (fresh > 0) session.credit(owner,ledger->ledger.addItem(shard.productId(),shard.name()+" Shard",fresh,null,0,0));
    }

    private static boolean inHub(String line) { return Signals.clean(line).endsWith("Dungeon Hub"); }

    private static boolean onHypixel(Minecraft client) {
        if (client.getConnection() == null) return false;
        var server = client.getCurrentServer();
        String host = server == null ? "" : server.ip.toLowerCase(java.util.Locale.ROOT).split(":", 2)[0];
        String brand = client.getConnection().serverBrand();
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net")
                || brand != null && brand.toLowerCase(java.util.Locale.ROOT).startsWith("hypixel");
    }

    private void syncWorld(Minecraft client, long now) {
        Connection next = client.getConnection().getConnection();
        if (network != next) {
            endRun("Disconnected", true);
            presence.reset();
            carriedFragments.clear();
            clearShardBaseline();
            purse.reset();
            essenceBalances.invalidate();
            network = next;
            lastLevel = null;
        }
        if (lastLevel != client.level) {
            endRun("Left dungeon", false);
            lastLevel = client.level;
            presence.arrived(now);
            transferring = false;
            inventoryReady = false;
        }
    }

    /** Hypixel uses team prefix + suffix around a synthetic score holder. Modern rows can use a display or fixed value. */
    static List<String> sidebarLines(Scoreboard board, PlayerTeam playerTeam) {
        var objective = playerTeam == null ? null : playerTeam.getColor().map(TeamColor::displaySlot)
                .map(board::getDisplayObjective).orElse(null);
        if (objective == null) objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return List.of();
        List<String> lines = new ArrayList<>();
        for (var entry : board.listPlayerScores(objective)) {
            var team = board.getPlayersTeam(entry.owner());
            String suffix = entry.numberFormatOverride() instanceof FixedFormat fixed ? " " + fixed.value().getString() : "";
            if (team != null) {
                lines.add(team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString() + suffix);
            }
            if (!entry.isHidden()) lines.add(PlayerTeam.formatNameForTeam(team,entry.ownerName()).getString() + suffix);
        }
        return lines;
    }

    private void endRun(String reason,boolean interrupted) {
        if(!session.inDungeon()) return;
        settleFragments(now(),true);
        purse.leaveDungeon(now(), interrupted, session.loot()::addCoins);
        session.leaveDungeon(reason,interrupted || session.paused());
        if (interrupted || session.paused()) clearShardBaseline();
        else checkBoxUntil = now() + 60_000;
        leftAt=now();essenceBalances.leave();
        inventoryReady=false; pickups.reset(); giants.reset();
    }

    private void scanInventory(Minecraft client,long now) {
        boolean container=client.gui.screen() instanceof AbstractContainerScreen<?> && !(client.gui.screen() instanceof InventoryScreen);
        if(container) containerUntil=now+500;
        Map<String,Integer> current=new HashMap<>();
        Map<String,SkyblockItems.Drop> samples=new HashMap<>();
        EnumMap<Giant,Integer> heldFragments = new EnumMap<>(Giant.class);
        var inv=client.player.getInventory();
        for(int slot=0;slot<inv.getContainerSize();slot++) {
            var drop=SkyblockItems.read(inv.getItem(slot),catalog);
            if(drop==null) continue;
            // Withdrawing an existing shard also places it in inventory. Count its reward or box gain instead.
            if (HuntingShards.byId(drop.id()) != null) continue;
            Giant giant = Giant.fromItem(drop.id());
            if (giant != null) {
                heldFragments.merge(giant, drop.count(), Integer::sum);
                continue;
            }
            // Aggregate across slots: inventory sorting and hotbar changes are not pickups.
            String key=drop.id()+"|"+drop.tier()+"|"+drop.quality()+"|"+drop.stars()+"|"+drop.uuid();
            current.merge(key,drop.count(),Integer::sum); samples.put(key,drop);
        }
        boolean trackingGains = inventoryReady && !container && now > containerUntil;
        heldFragments.forEach((giant, held) -> {
            // A server can award a fragment directly even if the giant's name tag was out of render range.
            boolean encounter=session.currentGiant()==giant || watcherAwake && (session.currentGiant()==null || session.inferredGiant());
            boolean eligible = trackingGains && !bossPhase && encounter && !stash.collecting(now);
            fragments.inventory(giant,held,eligible,now);
        });
        if(trackingGains) {
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
        if(!session.running() || !session.inDungeon() || client.level!=lastLevel || client.player==null || entity.getOwner()==client.player) return;
        var drop=SkyblockItems.read(entity.getItem(),catalog);
        if (drop == null) return;
        int amount = Math.min(count, drop.count());
        var shard = HuntingShards.byId(drop.id());
        if (shard != null) {
            creditShard(shard, amount, PickupReconciler.Source.PACKET);
            return;
        }
        Giant giant = Giant.fromItem(drop.id());
        if (giant != null) {
            if (!bossPhase && !stash.collecting(now())) creditFragment(giant, fragments.pickup(giant, amount));
        } else collect(drop,amount,PickupReconciler.Source.PACKET,now());
    }

    private void creditFragment(Giant giant, int count) {
        if (count <= 0) return;
        var item = catalog.get(giant.itemId);
        session.loot().addItem(giant.itemId, giant.fragmentName, count, null, 0, item == null ? 0 : item.npcPrice());
        session.collectFragment(giant, count);
    }
    private void settleFragments(long now,boolean finish) {
        if (session.running() && session.inDungeon()) {
            for (Giant g:Giant.values()) creditFragment(g,fragments.settle(g,now,finish));
            Giant giant=session.currentGiant();
            if (giant!=null && fragments.seenInInventory(giant)) session.stash().physicalInventory(session.currentRunId());
        }
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
    }

    public void entityChanged(Entity entity,boolean death,boolean removed) {
        var mc=Minecraft.getInstance();
        if (!trackingEntity(mc,entity)) return;
        if (removed) giants.removed(mc,entity,now(),watcherAwake);
        else giants.observe(mc,entity,now(),death);
    }
    public void entityDamaged(Entity entity) {
        var mc=Minecraft.getInstance();
        if (trackingEntity(mc,entity)) giants.damaged(mc,entity,now());
    }
    private boolean trackingEntity(Minecraft mc,Entity entity) {
        return entity!=null && session.running() && session.inDungeon() && !bossPhase && !transferring
                && mc.player!=null && mc.level==lastLevel && entity.level()==lastLevel;
    }
    public void stashCommand(String command) {
        var mc=Minecraft.getInstance();
        if (StashMessages.pickupCommand(command) && mc.player!=null && onHypixel(mc)) stash.begin(mc,now());
    }
    public void serverMessage(Minecraft client, Component message) {
        if (client.level == null || client.player==null || !onHypixel(client)) return;
        long now=now();
        syncWorld(client, now);
        String clean=Signals.clean(message.getString());
        stash.profile(client,clean,now);
        if (stash.claimMessage(client,message,clean,now)) return;
        var named=StashMessages.deposited(clean);
        if (named!=null || StashMessages.overflow(clean)) {
            if (session.running() && session.inDungeon() && !bossPhase && (watcherAwake || session.currentGiant()!=null)) {
                String scope=stash.scope(client);
                var items=StashChat.items(message,named,catalog);
                if (items.isEmpty()) session.stash().overflow(session.currentRunId(),scope);
                for (var entry:items.entrySet()) {
                    var g=entry.getKey();
                    if (session.currentGiant()!=null && session.currentGiant()!=g && !session.inferredGiant()) continue;
                    creditFragment(g,fragments.stashed(g,entry.getValue()));
                    session.stash().deposited(session.currentRunId(),scope,entry.getValue());
                }
            }
            return;
        }
        presence.announce(Signals.announcedFloor(clean), now);
        if (clean.equals("Warping...") || clean.startsWith("Sending to server ") && clean.endsWith("...")) {
            transferring = true;
            transferUntil=now+15_000;
            if (session.running() && client.player != null) {
                if (session.inDungeon()) scanInventory(client, now);
                carriedFragments.clear();
                var inventory = client.player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    var stack = SkyblockItems.read(inventory.getItem(slot), catalog);
                    if (stack == null) continue;
                    Giant giant = Giant.fromItem(stack.id());
                    if (giant != null) carriedFragments.merge(giant, stack.count(), Integer::sum);
                }
            }
        }
        if (clean.startsWith("[NPC] Mort: Here, I found this map") || clean.startsWith("[NPC] Mort: You should find it useful")
                || clean.equals("[NPC] Mort: Good luck.") || clean.startsWith("[BOSS] The Watcher: ")) {
            if (!transferring) presence.dungeonMessage(now);
        }
        if (!session.running()) return;
        var shardReceipt = HuntingShards.receipt(clean);
        if (shardReceipt != null) {
            int earned = huntingBox.receipt(shardReceipt, session.inDungeon(), now);
            if (earned > 0) creditBoxShard(shardReceipt.shard(), earned);
            return;
        }
        if (clean.startsWith("FUSION!") || clean.startsWith("You syphoned ") || clean.startsWith("You siphoned ")
                || clean.startsWith("[Bazaar]") && clean.contains("Shard") || clean.startsWith("Trade completed")) {
            clearShardBaseline();
        }
        if(Signals.externalMoney(clean)) purse.excludeTransaction(now);
        if(clean.startsWith("[Bazaar]") && clean.contains("Essence")) essenceBalances.invalidate();
        if(clean.startsWith("You salvaged ")) { salvageUntil=now+2_000; return; }
        if(now<=salvageUntil) {
            var salvaged=Signals.essence(clean,false);
            if(salvaged!=null) essenceBalances.salvaged(salvaged.id(),salvaged.count());
            return;
        }
        if(!session.inDungeon()) return;
        if(clean.startsWith("[BOSS] The Watcher:") || clean.equals("The BLOOD DOOR has been opened!")) watcherAwake=true;
        if(watcherAwake && clean.startsWith("Your ") && clean.contains(" hit ") && clean.endsWith(" damage.")) giants.combat(now);
        if(clean.startsWith("[BOSS] Maxor:") || clean.startsWith("[BOSS] Storm:")) bossPhase=true;
        if(Signals.watcherCleared(clean)) session.watcherCleared();
        var essence=Signals.essence(clean,now<=salvageUntil);
        if(essence!=null) { session.loot().addEssence(essence.id(),essence.count());essenceBalances.found(essence.id(),essence.count()); }
    }
}
