package dev.fragruns.data;

import dev.fragruns.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SessionLogTest {
    @TempDir Path directory;
    final AtomicLong time=new AtomicLong(1000);
    final Session session=new Session(time::get);
    SessionStore store() { return new SessionStore(directory); }
    static LootLedger loot(Session.Run r) {
        var loot=new LootLedger(); loot.restore(r.details().loot()); return loot;
    }
    @Test void separatesLootAndDelayedPayoutsEvenAfterNextGiantSpawns() {
        session.start(); session.enterDungeon(); UUID a=session.currentRunId();
        session.collectFragment(Giant.DIAMANTE,1);
        session.loot().addItem(Giant.DIAMANTE.itemId,"Handle",1,null,0,0);
        session.loot().addItem("GEAR","Armor",2,"ESSENCE_UNDEAD",9,1);
        time.addAndGet(77_000); session.leaveDungeon("Warp",false);
        session.enterDungeon(); session.giantDefeated(Giant.DIAMANTE);
        session.loot().addItem("SPIRIT_LEAP","Spirit Leap",3,null,0,0);
        session.credit(a,l->l.addCoins(80_000));
        session.credit(a,l->l.addItem("SHARD_ZOMBIE_SOLDIER","Zombie Soldier Shard",2,null,0,0));
        session.credit(a,l->l.addEssence("ESSENCE_UNDEAD",4));
        session.credit(session.currentRunId(),l->l.addCoins(65_000));
        time.addAndGet(91_000); session.leaveDungeon("Warp",false);
        var first=session.runs().getFirst(); var second=session.runs().getLast();
        assertEquals(77_000,first.milliseconds()); assertEquals(91_000,second.milliseconds());
        assertEquals(1,first.fragments()); assertEquals(0,second.fragments());
        assertNotEquals(a,second.details().id()); assertTrue(second.details().giantDefeated());
        var quotes=Map.of(Giant.DIAMANTE.itemId,400_000.0,"SPIRIT_LEAP",100.0,"SHARD_ZOMBIE_SOLDIER",1250.0,"ESSENCE_UNDEAD",500.0);
        var one=loot(first).value(quotes,0,true); var two=loot(second).value(quotes,0,true);
        assertEquals(80_000,one.coins()); assertEquals(11_000,one.essence()); assertEquals(2_500,one.otherItems());
        assertEquals(65_000,two.coins()); assertEquals(300,two.otherItems()); assertEquals(0,two.essence());
        assertEquals(session.loot().value(quotes,0,true).total(),one.total()+two.total());
        assertEquals(0,loot(second).itemCount(Giant.DIAMANTE.itemId));
    }
    @Test void archivesIncludeEarlyAndCheckpointDetails() throws Exception {
        session.start(); session.enterDungeon(); session.observeGiant(Giant.LASR); session.loot().addCoins(17);
        var checkpoint=session.snapshot().runs().getFirst();
        assertFalse(checkpoint.completed()); assertFalse(checkpoint.details().giantDefeated());
        assertEquals(0,checkpoint.details().endedAt());
        session.leaveDungeon("Early warp",false);
        var store=store(); store.archiveChecked(session);
        var data=store.readArchived(session.id()).runs().getFirst();
        assertEquals(checkpoint.details().id(),data.details().id());
        assertEquals("Early warp",data.endReason()); assertTrue(data.details().endedAt()>=data.details().startedAt());
        assertEquals(17,loot(data).coins()); assertEquals(0,session.completedRuns());
    }
    @Test void switchBackRestoresIdentityWithoutTimeAwayOrDuplicateSession() throws Exception {
        var store=store();
        session.start(); String first=session.id(); session.enterDungeon(); session.loot().addCoins(130);
        time.addAndGet(21_000); session.leaveDungeon("Warp",false); session.stop(); store.archiveChecked(session);
        session.start(); String other=session.id(); session.enterDungeon(); time.addAndGet(94_000);
        session.loot().addCoins(250); UUID partial=session.currentRunId();
        store.continueSession(session,first);
        assertEquals(first,session.id()); assertEquals(21_000,session.elapsedMs()); assertEquals(130,session.loot().coins());
        var saved=store.readArchived(other).runs().getFirst(); assertFalse(saved.completed());
        assertEquals(partial,saved.details().id()); assertEquals("Switched session",saved.endReason());
        assertEquals(250,loot(saved).coins()); assertEquals(94_000,saved.milliseconds());
        time.addAndGet(6000); session.enterDungeon(); session.leaveDungeon("Warp",false); session.stop();
        store.archiveChecked(session);
        assertEquals(27_000,store.readArchived(first).elapsedMs()); assertEquals(2,store.readArchived(first).totalRuns());
        assertEquals(2,store.catalog().sessions().size());
    }
    @Test void continuingTheCurrentPauseKeepsTheInProgressRunAndExcludesOfflineTime() throws Exception {
        session.start(); session.enterDungeon(); UUID id=session.currentRunId(); time.addAndGet(3000); session.pressSessionKey();
        time.addAndGet(500_000); store().continueSession(session,session.id()); time.addAndGet(1500);
        assertEquals(id,session.currentRunId()); assertEquals(4500,session.runMs()); assertEquals(4500,session.elapsedMs());
        assertFalse(session.confirmingStop()); assertEquals(0,session.totalRuns());
    }
    @Test void oldArchiveHasOnlyGenuineHistoricalFieldsAndCanBeContinued() throws Exception {
        var store=store(); String id="2026-10-01T23-59-58.123456700Z";
        var old=new Session.Snapshot(1,id,54_321,List.of(new Session.Run(3000,Giant.BIGFOOT,true,0,"Warp")),
                new LootLedger.Snapshot(Map.of(),Map.of(),50_000));
        JsonFiles.write(directory.resolve("history/"+id+".json"),old);
        var entry=store.catalog().sessions().getFirst();
        assertEquals(Instant.parse("2026-10-01T23:59:58.123456700Z"),entry.started());
        store.continueSession(session,id); assertNull(session.runs().getFirst().details()); assertEquals(50_000,session.loot().coins());
        session.enterDungeon(); session.loot().addCoins(60_000); session.leaveDungeon("Warp",false);
        assertEquals(60_000,loot(session.runs().getLast()).coins()); assertEquals(110_000,session.loot().coins());
    }
    @Test void unreadableArchiveDoesNotHideOtherSessionsOrChangeLiveState() throws Exception {
        var store=store(); session.start(); store.archiveChecked(session); session.enterDungeon();
        var before=session.snapshot();
        Files.writeString(directory.resolve("history/bad.json"),"{}");
        assertEquals(1,store.catalog().unreadable()); assertEquals(1,store.catalog().sessions().size());
        assertThrows(IOException.class,()->store.continueSession(session,"bad"));
        assertThrows(IOException.class,()->store.continueSession(session,"../session"));
        assertEquals(before,session.snapshot()); assertTrue(session.running()); assertTrue(session.inDungeon());
    }
    @Test void checkpointFailureLeavesCurrentRunUsableAndBothArchivesReadable() throws Exception {
        var store=store(); session.start(); session.stop(); store.archiveChecked(session); String target=session.id();
        session.start(); session.enterDungeon(); var before=session.snapshot();
        Files.createDirectory(directory.resolve("session.json.tmp"));
        assertThrows(IOException.class,()->store.continueSession(session,target));
        assertTrue(session.running()); assertEquals(before,session.snapshot()); assertEquals(2,store.catalog().sessions().size());
    }
    @Test void backupsAreRecoveredOnceAndCatalogSortsNewestFirst() throws Exception {
        var store=store();
        for (String id:List.of("2026-01-01T00-00-00Z","2026-01-01T00-00-00.001Z")) {
            session.restore(new Session.Snapshot(1,id,1,List.of(),new LootLedger().snapshot()));
            store.archiveChecked(session); store.archiveChecked(session);
        }
        Files.writeString(directory.resolve("history/"+session.id()+".json"),"broken");
        var entries=store.catalog();
        assertEquals(0,entries.unreadable()); assertEquals(2,entries.sessions().size());
        assertEquals(session.id(),entries.sessions().getFirst().id()); assertEquals(1,store.readArchived(session.id()).elapsedMs());
    }
    @Test void clearKeepsOnlyMapsOfTheActiveSessionAndRejectsLateWrites() throws Exception {
        var store=store(); var maps=store.maps(); long epoch=maps.generation();
        session.start(); session.enterDungeon(); UUID old=session.currentRunId();
        session.stop(); store.archiveChecked(session); session.start(); session.enterDungeon();
        UUID kept=session.currentRunId(); session.leaveDungeon("Warp",false); session.enterDungeon(); UUID current=session.currentRunId();
        var room=new DungeonLayout.Room(List.of(0,1),0xffaa0022,"Blood","WHITE");
        var layout=new DungeonLayout(1,"",List.of(room),List.of(new DungeonLayout.Door(0,1,0xff606060)),List.of());
        for (UUID id:List.of(old,kept,current)) maps.save(id,epoch,layout);
        Files.write(directory.resolve("maps/"+old+".png"),new byte[]{1});
        store.clearHistory(session); maps.save(old,epoch,layout);
        assertThrows(IOException.class,()->maps.read(old)); assertFalse(maps.hasLegacyImage(old));
        for (UUID id:List.of(kept,current)) assertEquals(layout,maps.read(id));
        session.stop(); store.clearHistory(session); assertThrows(IOException.class,()->maps.read(kept));
        assertThrows(IllegalArgumentException.class,()->new DungeonLayout(1,"bad",List.of(),List.of(),List.of()));
    }
}
