package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DungeonPresenceTest {
    private final DungeonPresence presence = new DungeonPresence();
    private static final String ENTRY = "-----------------------------\n§a[VIP] Runner entered The Catacombs, Floor VII!\n-----------------------------";

    @Test void queueInHubThenWarpAndSixtyTwoSecondsOfDungeonWithNoFloorRow() {
        presence.announce(Signals.announcedFloor(ENTRY), 0);
        presence.observe(Signals.area(List.of("⏣ Dungeon Hub")), 3_900);
        assertFalse(presence.inF7());
        presence.arrived(4_000);
        assertFalse(presence.inF7());
        presence.observe(Signals.area(List.of("Time Elapsed: 8s")), 12_000);
        presence.observe(Signals.Area.DUNGEON,13_500);
        assertEquals(9500,presence.confirmedAge(13_500));
        presence.observe(Signals.Area.DUNGEON, 47_000);
        presence.observe(Signals.Area.UNKNOWN, 65_000);
        assertTrue(presence.inF7());
        presence.arrived(66_000);
        assertFalse(presence.inF7());
    }

    @Test void floorConfirmationAlsoWorksAfterArrivalAndWhenSessionStartsMidRun() {
        presence.arrived(200);
        presence.announce(Signals.announcedFloor(ENTRY), 300);
        presence.observe(Signals.Area.DUNGEON, 500);
        presence.observe(Signals.Area.DUNGEON,2_100);
        assertTrue(presence.inF7());
        presence.arrived(4_000);
        assertFalse(presence.inF7());
        presence.observe(Signals.Area.OUTSIDE,4_010);
        presence.observe(Signals.Area.OUTSIDE,5_510);
        presence.observe(Signals.area(List.of("Dungeon: The Catacombs (F\u200b7)")),6_000);
        presence.observe(Signals.Area.F7,7_500);
        assertTrue(presence.inF7());
        presence.observe(Signals.area(List.of("Area: Hub")),8_000);
        presence.observe(Signals.Area.OUTSIDE,9_501);
        assertFalse(presence.inF7());
    }

    @Test void masterOtherFloorsAndPlayerChatNeverArmAnF7Entry() {
        for (String message : List.of(ENTRY.replace("Floor VII", "Floor VI"),
                ENTRY.replace("The Catacombs", "Master Mode The Catacombs"),
                "Party > Runner: I entered The Catacombs, Floor VII!")) {
            presence.announce(Signals.announcedFloor(message), 0);
            presence.arrived(4_000);
            presence.observe(Signals.Area.DUNGEON, 8_000);
            assertFalse(presence.inF7(), message);
        }
    }

    @Test void expiredQueueDisconnectAndContradictoryDestinationDiscardEntry() {
        presence.announce(Signals.announcedFloor(ENTRY), 0);
        presence.arrived(40_000);
        assertFalse(presence.inF7());
        presence.announce(Signals.Area.F7, 50_000);
        presence.reset();
        presence.arrived(52_000);
        assertFalse(presence.inF7());
        presence.announce(Signals.Area.F7, 60_000);
        presence.arrived(61_000);
        presence.observe(Signals.area(List.of("⏣ The Catacombs (M7)")), 61_000);
        assertFalse(presence.inF7());
    }

    @Test void aStaleHubRowOnArrivalCanBeCorrectedByMortAndAnotherF7CanQueueFromInside() {
        presence.announce(Signals.Area.F7, 1_000);
        presence.arrived(4_000);
        presence.observe(Signals.Area.OUTSIDE, 4_010);
        presence.dungeonMessage(6_000);
        assertTrue(presence.inF7());
        presence.announce(Signals.Area.F7, 8_000);
        presence.observe(Signals.Area.F7, 8_010);
        presence.arrived(10_000);
        assertFalse(presence.inF7());
        presence.dungeonMessage(10_501);
        assertTrue(presence.inF7());
        presence.arrived(30_000);
        assertFalse(presence.inF7());
    }
    @Test void transientWorldsAndStaleFloorOnReturnCannotCreateZeroOrSubsecondRuns() {
        for (long length:List.of(0L,600L,800L)) {
            presence.reset(); presence.arrived(1); presence.announce(Signals.Area.F7,4000);
            presence.arrived(4500); presence.observe(Signals.Area.F7,4500);
            presence.observe(Signals.Area.F7,4500+length);
            assertFalse(presence.inF7());
            presence.arrived(6000); presence.observe(Signals.Area.OUTSIDE,6050);
            presence.dungeonMessage(6800); assertTrue(presence.inF7()); assertEquals(800,presence.confirmedAge(6800));
            presence.observe(Signals.Area.OUTSIDE,7200); presence.observe(Signals.Area.F7,7800);
            assertTrue(presence.inF7()); assertEquals(1800,presence.confirmedAge(7800));
            presence.arrived(9000); presence.observe(Signals.Area.F7,9000); presence.observe(Signals.Area.F7,11500);
            assertFalse(presence.inF7());
        }
    }
    @Test void aVerifiedShortRunCanStillBeRecordedAndBackdatingStopsAtSessionStart() {
        var clock=new java.util.concurrent.atomic.AtomicLong(3_000);
        var s=new Session(clock::get); s.start(); clock.addAndGet(200);
        s.enterDungeon(5_000); clock.addAndGet(400); s.leaveDungeon("Warp",false);
        assertEquals(600,s.last());
    }
}
