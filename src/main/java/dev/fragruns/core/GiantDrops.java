package dev.fragruns.core;

import java.util.EnumMap;
import java.util.ArrayDeque;

/** One Watcher encounter per run. Two observations of its loot must credit that loot only once. */
public final class GiantDrops {
    private static final class Evidence {
        int peakHeld, inventoryGains, pickups, stashed, credited;
        final ArrayDeque<Gain> waiting=new ArrayDeque<>();
        int newlyConfirmed() {
            int confirmed = Math.max(credited,Math.max(stashed,Math.max(inventoryGains, pickups)));
            int gain = confirmed - credited;
            credited = confirmed;
            return gain;
        }
    }
    private static final class Gain {
        final long at;
        int count;
        Gain(long at,int count) { this.at=at; this.count=count; }
    }
    private final EnumMap<Giant,Evidence> evidence = new EnumMap<>(Giant.class);
    public GiantDrops() { reset(); }
    public void reset() {
        for (Giant giant : Giant.values()) evidence.put(giant, new Evidence());
    }
    /** Server inventory resyncs, slot moves, and metadata changes cannot make an old stack new again. */
    public int inventory(Giant giant, int held, boolean matchingEncounter) {
        inventory(giant,held,matchingEncounter,0);
        return settle(giant,0,true);
    }
    public void inventory(Giant giant,int held,boolean matchingEncounter,long now) {
        Evidence e = evidence.get(giant);
        int increase = Math.max(0, held - e.peakHeld);
        e.peakHeld = Math.max(e.peakHeld, held);
        if (matchingEncounter && increase>0) e.waiting.add(new Gain(now,increase));
    }
    public int settle(Giant giant,long now,boolean finish) {
        var e=evidence.get(giant);
        while (!e.waiting.isEmpty() && (finish || now-e.waiting.peek().at>=750)) e.inventoryGains+=e.waiting.remove().count;
        return e.newlyConfirmed();
    }
    public void withdrawal(Giant giant,int amount) {
        var waiting=evidence.get(giant).waiting;
        while (amount>0 && !waiting.isEmpty()) {
            var gain=waiting.getLast(); int used=Math.min(gain.count,amount);
            amount-=used;gain.count-=used;if (gain.count==0) waiting.removeLast();
        }
    }
    public int stashed(Giant giant,int count) {
        var e=evidence.get(giant);
        e.stashed=Math.max(e.stashed,count);
        return e.newlyConfirmed();
    }
    public void alreadyCredited(Giant giant,int count) {
        if (giant!=null) evidence.get(giant).credited=Math.max(evidence.get(giant).credited,count);
    }
    public boolean seenInInventory(Giant giant) { return evidence.get(giant).inventoryGains>0; }
    public int possiblyStashed(Giant giant) {
        var e=evidence.get(giant);return Math.max(e.stashed,Math.max(0,e.pickups-e.inventoryGains));
    }
    public int pickup(Giant giant, int amount) {
        Evidence e = evidence.get(giant);
        e.pickups += Math.max(amount, 0);
        return e.newlyConfirmed();
    }
}
