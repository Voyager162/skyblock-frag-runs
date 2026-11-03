package dev.fragruns.core;

/** Hypixel announces the selected floor in the old world, several seconds before the actual warp. */
public final class DungeonPresence {
    private static final long ENTRY_TIMEOUT_MS = 30_000;
    private long pendingUntil = Long.MIN_VALUE;
    private long arrivalHintUntil = Long.MIN_VALUE;
    private boolean inF7;

    public boolean inF7() { return inF7; }

    public void announce(Signals.Area floor, long now) {
        if (floor == Signals.Area.F7) pendingUntil = now + ENTRY_TIMEOUT_MS;
        if (floor == Signals.Area.OTHER_FLOOR) pendingUntil = Long.MIN_VALUE;
    }

    public void arrived(long now) {
        inF7 = pendingUntil >= now;
        arrivalHintUntil = pendingUntil;
        pendingUntil = Long.MIN_VALUE;
    }

    public void observe(Signals.Area area, long now) {
        switch (area) {
            case F7 -> {
                if (!inF7) pendingUntil = Long.MIN_VALUE;
                inF7 = true;
                arrivalHintUntil = Long.MIN_VALUE;
            }
            case OTHER_FLOOR -> { inF7 = false; arrivalHintUntil = Long.MIN_VALUE; }
            case OUTSIDE -> inF7 = false;
            case DUNGEON -> {
                // The new world can briefly inherit the old hub sidebar before Mort or the tab list arrives.
                if (!inF7 && (pendingUntil >= now || arrivalHintUntil >= now)) {
                    inF7 = true;
                    pendingUntil = Long.MIN_VALUE;
                    arrivalHintUntil = Long.MIN_VALUE;
                }
            }
            case UNKNOWN -> { /* Keep the confirmed floor while this world is loaded. */ }
        }
    }

    public void reset() {
        pendingUntil = Long.MIN_VALUE;
        arrivalHintUntil = Long.MIN_VALUE;
        inF7 = false;
    }
}
