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
        assertTrue(presence.inF7());
        presence.observe(Signals.area(List.of("Time Elapsed: 8s")), 12_000);
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
        assertTrue(presence.inF7());
        presence.arrived(4_000);
        assertFalse(presence.inF7());
        presence.observe(Signals.area(List.of("Dungeon: The Catacombs (F\u200b7)")), 5_000);
        assertTrue(presence.inF7());
        presence.observe(Signals.area(List.of("Area: Hub")), 6_000);
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
        presence.observe(Signals.Area.DUNGEON, 6_000);
        assertTrue(presence.inF7());
        presence.announce(Signals.Area.F7, 8_000);
        presence.observe(Signals.Area.F7, 8_010);
        presence.arrived(10_000);
        assertTrue(presence.inF7());
        presence.arrived(30_000);
        assertFalse(presence.inF7());
    }
}
