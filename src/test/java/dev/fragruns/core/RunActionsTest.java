package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static dev.fragruns.core.RunActions.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class RunActionsTest {
    final RunActions a=new RunActions();
    final UUID run=UUID.randomUUID();
    RunActionsTest(){a.enter(run);}
    @Test void existingSettingsKeepTheOldDelayAndSaveTheSelectedValue() {
        var gson=new com.google.gson.Gson();
        var c=gson.fromJson("{\"warpOnGiantKill\":true,\"reportSeconds\":8}",ModConfig.class);
        c.sanitize();assertEquals(1500,c.warpDelayMs);assertTrue(c.warpOnGiantKill);assertEquals(8,c.reportSeconds);
        c.warpDelayMs=2360;c.sanitize();assertEquals(2400,c.warpDelayMs);
        assertEquals(2400,gson.fromJson(gson.toJson(c),ModConfig.class).warpDelayMs);
        c.warpDelayMs=Integer.MIN_VALUE;c.sanitize();assertEquals(0,c.warpDelayMs);
        c.warpDelayMs=Integer.MAX_VALUE;c.sanitize();assertEquals(10_000,c.warpDelayMs);
    }
    @Test void everyAdjustableDelayWarpsOnceAtTheSelectedTime() {
        for (int delay=0;delay<=10_000;delay+=100) {
            a.enter(run);
            var first=a.dungeon(run,true,true,true,delay,1000);
            if (delay==0) assertEquals(WARP,first);
            else {
                assertEquals(NONE,first);
                assertEquals(NONE,a.dungeon(run,true,true,true,delay,1000+delay-1));
                assertEquals(WARP,a.dungeon(run,true,true,true,delay,1000+delay));
            }
            assertEquals(NONE,a.dungeon(run,true,true,true,delay,1001+delay));
        }
    }
    @Test void changingTheSettingAppliesToTheNextKillAndPausingCancelsALongDelay() {
        a.dungeon(run,true,true,true,2800,100);
        assertEquals(NONE,a.dungeon(run,true,true,true,100,200));
        assertEquals(WARP,a.dungeon(run,true,true,true,100,2900));
        a.enter(run);a.dungeon(run,true,true,true,100,3000);
        assertEquals(WARP,a.dungeon(run,true,true,true,100,3100));
        a.enter(run);a.dungeon(run,true,true,true,10_000,4000);
        a.clear();
        assertEquals(NONE,a.dungeon(run,true,true,true,10_000,14000));
    }
    @Test void noWarpFromSightingsOrDisabledKills() {
        assertEquals(NONE,a.dungeon(run,false,true,true,0));
        a.dungeon(run,true,false,true,100);
        assertEquals(NONE,a.dungeon(run,true,true,true,5000));
    }
    @Test void confirmedKillWaitsForLootAndWarpsOnlyOnce() {
        assertEquals(NONE,a.dungeon(run,true,true,true,10));
        assertEquals(NONE,a.dungeon(run,true,true,true,1509));
        assertEquals(WARP,a.dungeon(run,true,true,true,1510));
        assertEquals(NONE,a.dungeon(run,true,true,true,1550));
    }
    @Test void staleOrCancelledWarpsNeverFire() {
        a.dungeon(run,true,true,true,0);assertEquals(NONE,a.dungeon(run,true,true,false,1500));
        assertEquals(NONE,a.dungeon(run,true,true,true,6000));
        a.enter(run);a.dungeon(run,true,true,true,7000);a.manualTravel();
        assertEquals(NONE,a.dungeon(run,true,true,true,10_000));
        a.enter(run);a.dungeon(run,true,true,true,0);a.clear();assertEquals(NONE,a.dungeon(run,true,true,true,2000));
    }
    @Test void disablingWarpOrChangingVisitDisarmsAPendingCommand() {
        a.dungeon(run,true,true,true,0);a.dungeon(run,true,false,true,1200);
        assertEquals(NONE,a.dungeon(run,true,true,true,1600));
        a.enter(run);a.dungeon(run,true,true,true,0);assertEquals(NONE,a.dungeon(UUID.randomUUID(),true,true,true,2000));
    }
    @Test void queueOnlyAfterMatchingTrackedExitAndStableHub() {
        assertEquals(NONE,a.hub(true,true,true,false,2000));a.exit(UUID.randomUUID(),true,3000);
        assertEquals(NONE,a.hub(true,true,true,false,9000));a.enter(run);a.exit(run,true,10000);
        assertEquals(NONE,a.hub(true,false,true,false,11000));a.hub(true,true,true,false,12000);
        assertEquals(NONE,a.hub(true,true,true,false,14399));
        assertEquals(PARTY,a.hub(true,true,true,false,14400));
        assertEquals(NONE,a.hub(true,true,true,false,15000));
    }
    @Test void stashAndPlayerScreensGetTimeToFinish() {
        a.exit(run,true,0);a.hub(true,true,true,true,100);
        assertEquals(NONE,a.hub(true,true,true,true,3000));
        assertEquals(NONE,a.hub(true,true,false,false,4000));
        assertEquals(PARTY,a.hub(true,true,true,false,4100));
    }
    void requestParty() {
        a.exit(run,true,0);a.hub(true,true,true,false,100);
        assertEquals(PARTY,a.hub(true,true,true,false,2500));
    }
    @Test void soloResponseArmsJustOneNormalF7Request() {
        requestParty();a.partyMessage("You are not currently in a party.",2600);
        assertEquals(NONE,a.hub(true,true,true,false,2610));
        assertEquals(QUEUE,a.hub(true,true,true,false,3500));
        assertEquals("joininstance CATACOMBS_FLOOR_SEVEN",QUEUE.command);
        assertEquals(NONE,a.hub(true,true,true,false,3550));
        assertTrue(a.claimButton("menu/solo",3580));assertFalse(a.claimButton("menu/solo",3600));
        assertTrue(a.claimButton("confirmation/enter",3620));assertFalse(a.claimButton("other",3630));
    }
    @Test void confirmedSingletonWorksButGroupsAndChatLookalikesDoNot() {
        requestParty();a.partyMessage("Party > Kay: You are not currently in a party.",2550);
        assertEquals(NONE,a.hub(true,true,true,false,2600));
        a.partyMessage("Party Members (1)",2700);assertEquals(QUEUE,a.hub(true,true,true,false,3500));
        a.partyMessage("Party Members (2)",3540);assertFalse(a.confirming(3540));
    }
    @Test void groupJoiningDuringLookupCancelsAction() {
        requestParty();a.partyMessage("You are not currently in a party.",2600);
        a.partyMessage("Leo has joined the party!",2650);
        assertEquals(NONE,a.hub(true,true,true,false,2700));
    }
    @Test void lateRepliesPausesAndDisabledSettingsDoNotRequeue() {
        requestParty();a.partyMessage("You are not currently in a party.",8000);
        assertEquals(NONE,a.hub(true,true,true,false,8100));
        a.enter(run);requestParty();a.hub(false,true,true,false,2600);
        a.partyMessage("You are not currently in a party.",2650);assertEquals(NONE,a.hub(true,true,true,false,2700));
        a.enter(run);a.exit(run,false,0);assertEquals(NONE,a.hub(true,true,true,false,3000));
    }
    @Test void expiredAndManuallyReplacedQueuesDoNotActLater() {
        a.exit(run,true,0);a.hub(true,true,false,false,200);assertEquals(NONE,a.hub(true,true,true,false,30000));
        a.enter(run);requestParty();a.manualTravel();a.partyMessage("You're not in a party!",2800);
        assertEquals(NONE,a.hub(true,true,true,false,2801));
    }
    final SoloEntry.Option solo=new SoloEntry.Option(14,"§aSolo","Enter this dungeon on your own.");
    final SoloEntry.Option group=new SoloEntry.Option(11,"Party Finder","Search for a group.");
    @Test void choosesTheNamedSoloControlRegardlessOfSlot() {
        for(String title:List.of("The Catacombs - Floor VII","Queue for Dungeon","Select a mode","How would you like to play?"))
            assertEquals(14,SoloEntry.slot(title,List.of(group,solo)));
        assertEquals(7,SoloEntry.slot("Enter Dungeon",List.of(new SoloEntry.Option(7,"Play Solo!",""))));
    }
    @Test void neverChoosesOtherFloorsPartyFinderOrRandomConfirmations() {
        for(String title:List.of("The Catacombs - Floor VI","Master Mode - Floor VII","M7","Party Finder","Kuudra","Chest"))
            assertEquals(-1,SoloEntry.slot(title,List.of(group,solo)),title);
        assertEquals(-1,SoloEntry.slot("Confirm",List.of(new SoloEntry.Option(11,"Confirm",""))));
        assertEquals(-1,SoloEntry.slot("The Catacombs - Floor VII",List.of(solo,solo)));
        assertEquals(-1,SoloEntry.slot("Enter Dungeon",List.of(new SoloEntry.Option(11,"Solo","This option is not unlocked"))));
    }
}
