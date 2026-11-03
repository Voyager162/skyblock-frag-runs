package dev.fragruns.core;

import java.util.ArrayDeque;
import java.util.function.DoubleConsumer;

/** Delays deltas briefly so transaction chat arriving after scoreboard packets can exclude them. */
public final class PurseTracker {
    private record Change(long at, double amount) {}
    private final ArrayDeque<Change> pending = new ArrayDeque<>();
    private Double last;
    private long excludeUntil = -1;
    public void reset() { pending.clear(); last = null; excludeUntil = -1; }
    public void excludeTransaction(long now) { pending.clear(); excludeUntil = now + 2_000; }
    public void observe(double purse, long now) {
        if (!Double.isFinite(purse) || purse < 0) return;
        if (last != null && now > excludeUntil) {
            double delta = purse - last;
            if (delta != 0) pending.add(new Change(now, delta));
        }
        last = purse;
    }
    public void flush(long now, boolean finishing, DoubleConsumer sink) {
        while (!pending.isEmpty() && (finishing || now - pending.peek().at >= 1_500)) sink.accept(pending.remove().amount);
    }
}
