package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EssenceBalancesTest {
    @Test void silentDropsReconcileWithoutDoubleCountingChatOrSalvage() {
        var b=new EssenceBalances();var collected=new HashMap<String,Long>();
        b.observeHub(Map.of("ESSENCE_UNDEAD",100L),collected::put);b.enter();b.found("ESSENCE_UNDEAD",2);b.salvaged("ESSENCE_UNDEAD",70);b.leave();
        b.observeHub(Map.of("ESSENCE_UNDEAD",200L),collected::put);assertEquals(28,collected.get("ESSENCE_UNDEAD"));
        collected.clear();b.observeHub(Map.of("ESSENCE_UNDEAD",200L),collected::put);assertTrue(collected.isEmpty());
    }
    @Test void existingEssenceAndOutOfRunChangesAreNotProfit() {
        var b=new EssenceBalances();b.observeHub(Map.of("ESSENCE_UNDEAD",100L),(id,n)->fail());b.observeHub(Map.of("ESSENCE_UNDEAD",1000L),(id,n)->fail());
    }
    @Test void partialTabUpdateWaitsForCompleteSnapshot() {
        var b=new EssenceBalances();b.observeHub(Map.of("ESSENCE_UNDEAD",100L,"ESSENCE_WITHER",10L),(id,n)->fail());b.enter();b.leave();
        b.observeHub(Map.of("ESSENCE_UNDEAD",110L),(id,n)->fail());var found=new HashMap<String,Long>();
        b.observeHub(Map.of("ESSENCE_UNDEAD",110L,"ESSENCE_WITHER",15L),found::put);assertEquals(10,found.get("ESSENCE_UNDEAD"));assertEquals(5,found.get("ESSENCE_WITHER"));
    }
    @Test void tradingEssenceInvalidatesReconciliation() {
        var b=new EssenceBalances();b.observeHub(Map.of("ESSENCE_UNDEAD",100L),(id,n)->fail());b.enter();b.leave();b.invalidate();b.observeHub(Map.of("ESSENCE_UNDEAD",10000L),(id,n)->fail());
    }
    @Test void parsesExactTabBalancesAndRejectsRoundedNumbersAndOtherCurrencies() {
        var b=EssenceBalances.parse(List.of("§7Undead: §a12,345","Wither Essence: 100","Dragon: 1.2k","Coins: 9,000"));
        assertEquals(Map.of("ESSENCE_UNDEAD",12345L,"ESSENCE_WITHER",100L),b);
    }
}
