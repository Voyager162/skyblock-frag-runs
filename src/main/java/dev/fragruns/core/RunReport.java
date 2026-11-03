package dev.fragruns.core;

import java.util.Map;
import java.util.UUID;

/** A short-lived view of the earning run. Hub receipts can replace that run's details after exit. */
public final class RunReport {
    private UUID id;
    private String sessionId="";
    private int number;
    private long visibleMs,lastVisible=-1;
    private final LootLedger ledger=new LootLedger();
    private LootLedger.Snapshot snapshot;
    private Map<String,Double> prices;
    private double tax;
    private boolean salvage;
    private LootLedger.Valuation value;

    public void finished(Session session) {
        clear();
        var run=session.latestRun();
        if (run==null || run.details()==null) return;
        sessionId=session.id();id=run.details().id();number=session.totalRuns();
    }
    public void tick(Session session,ModConfig config,boolean visible,long now) {
        if (id==null) return;
        if (!config.showRunReport || !sessionId.equals(session.id())) {clear();return;}
        if (visible && lastVisible>=0) visibleMs+=Math.max(0,now-lastVisible);
        lastVisible=visible?now:-1;
        if (visibleMs>=config.reportSeconds*1000L) clear();
    }
    public Session.Run showing(Session session) {
        if (id==null || lastVisible<0 || !sessionId.equals(session.id())) return null;
        var r=session.latestRun();
        return r!=null && r.details()!=null && id.equals(r.details().id())?r:null;
    }
    public int number() { return number; }
    public double remaining(int seconds) { return Math.clamp(1.0-visibleMs/(seconds*1000.0),0,1); }
    public LootLedger.Valuation valuation(Session.Run run,Map<String,Double> market,ModConfig config) {
        var loot=run.details().loot();
        boolean changed=snapshot!=loot;
        if (changed) {ledger.restore(loot);snapshot=loot;}
        if (changed || prices!=market || value==null || tax!=config.bazaarTaxPercent || salvage!=config.includeSalvage) {
            prices=market;tax=config.bazaarTaxPercent;salvage=config.includeSalvage;
            value=ledger.value(prices,tax,salvage);
        }
        return value;
    }
    public void clear() {
        id=null;sessionId="";lastVisible=-1;visibleMs=0;snapshot=null;prices=null;value=null;ledger.clear();
    }
}
