package dev.fragruns.ui;

import dev.fragruns.core.Giant;
import dev.fragruns.core.LootLedger;
import java.util.ArrayList;
import java.util.List;

/** Each displayed subtotal is drawn from the same collected-loot valuation as the session total. */
record FragmentProfitRow(Giant giant, long quantity, double coins) {
    String label() { return (giant == null ? "Giant fragments" : giant.fragmentName) + " ×" + quantity; }
    int color() { return giant == null ? Theme.BLUE : giant.color; }

    static List<FragmentProfitRow> rows(LootLedger loot, LootLedger.Valuation value, boolean combined) {
        long quantity = 0;
        double coins = 0;
        List<FragmentProfitRow> rows = new ArrayList<>(4);
        for (Giant giant : Giant.values()) {
            long count = loot.itemCount(giant.itemId);
            double subtotal = value.fragmentValues().get(giant);
            if (combined) {
                quantity += count;
                coins += subtotal;
            } else rows.add(new FragmentProfitRow(giant, count, subtotal));
        }
        if (combined) rows.add(new FragmentProfitRow(null, quantity, coins));
        return rows;
    }
}
