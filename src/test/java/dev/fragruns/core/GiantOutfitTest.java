package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.fragruns.core.GiantOutfit.Gear.*;

class GiantOutfitTest {
    @Test void diamondAndSettledBootsIdentifyUnnamedGiants() {
        var gear=new GiantOutfit();
        assertNull(gear.observe(BOOTS_ONLY,-1,0,false)); assertNull(gear.observe(BOOTS_ONLY,-1,100,false));
        assertEquals(Giant.DIAMANTE,gear.observe(DIAMOND,-1,110,false));
        assertNull(gear.observe(BOOTS_ONLY,-1,500,false));
        assertEquals(Giant.BIGFOOT,gear.observe(BOOTS_ONLY,-1,850,false));
        assertNull(gear.observe(UNKNOWN,-1,900,true));
    }
    @Test void staticPinkAndCyclingLeatherResolveWithoutPickupOrName() {
        var gear=new GiantOutfit();
        assertNull(gear.observe(LEATHER,0xff69b4,0,false));
        assertEquals(Giant.JOLLY_PINK,gear.observe(LEATHER,0xff69b4,500,false));
        assertEquals(Giant.LASR,gear.observe(LEATHER,0xff0000,550,false));
        assertEquals(Giant.LASR,gear.observe(LEATHER,0xff69b4,600,true));
    }
    @Test void finalEquipmentOnInstantKillCountsTheGiantWithoutAddingAFragment() {
        for (Giant giant:Giant.values()) {
            var s=new Session(()->4000L); s.start();s.enterDungeon();s.observeOutfit(giant);s.giantDefeated(giant);s.leaveDungeon("Warp",false);
            assertEquals(giant,s.runs().getFirst().giant()); assertTrue(s.runs().getFirst().completed());
            var stats=s.giantStats().get(giant); assertEquals(1,stats.encounters());assertEquals(0,stats.fragments());assertEquals(0,stats.dropRate());
        }
        assertEquals(Giant.BIGFOOT,new GiantOutfit().observe(BOOTS_ONLY,-1,100,true));
    }
    @Test void sightingAloneIsPartialAndDefinitiveEvidenceCanCorrectAnInferredColor() {
        var s=new Session(()->1000L);s.start();s.enterDungeon();s.observeOutfit(Giant.JOLLY_PINK);
        s.observeOutfit(Giant.LASR);s.leaveDungeon("Early warp",false);assertEquals(0,s.completedRuns());
        assertEquals(Giant.LASR,s.runs().getFirst().giant());s.enterDungeon();s.observeOutfit(Giant.JOLLY_PINK);
        s.collectFragment(Giant.LASR,1);s.observeOutfit(Giant.JOLLY_PINK);s.leaveDungeon("Warp",false);
        assertEquals(1,s.giantStats().get(Giant.LASR).encounters());assertEquals(1,s.giantStats().get(Giant.LASR).fragments());
    }
    @Test void equipmentAndLabelsOfOtherGiantsCannotOverwriteAConfirmedNoDropKill() {
        var s=new Session(()->1000L); s.start();s.enterDungeon();
        s.observeOutfit(Giant.BIGFOOT);s.giantDefeated(Giant.BIGFOOT);
        s.observeOutfit(Giant.DIAMANTE);s.observeGiant(Giant.DIAMANTE);s.giantDefeated(Giant.DIAMANTE);
        s.leaveDungeon("Warp",false);
        assertEquals(Giant.BIGFOOT,s.runs().getFirst().giant());
        assertEquals(1,s.giantStats().get(Giant.BIGFOOT).encounters());assertEquals(0,s.runs().getFirst().fragments());
    }
}
