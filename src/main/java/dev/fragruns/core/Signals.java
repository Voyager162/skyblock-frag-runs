package dev.fragruns.core;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kept independent of Minecraft so real protocol text can be replayed in tests. */
public final class Signals {
    private static final Pattern FORMAT = Pattern.compile("§[0-9A-FK-ORa-fk-orx]");
    private static final Pattern FLOOR = Pattern.compile("(?:The )?Catacombs\\s*\\(F7\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+(?:\\.\\d+)?)\\s*([kmb]?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOUND_ESSENCE = Pattern.compile("^(?:You|[A-Za-z0-9_]+) found (?:a |an |([\\d,]+)x? )?(Wither|Undead|Dragon|Diamond|Gold|Ice|Spider|Crimson) Essence[!.].*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESSENCE_REWARD = Pattern.compile("^\\+?([\\d,]+)x? (Wither|Undead|Dragon|Diamond|Gold|Ice|Spider|Crimson) Essence!?$", Pattern.CASE_INSENSITIVE);
    public record Essence(String id, int count) {}
    private Signals() {}
    public static String clean(String s) { return FORMAT.matcher(s == null ? "" : s).replaceAll("").replace('\u00a0',' ').trim(); }
    public static boolean isF7(String title, List<String> lines) {
        return clean(title).toUpperCase(Locale.ROOT).contains("SKYBLOCK") && lines.stream().map(Signals::clean).anyMatch(s -> FLOOR.matcher(s).find());
    }
    public static OptionalDouble purse(List<String> lines) {
        for (String line : lines) {
            Matcher m = PURSE.matcher(clean(line));
            if (m.find()) {
                // Abbreviated purses cannot support precise deltas; don't invent coin gains from rounding.
                if (!m.group(2).isEmpty()) return OptionalDouble.empty();
                try { return OptionalDouble.of(Double.parseDouble(m.group(1).replace(",", ""))); }
                catch (NumberFormatException ignored) { return OptionalDouble.empty(); }
            }
        }
        return OptionalDouble.empty();
    }
    public static boolean externalMoney(String raw) {
        String s = clean(raw).toLowerCase(Locale.ROOT);
        // No broad "coins" filter: combat awards also contain that word.
        return s.startsWith("[bazaar]") || s.startsWith("[auction]") || s.startsWith("allowance!")
                || s.startsWith("you collected") && s.contains("coins")
                || s.startsWith("you have deposited") || s.startsWith("you have withdrawn")
                || s.contains("trade completed") || s.contains("coins from the jerry box")
                || s.startsWith("you claimed") && s.contains("coins")
                || s.startsWith("bank interest") || s.startsWith("you purchased")
                || s.startsWith("you sold") || s.startsWith("you bought");
    }
    public static boolean watcherCleared(String raw) {
        return clean(raw).matches("\\[BOSS] The Watcher: (?:You have proven yourself\\. You may pass\\.|That will be enough for now\\.)");
    }
    public static Essence essence(String raw, boolean inSalvageMessage) {
        if (inSalvageMessage) return null; // Already represented by the original equipment's salvage estimate.
        String s = clean(raw);
        Matcher found = FOUND_ESSENCE.matcher(s);
        if (found.matches()) return new Essence("ESSENCE_" + found.group(2).toUpperCase(Locale.ROOT),
                found.group(1) == null ? 1 : Integer.parseInt(found.group(1).replace(",", "")));
        Matcher reward = ESSENCE_REWARD.matcher(s);
        if (reward.matches()) return new Essence("ESSENCE_" + reward.group(2).toUpperCase(Locale.ROOT),
                Integer.parseInt(reward.group(1).replace(",", "")));
        return null;
    }
}
