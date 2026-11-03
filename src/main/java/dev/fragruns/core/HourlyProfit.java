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

        double otherHourly = session.profitPerHour(collected.otherItems() + collected.essence() + collected.coins());
        long averageRunMs = session.average();
        int finished = session.completedRuns();
        if (averageRunMs <= 0 || finished == 0) return otherHourly;

        // Price one of each fragment through the same tax/NPC/missing-price rules as actual loot.
        var basket = new LootLedger();
        for (Giant giant : Giant.values())
            basket.addItem(giant.itemId, giant.fragmentName, 1, null, 0, npcPrices.applyAsDouble(giant.itemId));
        double expectedPerRun = basket.value(prices, config.bazaarTaxPercent, false).items()
                * GIANT_SPAWN_CHANCE * FRAGMENT_DROP_CHANCE;

        double attemptsPerHour = 3_600_000.0 / averageRunMs;
        double finishedShare = (double) finished / session.totalRuns();
        return otherHourly + expectedPerRun * finishedShare * attemptsPerHour;
    }
}
