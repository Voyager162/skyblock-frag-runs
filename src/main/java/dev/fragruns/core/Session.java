package dev.fragruns.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** All mutations occur on the client thread. Time is monotonic, not tied to ticks or TPS. */
public final class Session {
    public record Run(long milliseconds, Giant giant, boolean completed, int fragments, String endReason) {}
    public record GiantStats(int encounters, int fragments, int runsWithDrop, double frequency, double dropRate) {}
    public record Snapshot(int schema, String id, long elapsedMs, List<Run> runs, LootLedger.Snapshot loot) {}
    public enum KeyResult { STARTED, CONFIRM_STOP, STOPPED }

    private final LongSupplier clock;
    private final List<Run> runs = new ArrayList<>();
    private final LootLedger loot = new LootLedger();
    private String id = "";
    private boolean running;
    private long startedAt, elapsedBase;
    private long confirmUntil = -1;
    private ActiveRun current;

    private static final class ActiveRun {
        final long enteredAt;
        Giant giant;
        boolean killed;
        int fragments;
        ActiveRun(long enteredAt) { this.enteredAt = enteredAt; }
    }

    public Session(LongSupplier clock) { this.clock = clock; }
    public static Session live() { return new Session(() -> System.nanoTime() / 1_000_000); }

    public KeyResult pressSessionKey() {
        long now = clock.getAsLong();
        if (!running) {
            start();
            return KeyResult.STARTED;
        }
        // The input latch rejects repeats; genuinely separate presses may be arbitrarily fast.
        if (confirmUntil >= now) {
            stop();
            return KeyResult.STOPPED;
        }
        confirmUntil = now + 4_000;
        return KeyResult.CONFIRM_STOP;
    }

    public void start() {
        if (running) return;
        runs.clear();
        loot.clear();
        elapsedBase = 0;
        startedAt = clock.getAsLong();
        id = java.time.Instant.now().toString().replace(':', '-');
        current = null;
        confirmUntil = -1;
        running = true;
    }

    public void stop() {
        if (!running) return;
        // A manually stopped run is partial, even if its giant was defeated.
        leaveDungeon("Session stopped", true);
        elapsedBase = elapsedMs();
        running = false;
        confirmUntil = -1;
    }

    public void enterDungeon() {
        if (running && current == null) current = new ActiveRun(clock.getAsLong());
    }

    public void observeGiant(Giant giant) {
        if (current != null && giant != null && current.giant == null) current.giant = giant;
    }

    public void giantDefeated(Giant giant) {
        if (current == null || giant == null) return;
        observeGiant(giant);
        if (current.giant == giant) current.killed = true;
    }

    public void watcherCleared() {
        if (current != null && current.giant != null) current.killed = true;
    }

    public void collectFragment(Giant giant, int count) {
        if (current == null || count <= 0) return;
        giantDefeated(giant);
        if (current.giant == giant) current.fragments += count;
    }

    public void leaveDungeon(String reason, boolean interrupted) {
        if (current == null) return;
        runs.add(new Run(runMs(), current.giant, current.killed && !interrupted,
                current.fragments, reason));
        current = null;
    }

    public long elapsedMs() { return Math.max(0, elapsedBase + (running ? clock.getAsLong() - startedAt : 0)); }
    public long runMs() { return current == null ? 0 : Math.max(0, clock.getAsLong() - current.enteredAt); }
    public boolean running() { return running; }
    public boolean inDungeon() { return current != null; }
    public Giant currentGiant() { return current == null ? null : current.giant; }
    public boolean giantKilled() { return current != null && current.killed; }
    public boolean confirmingStop() { return running && confirmUntil >= clock.getAsLong(); }
    public long confirmRemainingMs() { return Math.max(0, confirmUntil - clock.getAsLong()); }
    public void cancelConfirmation() { confirmUntil = -1; }
    public void clearSaved() {
        if (running) throw new IllegalStateException("A session is running");
        id = "";
        elapsedBase = 0;
        runs.clear();
        loot.clear();
    }
    public String id() { return id; }
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
            result.put(giant, new GiantStats(matching.size(), drops, withDrop,
                    eligible == 0 ? 0 : matching.size() * 100.0 / eligible,
                    matching.isEmpty() ? 0 : withDrop * 100.0 / matching.size()));
        }
        return result;
    }

    public Snapshot snapshot() {
        List<Run> saved=new ArrayList<>(runs);
        if(current!=null) saved.add(new Run(runMs(),current.giant,false,current.fragments,"Interrupted at save checkpoint"));
        return new Snapshot(1,id,elapsedMs(),List.copyOf(saved),loot.snapshot());
    }

    /** Restores a stopped session. Offline time and an unfinished run are never invented. */
    public void restore(Snapshot s) {
        if (s == null || s.schema != 1 || s.elapsedMs < 0 || s.runs == null || s.loot == null)
            throw new IllegalArgumentException("Invalid session save");
        for (Run r : s.runs) if (r == null || r.milliseconds < 0 || r.fragments < 0 || (r.completed && r.giant == null))
            throw new IllegalArgumentException("Invalid saved run");
        loot.restore(s.loot);
        id = s.id == null ? "" : s.id;
        elapsedBase = s.elapsedMs;
        runs.clear();
        runs.addAll(s.runs);
        running = false;
        current = null;
        confirmUntil = -1;
    }
}
