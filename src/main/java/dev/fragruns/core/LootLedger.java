package dev.fragruns.core;

import java.util.LinkedHashMap;
import java.util.EnumMap;
import java.util.Map;

/** Harvested quantities remain after sales. Salvage is valued once, at pickup. */
public final class LootLedger {
    public record Entry(String itemId, String name, long quantity, String essenceId, int essencePerItem, double npcPrice) {}
    public record Snapshot(Map<String, Entry> entries, Map<String, Long> essence, double coins) {}
    public record Valuation(double items, double directEssence, double salvage, double coins, int unpriced,
                            int npcFallbacks, Map<String, Double> lineValues,
                            Map<Giant, Double> fragmentValues, double otherItems) {
        public double total() { return items + directEssence + salvage + coins; }
        public double essence() { return directEssence + salvage; }
    }
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Long> essence = new LinkedHashMap<>();
    private double coins;

    public void addItem(String id, String name, int count, String essenceId, int salvageAmount, double npcPrice) {
        if (id == null || id.isBlank() || count <= 0 || salvageAmount < 0 || !Double.isFinite(npcPrice) || npcPrice < 0) return;
        // Quality affects the salvage yield, so separate those lots without losing the Bazaar product ID.
        String key = id + "|" + (essenceId == null ? "" : essenceId) + "|" + salvageAmount;
        Entry old = entries.get(key);
        entries.put(key, new Entry(id, name == null ? id : name, (old == null ? 0 : old.quantity) + count,
                essenceId, salvageAmount, npcPrice));
    }

    public void addEssence(String type, long amount) {
        if (amount > 0 && type != null && type.matches("ESSENCE_[A-Z]+")) essence.merge(type, amount, Long::sum);
    }

    public void addCoins(double amount) { if (Double.isFinite(amount)) coins += amount; }
    public double coins() { return coins; }
    public Map<String, Entry> entries() { return Map.copyOf(entries); }
    public Map<String, Long> essence() { return Map.copyOf(essence); }
    public long itemCount(String id) { return entries.values().stream().filter(e -> e.itemId.equals(id)).mapToLong(Entry::quantity).sum(); }

    public Valuation value(Map<String, Double> prices, double taxPercent, boolean includeSalvage) {
        double multiplier = 1 - Math.max(0, Math.min(10, taxPercent)) / 100;
        double items = 0, salvage = 0, direct = 0, otherItems = 0;
        Map<Giant, Double> fragments = new EnumMap<>(Giant.class);
        for (Giant giant : Giant.values()) fragments.put(giant, 0.0);
        int unknown = 0, npc = 0;
        Map<String, Double> lines = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> lot : entries.entrySet()) {
            Entry e = lot.getValue();
            double amount;
            if (includeSalvage && e.essencePerItem > 0 && e.essenceId != null) {
                Double price = validPrice(prices.get(e.essenceId));
                amount = price == null ? 0 : e.quantity * e.essencePerItem * price * multiplier;
                if (price == null) unknown++;
                salvage += amount;
            } else {
                Double price = validPrice(prices.get(e.itemId));
                if (price != null) amount = e.quantity * price * multiplier;
                else if (e.npcPrice > 0) { amount = e.quantity * e.npcPrice; npc++; }
                else { amount = 0; unknown++; }
                items += amount;
                Giant giant = Giant.fromItem(e.itemId);
                if (giant == null) otherItems += amount;
                else fragments.merge(giant, amount, Double::sum);
            }
            lines.put(lot.getKey(), amount);
        }
        for (var e : essence.entrySet()) {
            Double price = validPrice(prices.get(e.getKey()));
            double amount = price == null ? 0 : e.getValue() * price * multiplier;
            if (price == null) unknown++;
            direct += amount;
            lines.put(e.getKey(), amount);
        }
        return new Valuation(items, direct, salvage, coins, unknown, npc, Map.copyOf(lines), Map.copyOf(fragments), otherItems);
    }

    private static Double validPrice(Double price) { return price != null && Double.isFinite(price) && price > 0 ? price : null; }
    public void clear() { entries.clear(); essence.clear(); coins = 0; }
    public Snapshot snapshot() { return new Snapshot(new LinkedHashMap<>(entries), new LinkedHashMap<>(essence), coins); }
    public void restore(Snapshot s) {
        if (s == null || s.entries == null || s.essence == null || !Double.isFinite(s.coins))
            throw new IllegalArgumentException("Invalid loot save");
        for (Entry e : s.entries.values()) if (e == null || e.itemId == null || e.quantity < 0 || e.essencePerItem < 0 || !Double.isFinite(e.npcPrice))
            throw new IllegalArgumentException("Invalid loot entry");
        for (var e : s.essence.entrySet()) if (e.getValue() == null || e.getValue() < 0 || !e.getKey().matches("ESSENCE_[A-Z]+"))
            throw new IllegalArgumentException("Invalid essence entry");
        clear(); entries.putAll(s.entries); essence.putAll(s.essence); coins = s.coins;
    }
}
