package dev.fragruns.core;

import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Changes the hourly projection only; never adds expected drops to the collected-loot ledger. */
public final class HourlyProfit {
    // Documented F7 Watcher odds, independent of this session's giant/drop distribution.
    // Sources and assumptions: README.md, "Hourly profit modes".
    public static final double GIANT_SPAWN_CHANCE = 0.25;
    public static final double FRAGMENT_DROP_CHANCE = 0.50;

    private HourlyProfit() {}

    public static double calculate(Session session, LootLedger.Valuation collected,
                                   Map<String, Double> prices, ToDoubleFunction<String> npcPrices,
                                   ModConfig config) {
        if (!config.expectedFragmentProfit) return session.profitPerHour(collected.total());

        // Price one of each fragment through the same tax/NPC/missing-price rules as actual loot.
        var basket = new LootLedger();
        for (Giant giant : Giant.values())
            basket.addItem(giant.itemId, giant.fragmentName, 1, null, 0, npcPrices.applyAsDouble(giant.itemId));
        double expectedPerRun = basket.value(prices, config.bazaarTaxPercent, false).items()
                * GIANT_SPAWN_CHANCE * FRAGMENT_DROP_CHANCE;

        double otherProfit = collected.otherItems() + collected.essence() + collected.coins();
        // Early exits and the live run cannot earn an expected fragment. Session time includes downtime.
        return session.profitPerHour(otherProfit + session.completedRuns() * expectedPerRun);
    }
}
