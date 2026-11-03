package dev.fragruns.core;

/** A queue message identifies a floor; evidence in the destination identifies the actual visit. */
public final class DungeonPresence {
    private static final long NONE=Long.MIN_VALUE, QUEUE_MS=30_000, SETTLE_MS=1_500;
    private long queueUntil=NONE, arrival=NONE, exactSince=NONE, dungeonSince=NONE, outsideSince=NONE, entered=NONE;
    private boolean announcedArrival, leftDungeon;

    public boolean inF7() { return entered!=NONE; }
    public long confirmedAge(long now) { return inF7()?Math.max(0,now-entered):0; }
    public void announce(Signals.Area floor,long now) {
        if (floor==Signals.Area.F7) {
            queueUntil=now+QUEUE_MS;
            if (!inF7() && arrival!=NONE && now-arrival<3_000 && outsideSince==NONE) announcedArrival=true;
        }
        if (floor==Signals.Area.OTHER_FLOOR) { queueUntil=NONE; announcedArrival=false; }
    }
    public void arrived(long now) {
        leftDungeon |= inF7();
        entered=exactSince=dungeonSince=outsideSince=NONE;
        arrival=now;
        announcedArrival=queueUntil>=now;
    }
    public void observe(Signals.Area area,long now) {
        if (area!=Signals.Area.F7) exactSince=NONE;
        if (area==Signals.Area.F7 || area==Signals.Area.DUNGEON) {
            if (dungeonSince==NONE) dungeonSince=now;
            if (!inF7() && announcedArrival && queueUntil>=now && now-dungeonSince>=SETTLE_MS) confirm(arrival);
        } else dungeonSince=NONE;
        if (area!=Signals.Area.OUTSIDE) outsideSince=NONE;
        switch (area) {
            case F7 -> {
                if (inF7()) return;
                if (exactSince==NONE) exactSince=now;
                if (!leftDungeon && now-exactSince>=SETTLE_MS) confirm(exactSince);
            }
            case OUTSIDE -> {
                if (outsideSince==NONE) outsideSince=now;
                if (now-outsideSince>=SETTLE_MS) { entered=NONE; leftDungeon=false; }
            }
            case OTHER_FLOOR -> { entered=queueUntil=NONE; announcedArrival=false; leftDungeon=false; }
            case UNKNOWN, DUNGEON -> { /* Partial sidebars don't create or split a visit. */ }
        }
    }
    /** Mort and Watcher messages come from this world, including when the banner arrived after the warp. */
    public void dungeonMessage(long now) {
        if (inF7()) { outsideSince=NONE;return; }
        if (queueUntil>=now || exactSince!=NONE) confirm(announcedArrival?arrival:exactSince!=NONE?exactSince:now);
    }
    private void confirm(long when) {
        entered=when; queueUntil=NONE; announcedArrival=false; leftDungeon=false;
    }
    public void reset() {
        queueUntil=arrival=exactSince=dungeonSince=outsideSince=entered=NONE;
        announcedArrival=leftDungeon=false;
    }
}
