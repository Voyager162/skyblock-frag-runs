package dev.fragruns.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Consumer;
import java.util.UUID;

/** All mutations occur on the client thread. Time is monotonic, not tied to ticks or TPS. */
public final class Session {
    public record Details(UUID id, long startedAt, long endedAt, boolean giantDefeated, LootLedger.Snapshot loot) {}
    public record Run(long milliseconds, Giant giant, boolean completed, int fragments, String endReason, Details details) {
        public Run(long milliseconds, Giant giant, boolean completed, int fragments, String endReason) {
            this(milliseconds,giant,completed,fragments,endReason,null);
        }
    }
    public record GiantStats(int encounters, int fragments, int runsWithDrop, double frequency, double dropRate) {}
    public record Snapshot(int schema, String id, long elapsedMs, List<Run> runs, LootLedger.Snapshot loot, boolean paused, FragmentStash.Data stash) {
        public Snapshot(int schema,String id,long elapsedMs,List<Run> runs,LootLedger.Snapshot loot,boolean paused) {
            this(schema,id,elapsedMs,runs,loot,paused,null);
        }
        public Snapshot(int schema, String id, long elapsedMs, List<Run> runs, LootLedger.Snapshot loot) {
            this(schema, id, elapsedMs, runs, loot, false);
        }
    }
    public enum KeyResult { STARTED, PAUSED, RESUMED, STOPPED }
    private enum State { STOPPED, RUNNING, PAUSED }
    private static final long DOUBLE_PRESS_MS = 1_500;

    private final LongSupplier clock;
    private final List<Run> runs = new ArrayList<>();
    private final LootLedger loot = new LootLedger();
    private final FragmentStash stash=new FragmentStash();
    public FragmentStash stash() { return stash; }
    private String id = "";
    private State state = State.STOPPED;
    private long startedAt, elapsedBase;
    private long confirmUntil = Long.MIN_VALUE;
    private ActiveRun current;
    private UUID lastFinished;

    private static final class ActiveRun {
        final long enteredAt;
        final UUID id = UUID.randomUUID();
        final long wallStart;
        final LootLedger before = new LootLedger();
        Giant giant;
        boolean inferred;
        boolean killed;
        int fragments;
        ActiveRun(long enteredAt,long wallStart,LootLedger loot) {
            this.enteredAt=enteredAt; this.wallStart=wallStart; before.restore(loot.snapshot());
        }
    }

    public Session(LongSupplier clock) { this.clock = clock; }
    public static Session live() { return new Session(() -> System.nanoTime() / 1_000_000); }

    public KeyResult pressSessionKey() {
        long now = clock.getAsLong();
        if (!active()) {
            start();
            return KeyResult.STARTED;
        }
        // The input latch rejects repeats; genuinely separate presses may be arbitrarily fast.
        if (confirmUntil >= now) {
            stop();
            return KeyResult.STOPPED;
        }
        confirmUntil = now + DOUBLE_PRESS_MS;
        if (running()) {
            elapsedBase = elapsedMs();
            state = State.PAUSED;
            return KeyResult.PAUSED;
        }
        startedAt = now;
        state = State.RUNNING;
        return KeyResult.RESUMED;
    }

    public void start() {
        if (active()) return;
        runs.clear();
        loot.clear();
        stash.clear();
        elapsedBase = 0;
        startedAt = clock.getAsLong();
        id = java.time.Instant.now().toString().replace(':', '-');
        current = null;
        lastFinished = null;
        confirmUntil = Long.MIN_VALUE;
        state = State.RUNNING;
    }

    public void stop() {
        if (!active()) return;
        // A manually stopped run is partial, even if its giant was defeated.
        leaveDungeon("Session stopped", true);
        elapsedBase = elapsedMs();
        state = State.STOPPED;
        confirmUntil = Long.MIN_VALUE;
    }

    public void enterDungeon() { enterDungeon(0); }
    public void enterDungeon(long confirmedAge) {
        if (!running() || current!=null) return;
        long backdate=Math.clamp(confirmedAge,0,Math.max(0,clock.getAsLong()-startedAt));
        current=new ActiveRun(elapsedMs()-backdate,System.currentTimeMillis()-backdate,loot);
    }

    public void observeGiant(Giant giant) {
        if (running() && current!=null && giant!=null && (current.giant==null || current.inferred && !current.killed)) {
            current.giant=giant; current.inferred=false;
        }
    }
    public void observeOutfit(Giant giant) {
        if (running() && current!=null && !current.killed && giant!=null && (current.giant==null || current.inferred)) {
            current.giant=giant; current.inferred=true;
        }
    }

    public void giantDefeated(Giant giant) {
        if (!running() || current == null || giant == null) return;
        if (current.giant!=giant) observeGiant(giant);
        if (current.giant == giant) current.killed = true;
    }

    public void watcherCleared() {
        if (running() && current != null && current.giant != null) current.killed = true;
    }

    public void collectFragment(Giant giant, int count) {
        if (!running() || current == null || giant==null || count <= 0) return;
        // A collected fragment is direct evidence. Later equipment and labels on other entities are not.
        if (current.inferred && current.fragments==0) { current.giant=giant; current.inferred=false; }
        observeGiant(giant);
        giantDefeated(giant);
        if (current.giant == giant) current.fragments += count;
    }

    public void leaveDungeon(String reason, boolean interrupted) {
        if (current == null) return;
        runs.add(recordRun(reason,!interrupted && current.killed,System.currentTimeMillis()));
        lastFinished = current.id;
        current = null;
    }
    private Run recordRun(String reason, boolean completed, long endedAt) {
        return new Run(runMs(),current.giant,completed,current.fragments,reason,
                new Details(current.id,current.wallStart,endedAt,current.killed,loot.since(current.before)));
    }
    public UUID currentRunId() { return current == null ? null : current.id; }
    Run stashRun(UUID owner) {
        if (owner.equals(currentRunId())) return recordRun("In progress",false,0);
        for (int i=runs.size()-1;i>=0;i--) {
            var r=runs.get(i); if (r.details!=null && owner.equals(r.details.id)) return r;
        }
        return null;
    }
    /** Resolve an earlier earned reward, including after stopping or while playing another run. */
    void creditStash(UUID owner,Giant giant,int count,double npcPrice) {
        Consumer<LootLedger> add=l->l.addItem(giant.itemId,giant.fragmentName,count,null,0,npcPrice);
        if (owner!=null && owner.equals(currentRunId())) { current.fragments+=count; add.accept(loot); return; }
        if (owner!=null && !assignArchivedFragment(owner,giant,count,npcPrice)) return;
        add.accept(loot); if (current!=null) add.accept(current.before);
    }
    boolean assignArchivedFragment(UUID owner,Giant giant,int count,double npcPrice) {
        for (int i=runs.size()-1;i>=0;i--) {
            Run r=runs.get(i);var d=r.details;
            if (d==null || !owner.equals(d.id)) continue;
            var rewards=new LootLedger();rewards.restore(d.loot);
            rewards.addItem(giant.itemId,giant.fragmentName,count,null,0,npcPrice);
            runs.set(i,new Run(r.milliseconds,r.giant,r.completed,r.fragments+count,r.endReason,
                    new Details(d.id,d.startedAt,d.endedAt,d.giantDefeated,rewards.snapshot())));
            return true;
        }
        return false;
    }
    /** An itemized balance change in a single observed F7 can identify a previously unnamed giant. */
    void verifiedStash(UUID owner,Giant giant,int count,boolean normalExit,double price) {
        if (owner.equals(currentRunId())) {
            if (current.fragments==0) {current.giant=giant;current.inferred=false;current.killed=true;}
        } else for (int i=runs.size()-1;i>=0;i--) {
            var r=runs.get(i);var d=r.details;
            if (d==null || !d.id.equals(owner)) continue;
            if (r.fragments==0) runs.set(i,new Run(r.milliseconds,giant,r.completed||normalExit,0,r.endReason,
                    new Details(d.id,d.startedAt,d.endedAt,true,d.loot)));
            break;
        }
        creditStash(owner,giant,count,price);
    }
    public UUID rewardOwner() { return current == null ? lastFinished : current.id; }
    /** A reward seen on return to the hub can settle after the next run has already begun. */
    public void credit(UUID owner, Consumer<LootLedger> add) {
        if (!running() || owner == null) return;
        if (owner.equals(currentRunId())) { add.accept(loot); return; }
        for (int i = runs.size()-1; i >= 0; i--) {
            Run run = runs.get(i);
            Details d = run.details;
            if (d == null || !owner.equals(d.id)) continue;
            var settled = new LootLedger();
            settled.restore(d.loot);
            add.accept(settled);
            add.accept(loot);
            if (current != null) add.accept(current.before);
            runs.set(i,new Run(run.milliseconds,run.giant,run.completed,run.fragments,run.endReason,
                    new Details(owner,d.startedAt,d.endedAt,d.giantDefeated,settled.snapshot())));
            return;
        }
    }
    /** Continue this identity and recorded elapsed time without arming the double-press gesture. */
    public void resumeSaved() {
        if (running()) return;
        if (id.isBlank()) throw new IllegalStateException("No saved session to continue");
        startedAt = clock.getAsLong();
        state = State.RUNNING;
        confirmUntil = Long.MIN_VALUE;
    }

    public long elapsedMs() { return Math.max(0, elapsedBase + (running() ? clock.getAsLong() - startedAt : 0)); }
    public long runMs() { return current == null ? 0 : Math.max(0, elapsedMs() - current.enteredAt); }
    public boolean running() { return state == State.RUNNING; }
    public boolean paused() { return state == State.PAUSED; }
    public boolean active() { return state != State.STOPPED; }
    public boolean inDungeon() { return current != null; }
    public Giant currentGiant() { return current == null ? null : current.giant; }
    public int currentFragments() { return current==null?0:current.fragments; }
    public boolean inferredGiant() { return current!=null && current.inferred; }
    public boolean giantKilled() { return current != null && current.killed; }
    public boolean confirmingStop() { return active() && confirmUntil >= clock.getAsLong(); }
    public long confirmRemainingMs() { return confirmingStop() ? Math.max(0, confirmUntil - clock.getAsLong()) : 0; }
    public void cancelConfirmation() { confirmUntil = Long.MIN_VALUE; }
    public void clearSaved() {
        if (active()) throw new IllegalStateException("A session is active");
        id = "";
        lastFinished = null;
        elapsedBase = 0;
        runs.clear();
        loot.clear();
        stash.clear();
    }
    public String id() { return id; }
    public Run latestRun() { return runs.isEmpty()?null:runs.getLast(); }
    public List<Run> runs() { return List.copyOf(runs); }
    public LootLedger loot() { return loot; }
    public int totalRuns() { return runs.size(); }
    public int completedRuns() { return (int) runs.stream().filter(Run::completed).count(); }
    public long fastest() { return runs.stream().mapToLong(Run::milliseconds).min().orElse(0); }
    public long slowest() { return runs.stream().mapToLong(Run::milliseconds).max().orElse(0); }
    public long last() { return runs.isEmpty() ? 0 : runs.getLast().milliseconds(); }
    public long average() { return (long) runs.stream().mapToLong(Run::milliseconds).average().orElse(0); }
    public double profitPerHour(double profit) { return elapsedMs() == 0 ? 0 : profit * 3_600_000.0 / elapsedMs(); }

    public Map<Giant, GiantStats> giantStats() {
        Map<Giant, GiantStats> result = new EnumMap<>(Giant.class);
        int eligible = completedRuns();
        for (Giant giant : Giant.values()) {
            List<Run> matching = runs.stream().filter(r -> r.completed && r.giant == giant).toList();
            int drops = matching.stream().mapToInt(Run::fragments).sum();
            int withDrop = (int) matching.stream().filter(r -> r.fragments > 0).count();
            int shared=stash.sharedCount(giant,matching);
            drops+=shared; withDrop+=shared;
            result.put(giant, new GiantStats(matching.size(), drops, withDrop,
                    eligible == 0 ? 0 : matching.size() * 100.0 / eligible,
                    matching.isEmpty() ? 0 : withDrop * 100.0 / matching.size()));
        }
        return result;
    }

    public Snapshot snapshot() {
        return snapshot("Interrupted at save checkpoint",0,paused());
    }
    public Snapshot switchingAway() {
        return snapshot("Switched session",System.currentTimeMillis(),false);
    }
    private Snapshot snapshot(String reason,long endedAt,boolean paused) {
        List<Run> saved=new ArrayList<>(runs);
        if(current!=null) saved.add(recordRun(reason,false,endedAt));
        return new Snapshot(1,id,elapsedMs(),List.copyOf(saved),loot.snapshot(),paused,stash.snapshot());
    }

    /** Explicit pauses remain resumable. An interrupted live checkpoint recovers as stopped. */
    public void restore(Snapshot s) {
        if (s == null || s.schema != 1 || s.elapsedMs < 0 || s.runs == null || s.loot == null)
            throw new IllegalArgumentException("Invalid session save");
        var seen = new java.util.HashSet<UUID>();
        for (Run r : s.runs) {
            if (r == null || r.milliseconds < 0 || r.fragments < 0 || (r.completed && r.giant == null))
                throw new IllegalArgumentException("Invalid saved run");
            if (r.details != null) {
                if (r.details.id == null || !seen.add(r.details.id) || r.details.startedAt < 0 || r.details.endedAt < 0)
                    throw new IllegalArgumentException("Invalid run detail");
                new LootLedger().restore(r.details.loot);
            }
        }
        FragmentStash.validate(s.stash,seen);
        loot.restore(s.loot);
        stash.restore(s.stash);
        id = s.id == null ? "" : s.id;
        elapsedBase = s.elapsedMs;
        runs.clear();
        runs.addAll(s.runs);
        state = s.paused && !id.isBlank() ? State.PAUSED : State.STOPPED;
        current = null;
        lastFinished = null;
        confirmUntil = Long.MIN_VALUE;
    }
}
