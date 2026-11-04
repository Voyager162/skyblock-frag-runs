package dev.fragruns.core;
import dev.fragruns.data.DungeonLayout;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RunGradesTest {
    DungeonLayout map(boolean big,boolean connected) {
        return new DungeonLayout(1,"",List.of(
            new DungeonLayout.Room(List.of(0),0,"","","ENTRANCE"),
            new DungeonLayout.Room(big?List.of(1,7,8):List.of(1),0,"","","NORMAL"),
            new DungeonLayout.Room(List.of(2),0,"","","NORMAL"),
            new DungeonLayout.Room(List.of(3),0,"","","BLOOD")),
            connected?List.of(new DungeonLayout.Door(1,0,0),new DungeonLayout.Door(3,0,0),new DungeonLayout.Door(5,0,0)):List.of(),List.of());
    }
    @Test void routeWeightsRoomFootprintAndTime() {
        var small=RunGrades.score(map(false,true),41000,true);
        var big=RunGrades.score(map(true,true),41000,true);
        assertEquals(2,small.rooms());assertEquals(2,small.effort());assertEquals(2.5,small.difficulty());
        assertEquals(5,small.skill());assertTrue(big.difficulty()>small.difficulty());assertTrue(big.skill()>small.skill());
        assertEquals(10,RunGrades.score(map(false,true),1000,true).skill());
        assertTrue(RunGrades.score(map(false,true),82000,true).skill()<small.skill());
    }
    @Test void missingRouteAndEarlyExitAreNotInventedGrades() {
        assertNull(RunGrades.score(null,10000,true));assertNull(RunGrades.score(map(false,false),10000,true));
        assertTrue(Double.isNaN(RunGrades.score(map(false,true),10000,false).skill()));
        assertTrue(Double.isNaN(RunGrades.score(map(false,true),0,true).skill()));
    }
    @Test void roomTypesSurviveJsonAndOldMapsStillLoad() {
        var gson=new com.google.gson.Gson();var m=gson.fromJson(gson.toJson(map(true,true)),DungeonLayout.class);
        assertEquals(3,RunGrades.score(m,10000,true).effort());
        var legacy=gson.fromJson("{\"tiles\":[0],\"color\":0,\"name\":\"\",\"mark\":\"\"}",DungeonLayout.Room.class);
        assertNull(legacy.type());
    }
    Session history(Giant g,boolean drop,boolean unknown) {
        var s=new Session(()->1000);s.start();
        for(int i=0;i<20;i++) {s.enterDungeon();s.giantDefeated(g);if(drop)s.collectFragment(g,1);
            if(unknown)s.stash().overflow(s.currentRunId(),"test");s.leaveDungeon("Hub",false);}
        return s;
    }
    @Test void luckRewardsLasrSlightlyMoreThanDiamanteAndDrops() {
        assertEquals(50,RunGrades.luck(new Session(()->0)));
        int lasr=RunGrades.luck(history(Giant.LASR,true,false));
        assertTrue(lasr>RunGrades.luck(history(Giant.DIAMANTE,true,false)));
        assertTrue(lasr>RunGrades.luck(history(Giant.LASR,false,false)));
        assertTrue(RunGrades.luck(history(Giant.BIGFOOT,false,false))<50);
        assertTrue(lasr<=100);
    }
    @Test void unknownDropsDoNotCountAsFailures() {
        assertTrue(RunGrades.luck(history(Giant.LASR,false,true))>RunGrades.luck(history(Giant.LASR,false,false)));
    }
}
