package dev.fragruns.ui;

import dev.fragruns.core.Giant;
import dev.fragruns.core.LootLedger;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FragmentProfitRowTest {
    @Test void combinedRowUsesActualQuantitiesAndOnlyTheFourNetFragmentSubtotals() {
        var loot = new LootLedger();
        loot.addItem(Giant.DIAMANTE.itemId,"Handle",2,null,0,50_000);
        loot.addItem(Giant.LASR.itemId,"Eye",1,null,0,50_000);
        loot.addItem(Giant.BIGFOOT.itemId,"Bola",3,null,0,50_000);
        loot.addItem(Giant.JOLLY_PINK.itemId,"Rock",1,null,0,0);
        loot.addItem("SHARD_SKELETOR","Skeletor Shard",5,null,0,100);
        loot.addEssence("ESSENCE_UNDEAD",7);
        loot.addCoins(80_000);
        var original = loot.snapshot();
        for (double handle : new double[]{400_000,375_000}) {
            var prices = Map.of(Giant.DIAMANTE.itemId,handle,Giant.BIGFOOT.itemId,100_000d,"ESSENCE_UNDEAD",400d);
            var value = loot.value(prices,1.25,true);
            var separate = FragmentProfitRow.rows(loot,value,false);
            var combined = FragmentProfitRow.rows(loot,value,true);
            assertEquals(4,separate.size());
            assertEquals(1,combined.size());
            assertEquals("Giant fragments ×7",combined.getFirst().label());
            assertEquals((2*handle+300_000)*0.9875+50_000,combined.getFirst().coins(),1e-6);
            assertEquals(separate.stream().mapToDouble(FragmentProfitRow::coins).sum(),combined.getFirst().coins());
            assertEquals(value.total(),combined.getFirst().coins()+value.essence()+value.otherItems()+value.coins(),1e-6);
        }
        assertEquals(original,loot.snapshot());
    }
}
