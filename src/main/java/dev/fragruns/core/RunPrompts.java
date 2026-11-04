package dev.fragruns.core;

import java.util.UUID;

/** Event handling produces local prompts only. A separate explicit click consumes one travel command. */
public final class RunPrompts {
    public enum Kind { WARP, QUEUE }
    public record Prompt(Kind kind,String token) {}
    private UUID visit;
    private boolean offeredWarp,waitingHub,offeredQueue;
    private Prompt pending;
    private long expires;
    public void clear() { visit=null;offeredWarp=false;waitingHub=false;offeredQueue=false;pending=null; }
    public void enter(UUID run) { clear();visit=run; }
    public Prompt dungeon(UUID run,boolean killed,boolean enabled) {
        if (!enabled) { if (pending!=null && pending.kind()==Kind.WARP) pending=null;return null; }
        if (visit==null || !visit.equals(run) || !killed || offeredWarp) return null;
        offeredWarp=true;
        return pending=new Prompt(Kind.WARP,UUID.randomUUID().toString());
    }
    public void exit(UUID run,boolean enabled,long now) {
        boolean tracked=run!=null && run.equals(visit);
        clear();
        waitingHub=tracked && enabled;expires=now+120_000;
    }
    public Prompt hub(boolean enabled,boolean inHub,long now) {
        if (!enabled || now>=expires) { cancelQueue();return null; }
        if (!inHub) { if (offeredQueue) cancelQueue();return null; }
        if (!waitingHub || offeredQueue) return null;
        offeredQueue=true;
        return pending=new Prompt(Kind.QUEUE,UUID.randomUUID().toString());
    }
    public String click(String token,boolean inDungeon,boolean inHub,long now) {
        if (pending==null || !pending.token().equals(token)) return null;
        Kind kind=pending.kind();
        if (kind==Kind.WARP ? !inDungeon : !inHub || now>=expires) return null;
        pending=null;
        return kind==Kind.WARP?"warp dhub":"joininstance CATACOMBS_FLOOR_SEVEN";
    }
    public void cancelQueue() { waitingHub=false;offeredQueue=false;if(pending!=null && pending.kind()==Kind.QUEUE) pending=null; }
    public void manualTravel() { pending=null;offeredWarp=true;cancelQueue(); }
}
