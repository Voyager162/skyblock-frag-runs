package dev.fragruns.core;

import java.util.*;

/** Two complete, scoped material-stash readings bracket the runs. Alerts and giant sightings are not inputs. */
public final class HubStashAudit {
    private String scope="",session="";
    private Map<Giant,Integer> before;
    private UUID intervalId=UUID.randomUUID();
    private final Map<FragmentStash.Target,Boolean> runs=new LinkedHashMap<>();
    private final Map<FragmentStash.Target,EnumMap<Giant,Integer>> booked=new HashMap<>();
    private final EnumMap<Giant,Integer> withdrawn=new EnumMap<>(Giant.class);
    public void reset() { scope="";session="";before=null;clearInterval(); }
    private void clearInterval() { runs.clear();booked.clear();withdrawn.clear();intervalId=UUID.randomUUID(); }
    public UUID intervalId() { return intervalId; }
    public void scope(String next) {
        if (!scope.equals(next)) { reset();scope=next; }
    }
    public boolean needed(String id,boolean running) {
        return !runs.isEmpty() || running && (before==null || !session.equals(id));
    }
    public boolean pending() { return !runs.isEmpty(); }
    public boolean hasBaseline() { return before!=null; }
    public void run(String sessionId,UUID run,boolean normalExit) {
        if (run==null || scope.isEmpty()) return;
        if (runs.isEmpty() && !session.equals(sessionId)) before=null;
        runs.merge(new FragmentStash.Target(sessionId,run),normalExit,(a,b)->a||b);
    }
    public void booked(String sessionId,UUID run,Giant giant,int quantity) {
        var target=new FragmentStash.Target(sessionId,run);
        if (quantity>0 && runs.containsKey(target))
            booked.computeIfAbsent(target,k->new EnumMap<>(Giant.class)).merge(giant,quantity,Math::max);
    }
    public void withdrawn(Map<Giant,Integer> amounts) {
        if (before!=null) amounts.forEach((g,n)->{if(n>0) withdrawn.merge(g,n,Math::addExact);});
    }
    public Map<FragmentStash.Target,Boolean> targets() { return Map.copyOf(runs); }
    public Map<Giant,Integer> gains(Map<Giant,Integer> after) {
        var gains=new EnumMap<Giant,Integer>(Giant.class);
        if (before==null || runs.isEmpty()) return gains;
        for (Giant g:Giant.values()) {
            long gain=(long)after.getOrDefault(g,0)+withdrawn.getOrDefault(g,0)-before.getOrDefault(g,0);
            for (var delivered:booked.values()) gain-=delivered.getOrDefault(g,0);
            if (gain>0) gains.put(g,Math.toIntExact(gain));
        }
        return gains;
    }
    public void accept(String sessionId,Map<Giant,Integer> complete) {
        if (complete.values().stream().anyMatch(n->n==null || n<0)) throw new IllegalArgumentException("Invalid stash count");
        before=Map.copyOf(complete); session=sessionId;clearInterval();
    }
    public void dirtyOutsideRun() { before=null; }
}
