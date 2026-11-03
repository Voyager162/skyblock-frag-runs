package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    private final AtomicLong clock=new AtomicLong(100_000);
    private final Session session=new Session(clock::get);
    private void advance(long ms) { clock.addAndGet(ms); }
    private void run(long length,Giant giant,boolean killed,int fragments) {
        session.enterDungeon();session.observeGiant(giant);advance(length);
        if(killed) session.giantDefeated(giant);
        if(fragments>0) session.collectFragment(giant,fragments);
        session.leaveDungeon("Warp",false);
    }
    @Test void timerIncludesDowntimeButCurrentClockIsZeroOutsideDungeon() {
        session.start();advance(5_000);assertEquals(0,session.runMs());
        session.enterDungeon();advance(62_500);assertEquals(62_500,session.runMs());
        session.leaveDungeon("Warp",false);advance(7_500);
        assertEquals(75_000,session.elapsedMs());assertEquals(0,session.runMs());
    }
    @Test void fastestSlowestLastAndAverageFollowEveryExit() {
        session.start();run(80_000,Giant.DIAMANTE,true,1);run(120_000,Giant.LASR,true,0);run(100_000,Giant.BIGFOOT,true,1);
        assertEquals(80_000,session.fastest());assertEquals(120_000,session.slowest());assertEquals(100_000,session.last());assertEquals(100_000,session.average());assertEquals(3,session.totalRuns());
    }
    @Test void earlyExitsDoNotBiasGiantOrFragmentRates() {
        session.start();run(30_000,Giant.DIAMANTE,false,0);run(70_000,Giant.LASR,true,1);run(90_000,Giant.LASR,true,0);run(80_000,Giant.BIGFOOT,true,1);
        var stats=session.giantStats();assertEquals(0,stats.get(Giant.DIAMANTE).encounters());
        assertEquals(2,stats.get(Giant.LASR).encounters());assertEquals(66.6667,stats.get(Giant.LASR).frequency(),0.001);
        assertEquals(50,stats.get(Giant.LASR).dropRate());assertEquals(4,session.totalRuns());assertEquals(3,session.completedRuns());
    }
    @Test void giantSpawnsAndKillsAreCountedOncePerRun() {
        session.start();session.enterDungeon();
        for(int i=0;i<100;i++) {session.observeGiant(Giant.DIAMANTE);session.giantDefeated(Giant.DIAMANTE);}
        advance(60_000);session.leaveDungeon("Warp",false);session.leaveDungeon("Duplicate packet",false);
        assertEquals(1,session.totalRuns());assertEquals(1,session.giantStats().get(Giant.DIAMANTE).encounters());
    }
    @Test void dropRateIsChanceOfADropNotNumberOfFragments() {
        session.start();run(60_000,Giant.DIAMANTE,true,2);run(60_000,Giant.DIAMANTE,true,0);
        var stat=session.giantStats().get(Giant.DIAMANTE);assertEquals(2,stat.fragments());assertEquals(50,stat.dropRate());
    }
    @Test void stopNeedsTwoSeparatePressesAndFreezesBothTimers() {
        assertEquals(Session.KeyResult.STARTED,session.pressSessionKey());advance(60_000);session.enterDungeon();advance(20_000);
        assertEquals(Session.KeyResult.CONFIRM_STOP,session.pressSessionKey());assertTrue(session.running());
        advance(400);assertEquals(Session.KeyResult.STOPPED,session.pressSessionKey());assertFalse(session.running());
        long elapsed=session.elapsedMs();advance(10_000);assertEquals(elapsed,session.elapsedMs());assertEquals(0,session.runMs());
    }
    @Test void expiredConfirmationMustBeArmedAgain() {
        session.pressSessionKey();advance(1_000);session.pressSessionKey();advance(4_001);
        assertFalse(session.confirmingStop());assertEquals(Session.KeyResult.CONFIRM_STOP,session.pressSessionKey());assertTrue(session.running());
    }
    @Test void twoDistinctPressesCanConfirmImmediatelyWithoutAnArtificialDelay() {
        session.pressSessionKey();advance(1_000);session.pressSessionKey();advance(10);
        assertEquals(Session.KeyResult.STOPPED,session.pressSessionKey());assertFalse(session.running());
    }
    @Test void holdingTheSessionKeyCannotArmOrConfirmTheStop() {
        var latch=new PressLatch();
        assertTrue(latch.press());assertEquals(Session.KeyResult.STARTED,session.pressSessionKey());
        for(int i=0;i<100;i++) {advance(50);assertFalse(latch.press());}
        assertTrue(session.running());assertFalse(session.confirmingStop());
        latch.release();assertTrue(latch.press());assertEquals(Session.KeyResult.CONFIRM_STOP,session.pressSessionKey());
        for(int i=0;i<10;i++) {advance(50);assertFalse(latch.press());}
        assertTrue(session.running());
        latch.release();assertTrue(latch.press());assertEquals(Session.KeyResult.STOPPED,session.pressSessionKey());
    }
    @Test void duplicateUiAndTickDeliveryIsOnePressEvenDuringConfirmation() {
        var latch=new PressLatch();session.start();
        assertTrue(latch.press());session.pressSessionKey();
        assertFalse(latch.press());assertTrue(session.confirmingStop());assertTrue(session.running());
        latch.release();assertTrue(latch.press());session.pressSessionKey();assertFalse(session.running());
    }
    @Test void escapeCanCancelStop() {
        session.pressSessionKey();advance(1_000);session.pressSessionKey();session.cancelConfirmation();advance(500);
        assertEquals(Session.KeyResult.CONFIRM_STOP,session.pressSessionKey());
    }
    @Test void stoppingMidRunRecordsPartialAndKeepsLoot() {
        session.start();session.enterDungeon();session.collectFragment(Giant.DIAMANTE,1);
        session.loot().addItem(Giant.DIAMANTE.itemId,"Handle",1,null,0,50000);advance(60_000);session.stop();
        assertEquals(0,session.completedRuns());assertEquals(1,session.loot().itemCount(Giant.DIAMANTE.itemId));
    }
    @Test void recoveryNeverResumesAnOldClockOrCountsOfflineTime() {
        session.start();run(60_000,Giant.LASR,true,1);advance(20_000);var snapshot=session.snapshot();advance(8_000_000);
        Session restored=new Session(clock::get);restored.restore(snapshot);assertFalse(restored.running());assertEquals(80_000,restored.elapsedMs());assertEquals(1,restored.totalRuns());assertEquals(0,restored.runMs());
    }
    @Test void newSessionResetsAllCountersAndLoot() {
        session.start();run(60_000,Giant.DIAMANTE,true,1);session.loot().addCoins(80_000);session.stop();session.start();
        assertEquals(0,session.elapsedMs());assertEquals(0,session.totalRuns());assertEquals(0,session.loot().coins());
    }
    @Test void profitPerHourUsesWholeSessionAndIsSafeAtZero() {
        assertEquals(0,session.profitPerHour(100));session.start();advance(1_800_000);assertEquals(4_000_000,session.profitPerHour(2_000_000));
    }
    @Test void enteringOrReceivingDropsWithoutSessionDoesNothing() {
        session.enterDungeon();session.collectFragment(Giant.DIAMANTE,1);assertFalse(session.inDungeon());assertEquals(0,session.totalRuns());
    }
    @Test void disconnectIsPartialEvenWithAKnownGiant() {
        session.start();session.enterDungeon();session.giantDefeated(Giant.DIAMANTE);advance(50_000);session.leaveDungeon("Disconnect",true);assertEquals(0,session.completedRuns());
    }
    @Test void currentRunDoesNotPolluteCompletedStatistics() {
        session.start();session.enterDungeon();session.collectFragment(Giant.LASR,1);assertEquals(0,session.totalRuns());assertEquals(0,session.giantStats().get(Giant.LASR).encounters());
    }
    @Test void crashCheckpointKeepsTheUnfinishedRunAsPartial() {
        session.start();session.enterDungeon();session.collectFragment(Giant.DIAMANTE,1);advance(38_000);
        var recovered=new Session(clock::get);recovered.restore(session.snapshot());
        assertEquals(1,recovered.totalRuns());assertEquals(0,recovered.completedRuns());assertEquals(38_000,recovered.last());
        assertFalse(recovered.running());assertEquals(0,recovered.runMs());
        assertEquals(0,session.totalRuns());assertEquals(38_000,session.runMs());
    }
}
