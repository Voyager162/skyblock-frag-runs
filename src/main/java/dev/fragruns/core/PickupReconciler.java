package dev.fragruns.core;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** A pickup animation and an inventory increase are two observations of ONE drop, in either order. */
public final class PickupReconciler {
    public enum Source { PACKET, INVENTORY }
    private static final class Credit {
        final Source source; final long at; int amount;
        Credit(Source source, long at, int amount) { this.source = source; this.at = at; this.amount = amount; }
    }
    private final Map<String, ArrayDeque<Credit>> credits = new HashMap<>();
    public int observe(String id, int amount, Source source, long now) {
        if (amount <= 0) return 0;
        var queue = credits.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        queue.removeIf(c -> now - c.at > 2_000);
        int remaining = amount;
        for (Credit c : queue) {
            if (c.source == source) continue;
            int matched = Math.min(remaining, c.amount);
            c.amount -= matched; remaining -= matched;
            if (remaining == 0) break;
        }
        queue.removeIf(c -> c.amount == 0);
        if (remaining > 0) queue.add(new Credit(source, now, remaining));
        return remaining;
    }
    public void reset() { credits.clear(); }
}
