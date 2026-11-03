package dev.fragruns.core;

import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kept independent of Minecraft so real protocol text can be replayed in tests. */
public final class Signals {
    private static final Pattern FORMAT = Pattern.compile("§[0-9A-FK-ORa-fk-orx]");
    public enum Area { F7, OTHER_FLOOR, OUTSIDE, DUNGEON, UNKNOWN }
    private static final Pattern FLOOR = Pattern.compile("^(?:(?:Area|Dungeon):\\s*)?(?:[⏣ф]\\s*)?(?:(Master(?: Mode)?)\\s+)?(?:The )?Catacombs\\s*(?:\\(\\s*([FME])\\s*(\\d*)\\s*\\)|,?\\s+Floor\\s+([IVX]+|\\d+))$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENTRY = Pattern.compile("^(?:-+\\s+)?(?:\\[[\\w+ -]+]\\s+)*\\w{1,16} entered (?:the )?(?:(Master(?: Mode)?) )?(?:The )?Catacombs, (?:Floor ([IVX]+|\\d+)|(Entrance))!?(?:\\s+-+)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_DECORATION = Pattern.compile("[\\p{Cf}\\p{Co}]");
    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+(?:\\.\\d+)?)\\s*([kmb]?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOUND_ESSENCE = Pattern.compile("^(?:You|[A-Za-z0-9_]+) found (?:a |an |([\\d,]+)x? )?(Wither|Undead|Dragon|Diamond|Gold|Ice|Spider|Crimson) Essence[!.].*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESSENCE_REWARD = Pattern.compile("^\\+?([\\d,]+)x? (Wither|Undead|Dragon|Diamond|Gold|Ice|Spider|Crimson) Essence!?$", Pattern.CASE_INSENSITIVE);
    public record Essence(String id, int count) {}
    private Signals() {}
    public static String clean(String s) { return FORMAT.matcher(s == null ? "" : s).replaceAll("").replace('\u00a0',' ').trim(); }
    /** Classify only complete server location rows. Missing and partially rebuilt sidebars are common. */
    public static Area area(List<String> lines) {
        Area result = Area.UNKNOWN;
        for (String raw : lines) {
            String line = SCORE_DECORATION.matcher(clean(raw)).replaceAll("").strip();
            Matcher floor = FLOOR.matcher(line);
            if (floor.matches()) {
                boolean seven = floor.group(2) == null ? isSeven(floor.group(4))
                        : "F".equalsIgnoreCase(floor.group(2)) && "7".equals(floor.group(3));
                return floor.group(1) == null && seven ? Area.F7 : Area.OTHER_FLOOR;
            }
            if (line.startsWith("⏣") || line.startsWith("ф") || line.startsWith("Area:") || line.equals("Dungeon Hub")) {
                String name = line.replaceFirst("^(?:⏣|ф|Area:)\\s*", "");
                if (name.equalsIgnoreCase("The Catacombs") || name.equalsIgnoreCase("Catacombs")
                        || name.equalsIgnoreCase("Dungeon")) result = Area.DUNGEON;
                else if (name.equals("Dungeon Hub") || name.equals("Hub") || name.equals("Village")
                        || name.equals("Private Island") || name.equals("Your Island")) result = Area.OUTSIDE;
            }
        }
        return result;
    }
    /** A server banner may have separator lines. Player chat mentioning a floor is not an entry. */
    public static Area announcedFloor(String raw) {
        Matcher entry = ENTRY.matcher(clean(raw).replace("\\n", "\n"));
        if (!entry.matches()) return Area.UNKNOWN;
        return entry.group(1) == null && isSeven(entry.group(2)) ? Area.F7 : Area.OTHER_FLOOR;
    }
    private static boolean isSeven(String token) { return "VII".equalsIgnoreCase(token) || "7".equals(token); }
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
