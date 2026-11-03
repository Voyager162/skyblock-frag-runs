package dev.fragruns.core;

import java.util.Locale;
import java.util.UUID;

/** Commands are issued once per tracked visit. All delayed actions must recheck their live context. */
public final class RunActions {
    public enum Action {
        NONE(""), WARP("warp dhub"), PARTY("party list"), QUEUE("joininstance CATACOMBS_FLOOR_SEVEN");
        public final String command;
        Action(String command) { this.command=command; }
    }
    private enum Queue { IDLE, HUB, PARTY, SOLO, CONFIRM }
    private UUID visit;
    private boolean sawKill;
    private long warpAt=-1,expires,hubSince=-1,requestAt;
    private Queue queue=Queue.IDLE;
    private String lastButton="";
    private int confirmations;

    public void enter(UUID run) { clear();visit=run; }
    public void clear() {
        visit=null;sawKill=false;warpAt=-1;queue=Queue.IDLE;hubSince=-1;lastButton="";confirmations=0;
    }
    public void exit(UUID run,boolean requeue,long now) {
        boolean tracked=run!=null && run.equals(visit);
        clear();
        if (tracked && requeue) {queue=Queue.HUB;expires=now+25_000;}
    }
    public Action dungeon(UUID run,boolean killed,boolean enabled,boolean available,long now) {
        return dungeon(run,killed,enabled,available,ModConfig.DEFAULT_WARP_DELAY_MS,now);
    }
    public Action dungeon(UUID run,boolean killed,boolean enabled,boolean available,int delayMs,long now) {
        if (visit==null || !visit.equals(run)) return Action.NONE;
        if (killed && !sawKill) {
            sawKill=true;
            if (enabled) warpAt=now+delayMs;
        }
        if (!enabled || warpAt>=0 && now>warpAt+3_000) warpAt=-1;
        if (warpAt<0 || now<warpAt || !available) return Action.NONE;
        warpAt=-1;
        return Action.WARP;
    }
    public Action hub(boolean enabled,boolean inHub,boolean available,boolean awaitingStash,long now) {
        if (queue==Queue.IDLE) return Action.NONE;
        if (!enabled || now>=expires || queue==Queue.PARTY && now-requestAt>5_000) {clear();return Action.NONE;}
        if (!inHub) {
            if (queue!=Queue.HUB) cancelQueue();
            hubSince=-1;return Action.NONE;
        }
        if (hubSince<0) hubSince=now;
        if (!available) return Action.NONE;
        if (queue==Queue.HUB && now-hubSince>=2_400 && (!awaitingStash || now-hubSince>=8_000)) {
            queue=Queue.PARTY;requestAt=now;return Action.PARTY;
        }
        if (queue==Queue.SOLO && now-requestAt>=1_000) {queue=Queue.CONFIRM;expires=now+5_000;return Action.QUEUE;}
        return Action.NONE;
    }
    public void partyMessage(String text,long now) {
        if (queue!=Queue.PARTY && queue!=Queue.SOLO && queue!=Queue.CONFIRM) return;
        String s=text.toLowerCase(Locale.ROOT);
        if (s.matches("party members? \\([0-9]+\\).*") && !s.matches("party members? \\(1\\)[: ]*")
                || s.contains(" has joined the party!") || s.startsWith("you have joined ") && s.contains("party")) {
            clear();return;
        }
        if (queue==Queue.PARTY && now-requestAt<=5_000 && (s.matches("you(?: are|’re|'re) not (?:currently )?in a party[.!]?")
                || s.matches("you (?:must|need to) be in a party to (?:use|run) this command[.!]?")
                || s.matches("party members? \\(1\\)[: ]*"))) {
            queue=Queue.SOLO;expires=Math.min(expires,now+3_000);
        }
    }
    public boolean confirming(long now) { return queue==Queue.CONFIRM && now<expires; }
    public boolean claimButton(String key,long now) {
        if (!confirming(now) || confirmations>=2 || key.equals(lastButton)) return false;
        confirmations++;lastButton=key;expires=now+5_000;return true;
    }
    public void cancelQueue() { queue=Queue.IDLE;hubSince=-1; }
    public void manualTravel() { warpAt=-1;sawKill=true;cancelQueue(); }
}
