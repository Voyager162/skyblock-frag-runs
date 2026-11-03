package dev.fragruns.core;

import java.util.ArrayDeque;
import java.util.function.DoubleConsumer;

/** Delays deltas briefly so transaction chat arriving after scoreboard packets can exclude them. */
public final class PurseTracker {
    private record Change(long at, double amount) {}
    private final ArrayDeque<Change> pending = new ArrayDeque<>();
    private Double last;
    private Double outsideBalance;
    private long outsideAt, returnUntil = Long.MIN_VALUE;
    private long excludeUntil = -1;
    public void reset() {
        pending.clear();
        last = outsideBalance = null;
        returnUntil = Long.MIN_VALUE;
        excludeUntil = -1;
    }
    public void excludeTransaction(long now) {
        pending.clear();
        last = outsideBalance = null;
        returnUntil = Long.MIN_VALUE;
        excludeUntil = now + 2_000;
    }
    public void enterDungeon(long now) {
        last = now - outsideAt <= 10_000 ? outsideBalance : null;
        outsideBalance = null;
        returnUntil = Long.MIN_VALUE;
    }
    public void leaveDungeon(long now, boolean interrupted, DoubleConsumer sink) {
        flush(now, true, sink);
        returnUntil = interrupted ? Long.MIN_VALUE : now + 10_000;
    }
    /** If the dungeon hides the purse, reconcile the first exact balance back in the hub. */
    public void observeOutside(double balance, long now) {
        if (!Double.isFinite(balance) || balance < 0) return;
        if (now <= returnUntil) observe(balance, now);
        returnUntil = Long.MIN_VALUE;
        outsideBalance = balance;
        outsideAt = now;
        last = null;
    }
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
