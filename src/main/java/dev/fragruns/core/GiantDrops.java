package dev.fragruns.core;

import java.util.EnumMap;

/** One Watcher encounter per run. Two observations of its loot must credit that loot only once. */
public final class GiantDrops {
    private static final class Evidence {
        int peakHeld, inventoryGains, pickups, credited;
        int newlyConfirmed() {
            int confirmed = Math.max(inventoryGains, pickups);
            int gain = confirmed - credited;
            credited = confirmed;
            return gain;
        }
    }
    private final EnumMap<Giant,Evidence> evidence = new EnumMap<>(Giant.class);
    public GiantDrops() { reset(); }
    public void reset() {
        for (Giant giant : Giant.values()) evidence.put(giant, new Evidence());
    }
    /** Server inventory resyncs, slot moves, and metadata changes cannot make an old stack new again. */
    public int inventory(Giant giant, int held, boolean matchingEncounter) {
        Evidence e = evidence.get(giant);
        int increase = Math.max(0, held - e.peakHeld);
        e.peakHeld = Math.max(e.peakHeld, held);
        if (matchingEncounter) e.inventoryGains += increase;
        return e.newlyConfirmed();
    }
    public int pickup(Giant giant, int amount) {
        Evidence e = evidence.get(giant);
        e.pickups += Math.max(amount, 0);
        return e.newlyConfirmed();
    }
}
