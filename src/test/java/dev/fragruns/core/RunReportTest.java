package dev.fragruns.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RunReportTest {
    long now;
    final Session session=new Session(()->now);
    final RunReport report=new RunReport();
    final ModConfig config=new ModConfig();
    RunReportTest(){session.start();}
    void exit(){
        now+=8000;session.leaveDungeon("Left dungeon",false);report.finished(session);
        report.tick(session,config,true,now);
    }
    @Test void oldAndNewConfigsGetRequestedDefaultsAndDurationBounds(){
        var old=new Gson().fromJson("{\"includeSalvage\":false}",ModConfig.class);
        assertFalse(old.warpOnGiantKill);assertFalse(old.queueSoloF7);assertTrue(old.showRunReport);
        assertFalse(old.includeSalvage);assertEquals(4,old.reportSeconds);assertEquals(4,config.reportSeconds);
        old.reportSeconds=-4;old.sanitize();assertEquals(1,old.reportSeconds);
        old.reportSeconds=999;old.sanitize();assertEquals(30,old.reportSeconds);
    }
    @Test void reportUsesFourVisibleSecondsAndDoesNotConsumeTimeUnderAMenu(){
        session.enterDungeon();exit();report.tick(session,config,true,now+1000);
        report.tick(session,config,false,now+1100);assertNull(report.showing(session));
        report.tick(session,config,true,now+60_000);assertEquals(0.75,report.remaining(4));
        report.tick(session,config,true,now+62999);assertNotNull(report.showing(session));
        report.tick(session,config,true,now+63000);assertNull(report.showing(session));
    }
    @Test void durationChangeAndReportSwitchTakeEffectWithoutReplayingOldRuns(){
        session.enterDungeon();exit();config.reportSeconds=2;report.tick(session,config,true,now+2000);
        assertNull(report.showing(session));session.enterDungeon();exit();config.showRunReport=false;
        report.tick(session,config,true,now);config.showRunReport=true;report.tick(session,config,true,now);
        assertNull(report.showing(session));
    }
    @Test void includesOnlyThisRunAndRevaluesEachActualCategory(){
        session.loot().addCoins(12_345);session.enterDungeon();
        var id=session.currentRunId();var l=session.loot();
        l.addCoins(80_000);l.addItem("ARMOR","Armor",2,"ESSENCE_UNDEAD",10,0);
        l.addItem("SPIRIT_LEAP","Spirit Leap",3,null,0,0);l.addEssence("ESSENCE_WITHER",6);
        l.addItem("UNKNOWN","Unknown",1,null,0,0);
        session.giantDefeated(Giant.LASR);exit();
        var market=Map.of("ESSENCE_UNDEAD",50.0,"ESSENCE_WITHER",100.0,"SPIRIT_LEAP",1000.0,Giant.LASR.itemId,400_000.0);
        config.bazaarTaxPercent=0;var r=report.showing(session);assertEquals(8000,r.milliseconds());assertEquals(Giant.LASR,r.giant());
        var value=report.valuation(r,market,config);
        assertEquals(1000,value.salvage());assertEquals(600,value.directEssence());assertEquals(3000,value.otherItems());assertEquals(1,value.unpriced());
        assertEquals(84600,value.total());assertEquals(80000,value.coins());
        session.verifiedStash(id,Giant.LASR,1,true,0);session.credit(id,ledger->ledger.addCoins(2000));
        var settled=report.showing(session);assertEquals(1,settled.fragments());var updated=report.valuation(settled,market,config);
        assertEquals(486600,updated.total());assertEquals(400000,updated.fragmentValues().get(Giant.LASR));
        config.bazaarTaxPercent=1;assertEquals(482554,report.valuation(settled,market,config).total());
    }
    @Test void earlyExitsAndNewSessionsDoNotReuseAnotherRunsProfit(){
        session.enterDungeon();exit();assertFalse(report.showing(session).completed());assertNull(report.showing(session).giant());
        session.enterDungeon();session.loot().addCoins(1000);exit();assertEquals(2,report.number());
        assertEquals(1000,report.valuation(report.showing(session),Map.of(),config).total());
        session.stop();session.start();report.tick(session,config,true,now);assertNull(report.showing(session));
    }
}
