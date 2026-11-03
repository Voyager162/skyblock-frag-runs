package dev.fragruns.data;

import dev.fragruns.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StashTest {
    @TempDir Path directory;
    static final String PROFILE="player/Apple", OTHER="player/Banana";
    static final Giant EYE=Giant.LASR;
    static final String ALERT="One or more items didn't fit in your inventory and were added to your material stash! Click here to pick them up!";
    Session session() { var s=new Session(()->1000L);s.start();return s; }
    UUID run(Session s,Giant g,boolean killed,boolean notice) {
        s.enterDungeon();UUID id=s.currentRunId();s.observeGiant(g);if (notice) s.stash().overflow(id,PROFILE);
        if (killed) s.giantDefeated(g);s.leaveDungeon("Warp",false);return id;
    }
    void claim(SessionStore store,Session s,Giant g,int n,boolean empty) throws Exception {
        store.settleStash(s,PROFILE,Map.of(g,n),empty,false,type->5);
    }
    @Test void exactServerNoticesAndAllFourItemNames() {
        assertTrue(StashMessages.overflow(ALERT));assertTrue(StashMessages.overflow(Signals.clean("§e"+ALERT.replace("Click here","§6Click here§e"))));
        for (Giant g:Giant.values()) {
            assertEquals(new StashMessages.Drop(g,1),StashMessages.item(g.fragmentName));
            assertEquals(new StashMessages.Drop(g,2),StashMessages.item("§a2x "+g.fragmentName.replace("'","’")));
            assertEquals(new StashMessages.Drop(g,3),StashMessages.item(g.fragmentName+" ×3"));
            assertEquals(new StashMessages.Drop(g,1),StashMessages.deposited(g.fragmentName+" was added to your material stash!"));
        }
        assertFalse(StashMessages.overflow("Party > Test: "+ALERT));assertFalse(StashMessages.withdrawal("Party > Test: From stash: "+EYE.fragmentName));
        assertNull(StashMessages.item("Livid Fragment"));assertNull(StashMessages.item("Sold "+EYE.fragmentName));assertNull(StashMessages.item("0x "+EYE.fragmentName));
        assertFalse(StashMessages.materialEmptied("You picked up all items from your item stash!"));
        assertFalse(StashMessages.materialEmptied("You picked up 1 item from your material stash!"));
    }
    @Test void claimingAfterStoppingAndRestartingUpdatesBothReportsExactlyOnce() throws Exception {
        var store=new SessionStore(directory);var s=session();UUID run=run(s,EYE,true,true);s.stop();store.archiveChecked(s);store.saveSession(s);
        var reopened=Session.live();store=new SessionStore(directory);store.loadSession(reopened);
        claim(store,reopened,EYE,1,true);claim(store,reopened,EYE,1,true);
        var saved=store.readArchived(s.id());
        assertEquals(reopened.snapshot(),saved.snapshot());assertEquals(run,saved.runs().getFirst().details().id());
        assertEquals(1,saved.runs().getFirst().fragments());assertEquals(100,saved.giantStats().get(EYE).dropRate());
        var loot=new LootLedger();loot.restore(saved.runs().getFirst().details().loot());assertEquals(1,loot.itemCount(EYE.itemId));
        assertEquals(500_000,saved.loot().value(Map.of(EYE.itemId,500_000.),0,true).total());
    }
    @Test void collectionInAnotherRunDoesNotChangeThatRunsLedgerOrFragmentCount() throws Exception {
        var store=new SessionStore(directory);var s=session();run(s,EYE,true,true);s.enterDungeon();s.giantDefeated(Giant.DIAMANTE);
        claim(store,s,EYE,1,true);s.leaveDungeon("Warp",false);
        assertEquals(1,s.runs().getFirst().fragments());assertEquals(0,s.runs().getLast().fragments());
        assertTrue(s.runs().getLast().details().loot().entries().isEmpty());assertEquals(1,s.loot().itemCount(EYE.itemId));
    }
    @Test void aFreshSessionCanSettleAnOlderArchiveWithoutTakingItsProfit() throws Exception {
        var store=new SessionStore(directory);var s=session();run(s,EYE,true,true);s.stop();store.archiveChecked(s);String first=s.id();s.start();
        run(s,Giant.BIGFOOT,true,true);claim(store,s,EYE,1,true);
        assertEquals(1,store.readArchived(first).loot().itemCount(EYE.itemId));assertEquals(0,s.loot().itemCount(EYE.itemId));
        assertFalse(s.stash().outstanding());
    }
    @Test void unrelatedStockNoKillAndOtherProfilesDoNotCount() throws Exception {
        var s=session();var store=new SessionStore(directory);
        run(s,EYE,true,false);run(s,EYE,false,true);run(s,Giant.DIAMANTE,true,true);
        store.settleStash(s,OTHER,Map.of(Giant.DIAMANTE,1),true,false,g->0);
        assertTrue(s.stash().outstanding());claim(store,s,EYE,7,true);
        assertEquals(0,s.loot().itemCount(EYE.itemId));assertEquals(0,s.loot().itemCount(Giant.DIAMANTE.itemId));assertFalse(s.stash().outstanding());
    }
    @Test void itemizedDepositAndBalanceThenWithdrawalAreValuedOnce() throws Exception {
        var s=session();var store=new SessionStore(directory);run(s,EYE,true,true);
        store.settleStash(s,PROFILE,Map.of(EYE,1),false,true,g->0);
        assertEquals(1,s.runs().getFirst().fragments());assertTrue(s.stash().outstanding());
        run(s,EYE,true,true);claim(store,s,EYE,1,true);
        assertEquals(0,s.runs().getLast().fragments());assertEquals(1,s.loot().itemCount(EYE.itemId));
    }
    @Test void ambiguousRunsCreditTheSessionWithoutInventingAWinner() throws Exception {
        var s=session();var store=new SessionStore(directory);UUID a=run(s,EYE,true,true),b=run(s,EYE,true,true);
        claim(store,s,EYE,1,true);
        assertEquals(1,s.loot().itemCount(EYE.itemId));assertEquals(1,s.giantStats().get(EYE).fragments());
        assertEquals(50,s.giantStats().get(EYE).dropRate());assertTrue(s.stash().shared(a));assertTrue(s.stash().shared(b));
        assertTrue(s.runs().stream().allMatch(r->r.fragments()==0 && r.details().loot().entries().isEmpty()));
        s.stop();store.archiveChecked(s);assertEquals(s.snapshot(),store.readArchived(s.id()).snapshot());
    }
    @Test void multiFragmentStackCanConfirmEveryMatchingRunButCannotInventMoreDrops() throws Exception {
        var s=session();var store=new SessionStore(directory);run(s,EYE,true,true);run(s,EYE,true,true);claim(store,s,EYE,12,true);
        assertEquals(List.of(1,1),s.runs().stream().map(Session.Run::fragments).toList());
        assertEquals(2,s.giantStats().get(EYE).fragments());assertEquals(2,s.loot().itemCount(EYE.itemId));
    }
    @Test void fullClearRetiresUnrelatedNoticesButPartialClaimsKeepThem() throws Exception {
        var s=session();var store=new SessionStore(directory);UUID id=run(s,EYE,true,true);
        store.settleStash(s,PROFILE,Map.of(),false,false,g->0);assertTrue(s.stash().awaiting(id));
        store.settleStash(s,PROFILE,Map.of(),true,false,g->0);assertFalse(s.stash().awaiting(id));
        claim(store,s,EYE,1,true);assertEquals(0,s.loot().itemCount(EYE.itemId));
    }
    @Test void confirmedReceiptAcrossSessionsCanBeAssignedAfterRestartAndOnlyOnce() throws Exception {
        var store=new SessionStore(directory);var a=session();UUID first=run(a,EYE,true,true);a.stop();store.archiveChecked(a);
        var b=session();UUID second=run(b,EYE,true,true);b.stop();store.saveSession(b);store.archiveChecked(b);
        claim(store,b,EYE,1,true);
        assertEquals(0,b.loot().itemCount(EYE.itemId));assertTrue(store.hasStashChoice(a.id(),first));assertTrue(store.hasStashChoice(b.id(),second));
        store=new SessionStore(directory); assertTrue(store.hasStashChoice(a.id(),first));
        var assigned=store.assignStash(b,a.id(),first,g->3); assertEquals(1,assigned.runs().getFirst().fragments());
        assertFalse(store.hasStashChoice(b.id(),second));assertEquals(0,b.loot().itemCount(EYE.itemId));
        var disk=new SessionStore(directory);assertThrows(java.io.IOException.class,()->disk.assignStash(b,b.id(),second,g->3));
    }
    @Test void inventoryBeforeStashChatAndPauseResyncDoNotDuplicateAnAward() {
        var d=new GiantDrops();d.inventory(EYE,4,false,0);d.inventory(EYE,5,true,100);d.withdrawal(EYE,1);
        assertEquals(0,d.settle(EYE,1200,false));d.inventory(EYE,6,true,1300);assertEquals(1,d.settle(EYE,2200,false));
        assertEquals(0,d.stashed(EYE,1));d.reset();d.alreadyCredited(EYE,1);assertEquals(0,d.stashed(EYE,1));assertEquals(0,d.pickup(EYE,1));
    }
    @Test void twoPartialCollectionsEventuallyIdentifyBothRunsWithoutRebookingSharedProfit() throws Exception {
        var s=session();var store=new SessionStore(directory);UUID a=run(s,EYE,true,true);
        s.enterDungeon();UUID b=s.currentRunId();s.giantDefeated(EYE);s.stash().overflow(b,PROFILE);
        claim(store,s,EYE,1,false);assertTrue(s.stash().shared(a));assertEquals(1,s.loot().itemCount(EYE.itemId));
        claim(store,s,EYE,1,true);s.leaveDungeon("Warp",false);
        assertFalse(s.stash().shared(a));assertFalse(s.stash().shared(b));
        assertEquals(List.of(1,1),s.runs().stream().map(Session.Run::fragments).toList());assertEquals(2,s.loot().itemCount(EYE.itemId));
        for (var run:s.runs()) {var ledger=new LootLedger();ledger.restore(run.details().loot());assertEquals(1,ledger.itemCount(EYE.itemId));}
    }
    @Test void ambiguousInspectedBalanceAndSubsequentCollectionCreateOnlyOneReceipt() throws Exception {
        var store=new SessionStore(directory);var a=session();UUID first=run(a,EYE,true,true);a.stop();store.archiveChecked(a);
        var b=session();UUID second=run(b,EYE,true,true);b.stop();store.archiveChecked(b);
        assertEquals(1,store.settleStash(b,PROFILE,Map.of(EYE,1),false,true,g->0).choices().size());
        assertTrue(store.settleStash(b,PROFILE,Map.of(EYE,1),true,false,g->0).choices().isEmpty());
        store.assignStash(b,a.id(),first,g->0);assertFalse(store.hasStashChoice(b.id(),second));
    }
}
