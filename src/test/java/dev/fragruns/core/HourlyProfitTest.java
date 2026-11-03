package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;
import static org.junit.jupiter.api.Assertions.*;

class HourlyProfitTest {
    private static final Map<String,Double> PRICES=Map.of(
            Giant.DIAMANTE.itemId,1_000_000.0,Giant.LASR.itemId,200_000.0,
            Giant.BIGFOOT.itemId,100_000.0,Giant.JOLLY_PINK.itemId,50_000.0,
            "SPIRIT_LEAP",100.0,"LIVID_FRAGMENT",10_000.0,"ESSENCE_UNDEAD",10.0);
    private final AtomicLong clock=new AtomicLong();
    private final Session session=new Session(clock::get);
    private final ModConfig config=new ModConfig();

    HourlyProfitTest() { config.bazaarTaxPercent=0; session.start(); }
    private void finish(Giant giant,int drops) {
        session.enterDungeon();session.giantDefeated(giant);clock.addAndGet(60_000);
        if(drops>0) {
            session.collectFragment(giant,drops);
            session.loot().addItem(giant.itemId,giant.fragmentName,drops,null,0,50_000);
        }
        session.leaveDungeon("Warp",false);
    }
    private double hourly(Map<String,Double> prices,ToDoubleFunction<String> npcPrices) {
        var collected=session.loot().value(prices,config.bazaarTaxPercent,config.includeSalvage);
        return HourlyProfit.calculate(session,collected,prices,npcPrices,config);
    }
    private double hourly() { return hourly(PRICES,id->50_000); }

    @Test void noTimeOrFinishedRunCannotInventFragmentIncome() {
        assertEquals(0,hourly());clock.set(60_000);assertEquals(0,hourly());
        session.enterDungeon();session.collectFragment(Giant.DIAMANTE,1);
        session.loot().addItem(Giant.DIAMANTE.itemId,"Handle",1,null,0,50_000);
        assertEquals(0,hourly());
    }
    @Test void usesFixedSpawnAndDropOddsWithFullSessionPace() {
        finish(Giant.DIAMANTE,0);finish(Giant.DIAMANTE,0);clock.set(1_800_000);
        // (1m + 200k + 100k + 50k) * 25% * 50% * 2 runs / 0.5 hours.
        assertEquals(675_000,hourly());
    }
    @Test void giantIdentityAndFragmentLuckDoNotChangeExpectedIncome() {
        finish(Giant.DIAMANTE,2);finish(Giant.DIAMANTE,2);clock.set(1_800_000);
        double lucky=hourly();
        session.stop();session.start();finish(Giant.JOLLY_PINK,0);finish(Giant.JOLLY_PINK,0);clock.addAndGet(1_680_000);
        assertEquals(675_000,lucky);assertEquals(lucky,hourly());
    }
    @Test void collectedModeExactlyRestoresOldCalculationIncludingPartialLoot() {
        finish(Giant.DIAMANTE,2);clock.set(1_800_000);
        session.enterDungeon();session.collectFragment(Giant.LASR,1);
        session.loot().addItem(Giant.LASR.itemId,"Eye",1,null,0,50_000);
        session.loot().addCoins(75_000);session.stop();config.expectedFragmentProfit=false;
        assertEquals(4_550_000,hourly());
        assertEquals(session.profitPerHour(session.loot().value(PRICES,0,true).total()),hourly());
    }
    @Test void onlyGiantFragmentsAreReplacedOtherLootAndSalvageRemainCollected() {
        finish(Giant.DIAMANTE,1);clock.set(3_600_000);
        session.loot().addCoins(80_000);
        session.loot().addItem("SPIRIT_LEAP","Leap",3,null,0,0);
        session.loot().addItem("LIVID_FRAGMENT","Livid",2,null,0,0);
        session.loot().addEssence("ESSENCE_UNDEAD",10);
        session.loot().addItem("ARMOR","Armor",2,"ESSENCE_UNDEAD",15,1_000);
        assertEquals(168_750+80_000+300+20_000+100+300,hourly());
        config.includeSalvage=false;
        assertEquals(168_750+80_000+300+20_000+100+2_000,hourly());
    }
    @Test void earlyResetsAndInterruptionsAddTimeButNoExpectedDrops() {
        finish(Giant.LASR,0);
        session.enterDungeon();session.observeGiant(Giant.DIAMANTE);clock.addAndGet(60_000);session.leaveDungeon("Early",false);
        session.enterDungeon();session.giantDefeated(Giant.DIAMANTE);clock.addAndGet(60_000);session.leaveDungeon("Disconnect",true);
        clock.set(3_600_000);
        assertEquals(3,session.totalRuns());assertEquals(1,session.completedRuns());assertEquals(168_750,hourly());
    }
    @Test void marketChangesRepriceExpectedFragmentsWithoutAnyCollectedFragments() {
        finish(Giant.LASR,0);clock.set(3_600_000);assertEquals(168_750,hourly());
        var changed=new java.util.HashMap<>(PRICES);changed.put(Giant.DIAMANTE.itemId,2_000_000.0);
        assertEquals(293_750,hourly(changed,id->50_000));
    }
    @Test void expectedBazaarValuePaysTaxButNpcFallbackDoesNot() {
        finish(Giant.BIGFOOT,0);clock.set(3_600_000);config.bazaarTaxPercent=1.25;
        // One valid Bazaar quote and three NPC fallbacks; kill coins have no tax.
        session.loot().addCoins(80_000);
        assertEquals(80_000+(987_500+150_000)*0.125,hourly(Map.of(Giant.DIAMANTE.itemId,1_000_000.0),id->50_000));
    }
    @Test void missingAndInvalidQuotesDoNotInventMarketValues() {
        finish(Giant.LASR,0);clock.set(3_600_000);
        assertEquals(0,hourly(Map.of(),id->0));
        assertEquals(25_000,hourly(Map.of(Giant.DIAMANTE.itemId,Double.NaN,Giant.LASR.itemId,-10.0),id->50_000));
    }
    @Test void togglingDoesNotChangeTotalsCountsHistoryOrSaves() {
        finish(Giant.DIAMANTE,1);clock.set(3_600_000);
        var original=session.snapshot();var stats=session.giantStats();
        assertEquals(168_750,hourly());config.expectedFragmentProfit=false;assertEquals(1_000_000,hourly());
        config.expectedFragmentProfit=true;assertEquals(168_750,hourly());
        assertEquals(original,session.snapshot());assertEquals(stats,session.giantStats());
    }
    @Test void stoppedAndRestoredSessionKeepsTheSameProjection() {
        finish(Giant.BIGFOOT,0);clock.set(3_600_000);session.stop();double saved=hourly();clock.addAndGet(3_600_000);
        assertEquals(saved,hourly());
        var restored=new Session(clock::get);restored.restore(session.snapshot());
        assertEquals(saved,HourlyProfit.calculate(restored,restored.loot().value(PRICES,0,true),PRICES,id->50_000,config));
    }
}
