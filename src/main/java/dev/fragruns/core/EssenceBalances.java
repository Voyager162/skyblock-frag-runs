package dev.fragruns.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

/** Reconciles silent mob essence from Dungeon Hub balances, excluding already-valued salvage and chat drops. */
public final class EssenceBalances {
    private static final Pattern LINE=Pattern.compile("^(Wither|Undead|Dragon|Diamond|Gold|Ice|Spider|Crimson)(?: Essence)?:\\s*([\\d,]+)$",Pattern.CASE_INSENSITIVE);
    private Map<String,Long> latest=Map.of(),before=Map.of();
    private final Map<String,Long> counted=new HashMap<>(),salvaged=new HashMap<>();
    private boolean pending;
    public static Map<String,Long> parse(Iterable<String> lines) {
        Map<String,Long> result=new HashMap<>();
        for(String line:lines) {
            var match=LINE.matcher(Signals.clean(line));
            if(match.matches()) try { result.put("ESSENCE_"+match.group(1).toUpperCase(java.util.Locale.ROOT),Long.parseLong(match.group(2).replace(",",""))); }
            catch(NumberFormatException ignored) {}
        }
        return result;
    }
    public void enter() { before=latest; counted.clear();salvaged.clear();pending=false; }
    public void found(String id,long quantity) { counted.merge(id,quantity,Long::sum); }
    public void salvaged(String id,long quantity) { salvaged.merge(id,quantity,Long::sum); }
    public void leave() { pending=!before.isEmpty(); }
    public void invalidate() { pending=false;before=Map.of();latest=Map.of(); }
    public boolean hasBaseline() { return !latest.isEmpty(); }
    public void observeHub(Map<String,Long> balances,BiConsumer<String,Long> add) {
        if(balances.isEmpty()) return;
        if(pending) {
            // Wait for every known essence line; partially updated tab lists are not a reliable snapshot.
            if(!balances.keySet().containsAll(before.keySet())) return;
            for(var entry:before.entrySet()) {
                long net=balances.get(entry.getKey())-entry.getValue()-counted.getOrDefault(entry.getKey(),0L)-salvaged.getOrDefault(entry.getKey(),0L);
                if(net>0) add.accept(entry.getKey(),net);
            }
            pending=false;
        }
        latest=Map.copyOf(balances);
    }
}
