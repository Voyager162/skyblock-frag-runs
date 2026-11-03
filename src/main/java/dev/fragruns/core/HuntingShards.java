package dev.fragruns.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Hunting Box labels and physical attribute IDs can differ from their Bazaar product IDs. */
public final class HuntingShards {
    public record Shard(String name, String productId) {}
    public record Receipt(Shard shard, int count, boolean deposit) {}
    private static final Map<String,Shard> BY_NAME = new HashMap<>(), BY_ID = new HashMap<>();
    private static final Pattern BOX = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Hunting Box");
    private static final Pattern OWNED = Pattern.compile("Owned: ([\\d,]+) Shards?");
    private static final String DROP = "(?:(?:an?|the|x?(?<count>[\\d,]+)x?) )?(?<name>.+?) Shards?(?: x(?<after>[\\d,]+))?";
    private static final Pattern DEPOSIT = Pattern.compile("You sent " + DROP + " to your Hunting Box\\.");
    private static final List<Pattern> GAINS = List.of(
            Pattern.compile("(?:\\p{Co} (?:GOOD|GREAT|OUTSTANDING) CATCH! )?You caught " + DROP + "!"),
            Pattern.compile("CHARM! You charmed .+? and received " + DROP + "!"),
            Pattern.compile("CAPTURE! You (?:caught .+? and gained|found .+?, and as a reward (?:he|she|it|they) gave you) " + DROP + "!"),
            Pattern.compile("LOOT SHARE!? You received " + DROP + " (?:for assisting|from) .+!"),
            Pattern.compile("FLOOR DROP! You found " + DROP + " on the ground!"),
            Pattern.compile("SHARD! Your contribution earned you " + DROP + "!"));
    static {
        var stream = Objects.requireNonNull(HuntingShards.class.getResourceAsStream("/data/fragruns/shards.tsv"));
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split("\t");
                var shard = new Shard(fields[0], fields[1]);
                BY_NAME.put(shard.name(), shard);
                BY_ID.put(shard.productId(), shard);
                BY_ID.put(fields[2], shard);
            }
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private HuntingShards() {}
    public static Shard byId(String id) { return BY_ID.get(id); }
    public static Shard byLabel(String text) {
        String name = Signals.clean(text);
        int suffix = name.indexOf(" Shard");
        return BY_NAME.get(suffix < 0 ? name : name.substring(0, suffix));
    }
    public static boolean isBox(String title) { return BOX.matcher(Signals.clean(title)).matches(); }
    public static int owned(String line) {
        var match = OWNED.matcher(Signals.clean(line));
        return match.matches() ? number(match.group(1)) : -1;
    }
    private static int number(String value) {
        try { return Integer.parseInt(value.replace(",", "")); }
        catch (NumberFormatException e) { return -1; }
    }
    public static Receipt receipt(String message) {
        String line = Signals.clean(message);
        if (!line.contains("Shard")) return null;
        boolean deposit = line.startsWith("You sent ");
        for (Pattern pattern : deposit ? List.of(DEPOSIT) : GAINS) {
            var match = pattern.matcher(line);
            if (!match.matches()) continue;
            Shard shard = BY_NAME.get(match.group("name"));
            String amount = match.group("count") == null ? match.group("after") : match.group("count");
            int count = amount == null ? 1 : number(amount);
            if (shard != null && count > 0) return new Receipt(shard, count, deposit);
        }
        return null;
    }
}
