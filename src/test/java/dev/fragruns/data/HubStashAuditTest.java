package dev.fragruns.data;

import dev.fragruns.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HubStashAuditTest {
    @TempDir Path directory;
    final HubStashAudit audit=new HubStashAudit();
    final Giant eye=Giant.LASR,handle=Giant.DIAMANTE;
    final Session session=new Session(()->1_000L);

    HubStashAuditTest() {
        session.start();
        audit.scope("player/profile");
    }
    void run(Giant giant,boolean normalExit) {
        session.enterDungeon();
        audit.run(session.id(),session.currentRunId(),false);
        if (giant!=null) session.giantDefeated(giant);
        audit.run(session.id(),session.currentRunId(),normalExit);
        session.leaveDungeon("Warp",!normalExit);
    }
    void compare(Map<Giant,Integer> balances) throws Exception {
        new SessionStore(directory).applyHubBalances(session,audit.intervalId(),audit.targets(),audit.gains(balances),g->50);
        audit.accept(session.id(),balances);
    }
    @Test void otherWatcherLootAfterAKillDoesNotCreditExistingEyes() throws Exception {
        var before=Map.of(eye,3,handle,9);
        compare(before);run(eye,true);compare(before);
        assertEquals(1,session.giantStats().get(eye).encounters());
        assertEquals(0,session.giantStats().get(eye).fragments());
        assertEquals(0,session.loot().itemCount(eye.itemId));
        assertFalse(audit.needed(session.id(),true));
    }
    @Test void allFourItemizedGainsCreditOriginalUnlabelledRunsWithoutAStashNotice() throws Exception {
        compare(Map.of());
        var after=new EnumMap<Giant,Integer>(Giant.class);
        for (var g:Giant.values()) {
            run(null,true);after.put(g,1);compare(after);
            var r=session.runs().getLast();
            assertEquals(g,r.giant());assertTrue(r.completed());assertEquals(1,r.fragments());
            assertEquals(100,session.giantStats().get(g).dropRate());
            var loot=new LootLedger();loot.restore(r.details().loot());
            assertEquals(1,loot.itemCount(g.itemId));
        }
    }
    @Test void aFirstReadingAfterAnF7CannotTurnOldStockIntoProfit() throws Exception {
        run(eye,true);compare(Map.of(eye,83));
        assertEquals(0,session.runs().getFirst().fragments());
        run(eye,true);compare(Map.of(eye,84));
        assertEquals(1,session.loot().itemCount(eye.itemId));
    }
    @Test void onlyTheNetGainCountsIfTheStashIsClaimedBeforeReturning() throws Exception {
        compare(Map.of(eye,3));run(eye,true);
        audit.withdrawn(Map.of(eye,4));compare(Map.of());
        assertEquals(1,session.runs().getLast().fragments());
        run(eye,true);compare(Map.of());assertEquals(0,session.runs().getLast().fragments());
    }
    @Test void withdrawingOldStockAndReopeningDoesNotCreateANewFragment() throws Exception {
        compare(Map.of(eye,2));run(eye,true);
        audit.withdrawn(Map.of(eye,2));compare(Map.of());compare(Map.of());
        assertEquals(0,session.loot().itemCount(eye.itemId));
    }
    @Test void subtractNamedDepositsAndOverlappingPickupPacketsExactlyOnce() {
        audit.accept(session.id(),Map.of(eye,2));run(eye,true);
        var target=audit.targets().keySet().iterator().next();
        audit.booked(target.session(),target.run(),eye,1);audit.booked(target.session(),target.run(),eye,1);
        assertEquals(Map.of(),audit.gains(Map.of(eye,3)));
        assertEquals(Map.of(eye,1),audit.gains(Map.of(eye,4)));
        var evidence=new GiantDrops();evidence.pickup(eye,1);
        assertEquals(1,evidence.possiblyStashed(eye));
        evidence.inventory(eye,1,true);assertEquals(0,evidence.possiblyStashed(eye));
    }
    @Test void profileSwitchDisconnectAndOutsideDepositsInvalidateEarlierComparisons() {
        audit.accept(session.id(),Map.of());run(eye,true);audit.scope("player/other");
        assertTrue(audit.targets().isEmpty());assertTrue(audit.needed(session.id(),true));
        run(eye,true);assertEquals(Map.of(),audit.gains(Map.of(eye,99)));
        audit.accept(session.id(),Map.of(eye,99));run(eye,true);audit.dirtyOutsideRun();
        assertEquals(Map.of(),audit.gains(Map.of(eye,100)));
        audit.reset();run(eye,true);assertFalse(audit.pending());
    }
    @Test void stoppedArchiveAndCheckpointCanRetryAWriteWithoutDoubleCredit() throws Exception {
        compare(Map.of(eye,7));run(eye,true);session.stop();
        var store=new SessionStore(directory);
        store.archiveChecked(session);
        // A directory in place of the atomic staging file forces a real I/O failure.
        var blocker=Files.createDirectory(directory.resolve("session.json.tmp"));
        assertThrows(java.io.IOException.class,()->store.applyHubBalances(session,audit.intervalId(),audit.targets(),Map.of(eye,1),g->50));
        Files.delete(blocker);compare(Map.of(eye,8));
        var reloaded=Session.live();store.loadSession(reloaded);
        assertEquals(1,reloaded.runs().getFirst().fragments());
        assertEquals(reloaded.snapshot(),store.readArchived(session.id()).snapshot());
    }
    @Test void oneMeasuredGainAcrossTwoMatchingRunsIsSharedOnce() throws Exception {
        compare(Map.of());run(eye,true);run(eye,true);compare(Map.of(eye,1));
        assertEquals(1,session.loot().itemCount(eye.itemId));
        assertEquals(50,session.giantStats().get(eye).dropRate());
        assertTrue(session.runs().stream().allMatch(r->r.fragments()==0 && session.stash().shared(r.details().id())));
    }
    @Test void aVerifiedFragmentDoesNotConvertAnInterruptedRunToCompleted() throws Exception {
        compare(Map.of());run(null,false);compare(Map.of(handle,1));
        assertEquals(1,session.runs().getFirst().fragments());assertFalse(session.runs().getFirst().completed());
    }
    @Test void aVerifiedGainBetweenSessionsCanBeAssignedToAnUnseenGiant() throws Exception {
        var store=new SessionStore(directory);
        compare(Map.of());run(null,true);session.stop();store.archiveChecked(session);
        var first=audit.targets().keySet().iterator().next();
        session.start();run(null,true);
        var second=audit.targets().keySet().stream().filter(t->!t.equals(first)).findFirst().orElseThrow();
        var id=audit.intervalId();
        store.applyHubBalances(session,id,audit.targets(),Map.of(handle,1),g->50);
        store.applyHubBalances(session,id,audit.targets(),Map.of(handle,1),g->50);
        var reloaded=new SessionStore(directory);
        assertTrue(reloaded.hasStashChoice(second.session(),second.run()));
        var assigned=reloaded.assignStash(session,first.session(),first.run(),g->50);
        assertEquals(1,assigned.loot().itemCount(handle.itemId));assertTrue(assigned.runs().getFirst().completed());
        assertEquals(handle,assigned.runs().getFirst().giant());
        assertFalse(reloaded.hasStashChoice(second.session(),second.run()));
        assertEquals(0,session.loot().itemCount(handle.itemId));
    }
    @Test void emptyRepliesExcludeItemStashRemindersCooldownsAndPlayerChat() {
        assertTrue(StashMessages.emptyView("Your material stash is empty!"));
        assertTrue(StashMessages.emptyView("You don't have anything stashed away!"));
        assertTrue(StashMessages.emptyView("Your material stash is currently empty!"));
        assertFalse(StashMessages.emptyView("Your item stash is empty!"));
        assertFalse(StashMessages.emptyView("You have 5 materials stashed away!"));
        assertFalse(StashMessages.emptyView("Command Failed: This command is on cooldown!"));
        assertFalse(StashMessages.emptyView("Party > Steve: Your material stash is empty!"));
    }
}
