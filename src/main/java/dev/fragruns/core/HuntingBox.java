package dev.fragruns.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ObjIntConsumer;

/** Reconcile exact, visible box balances with personal captures. Missing pages are never read as zero. */
public final class HuntingBox {
    private static final long SETTLE_MS = 1_500;
    private final Map<HuntingShards.Shard,Balance> balances = new HashMap<>();
    private static final class Balance {
        long expected;
        int visible, recentlyCredited;
        long settleAt = Long.MAX_VALUE, creditedAt = Long.MIN_VALUE;
        Balance(int owned) { expected = visible = owned; }
    }
    public void reset() { balances.clear(); }
    public void observe(HuntingShards.Shard shard, int owned, boolean eligible, long now) {
        if (owned < 0) return;
        Balance b = balances.get(shard);
        if (b == null || !eligible) {
            balances.put(shard, new Balance(owned));
        } else if (b.visible != owned) {
            b.visible = owned;
            b.settleAt = now + SETTLE_MS;
        }
    }
    /** Returns the earned quantity still owed to the loot ledger. A deposit only adjusts the baseline. */
    public int receipt(HuntingShards.Receipt receipt, boolean earning, long now) {
        int earned = earning && !receipt.deposit() ? receipt.count() : 0;
        Balance b = balances.get(receipt.shard());
        if (b != null) {
            int overlap = 0;
            if (earned > 0 && b.recentlyCredited > 0 && now - b.creditedAt <= SETTLE_MS) {
                overlap = Math.min(earned, b.recentlyCredited);
                b.recentlyCredited -= overlap;
            }
            b.expected += receipt.count() - overlap;
            earned -= overlap;
        }
        return earned;
    }
    public void settle(long now, boolean finish, ObjIntConsumer<HuntingShards.Shard> credit) {
        for (var entry : balances.entrySet()) {
            Balance b = entry.getValue();
            if (b.settleAt == Long.MAX_VALUE || !finish && now < b.settleAt) continue;
            b.settleAt = Long.MAX_VALUE;
            if (b.visible > b.expected) {
                int gain = (int) (b.visible - b.expected);
                b.expected = b.visible;
                b.recentlyCredited = gain;
                b.creditedAt = now;
                credit.accept(entry.getKey(), gain);
            }
        }
    }
}
