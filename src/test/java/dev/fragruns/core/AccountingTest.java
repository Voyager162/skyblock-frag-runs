package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AccountingTest {
    @Test void packetThenInventoryCountsOnlyOnce() {
        var r=new PickupReconciler();assertEquals(3,r.observe("FRAG",3,PickupReconciler.Source.PACKET,100));assertEquals(0,r.observe("FRAG",3,PickupReconciler.Source.INVENTORY,300));
    }
    @Test void inventoryThenPacketCountsOnlyOnce() {
        var r=new PickupReconciler();assertEquals(3,r.observe("FRAG",3,PickupReconciler.Source.INVENTORY,100));assertEquals(0,r.observe("FRAG",3,PickupReconciler.Source.PACKET,300));
    }
    @Test void coalescedInventoryUpdateMatchesSeveralPickups() {
        var r=new PickupReconciler();r.observe("FRAG",1,PickupReconciler.Source.PACKET,100);r.observe("FRAG",2,PickupReconciler.Source.PACKET,200);
        assertEquals(1,r.observe("FRAG",4,PickupReconciler.Source.INVENTORY,300));assertEquals(0,r.observe("FRAG",1,PickupReconciler.Source.PACKET,400));
    }
    @Test void differentItemsNeverSuppressEachOther() {
        var r=new PickupReconciler();r.observe("A",1,PickupReconciler.Source.PACKET,100);assertEquals(1,r.observe("B",1,PickupReconciler.Source.INVENTORY,200));
    }
    @Test void oldCreditsExpireAndRunResetClearsThem() {
        var r=new PickupReconciler();r.observe("A",1,PickupReconciler.Source.PACKET,100);assertEquals(1,r.observe("A",1,PickupReconciler.Source.INVENTORY,2200));r.reset();assertEquals(1,r.observe("A",1,PickupReconciler.Source.PACKET,2300));
    }
    @Test void purseBaselineIsNotIncomeAndDeltasAreDelayed() {
        var p=new PurseTracker();AtomicReference<Double> sum=new AtomicReference<>(0.0);
        p.observe(1_000_000,100);p.observe(1_070_000,200);p.flush(500,false,v->sum.updateAndGet(s->s+v));assertEquals(0,sum.get());
        p.flush(2_000,false,v->sum.updateAndGet(s->s+v));assertEquals(70_000,sum.get());
    }
    @Test void transactionsCanArriveBeforeOrAfterThePursePacket() {
        var p=new PurseTracker();p.observe(1_000,0);p.observe(101_000,100);p.excludeTransaction(200);p.flush(5_000,true,v->fail("Sale counted as kill coins"));
        p.excludeTransaction(6_000);p.observe(201_000,6_100);p.flush(9_000,true,v->fail("Sale counted as kill coins"));
    }
    @Test void observedCoinLossReducesProfitAndNewWorldResetsBalance() {
        var p=new PurseTracker();p.observe(50_000,0);p.observe(49_000,100);p.flush(2_000,false,v->assertEquals(-1000,v));p.reset();p.observe(500_000,5_000);p.flush(8_000,true,v->fail());
    }
    @Test void livePricesRevalueAllHarvestedLoot() {
        var l=new LootLedger();l.addItem("FRAG","Fragment",3,null,0,50_000);l.addCoins(80_000);
        assertEquals(380_000,l.value(Map.of("FRAG",100_000.0),0,true).total());assertEquals(680_000,l.value(Map.of("FRAG",200_000.0),0,true).total());
    }
    @Test void salvageReplacesItemValueInsteadOfAddingBoth() {
        var l=new LootLedger();l.addItem("ARMOR","Armor",2,"ESSENCE_UNDEAD",15,1);
        var values=Map.of("ARMOR",1_000_000.0,"ESSENCE_UNDEAD",500.0);
        assertEquals(15_000,l.value(values,0,true).total());assertEquals(2_000_000,l.value(values,0,false).total());
    }
    @Test void taxAppliesToBazaarButNotCoinsOrNpcSales() {
        var l=new LootLedger();l.addItem("BZ","Bazaar",1,null,0,0);l.addItem("NPC","Npc",1,null,0,100);l.addCoins(100);
        assertEquals(299,l.value(Map.of("BZ",100.0),1,true).total());
    }
    @Test void missingPricesAreVisibleAndDoNotUseFakePrices() {
        var l=new LootLedger();l.addItem("UNKNOWN","Unknown",1,null,0,0);l.addEssence("ESSENCE_WITHER",3);
        var v=l.value(Map.of(),0,true);assertEquals(2,v.unpriced());assertEquals(0,v.total());
    }
    @Test void perfectQualityAndOrdinaryGearStaySeparate() {
        var l=new LootLedger();l.addItem("ARMOR","Armor",2,"ESSENCE_UNDEAD",10,1);l.addItem("ARMOR","Armor",1,"ESSENCE_UNDEAD",25,1);
        assertEquals(3,l.itemCount("ARMOR"));assertEquals(45,l.value(Map.of("ESSENCE_UNDEAD",1.0),0,true).total());
    }
    @Test void invalidValuesNeverContaminateProfit() {
        var l=new LootLedger();l.addCoins(Double.NaN);l.addCoins(Double.POSITIVE_INFINITY);l.addItem("BAD","Bad",-2,null,0,1);l.addItem("X","X",1,null,0,0);
        assertEquals(0,l.value(Map.of("X",Double.NaN),0,true).total());assertEquals(1,l.value(Map.of(),0,true).unpriced());
    }
    @Test void ledgerRoundtripRetainsAllCategories() {
        var l=new LootLedger();l.addCoins(83_001.5);l.addEssence("ESSENCE_WITHER",2);l.addItem("FRAG","Fragment",2,null,0,50_000);
        var restored=new LootLedger();restored.restore(l.snapshot());assertEquals(l.snapshot(),restored.snapshot());
    }
    @Test void profitBreakdownSeparatesEveryGiantAndGroupsOtherFragmentsWithOtherItems() {
        var ledger=new LootLedger();
        var prices=new java.util.HashMap<String,Double>();
        int index=1;
        for(Giant giant:Giant.values()) {
            ledger.addItem(giant.itemId,giant.fragmentName,index,null,0,0);
            prices.put(giant.itemId,index*1000.0);index++;
        }
        for(String id:new String[]{"SPIRIT_LEAP","LIVID_FRAGMENT","BONZO_FRAGMENT","SUPERIOR_FRAGMENT"}) {
            ledger.addItem(id,id,2,null,0,0);prices.put(id,100.0);
        }
        ledger.addItem("ARMOR","Armor",2,"ESSENCE_UNDEAD",15,1);
        ledger.addEssence("ESSENCE_UNDEAD",3);ledger.addEssence("ESSENCE_WITHER",5);
        prices.put("ESSENCE_UNDEAD",20.0);prices.put("ESSENCE_WITHER",100.0);ledger.addCoins(80_000);
        var value=ledger.value(prices,1,true);
        index=1;
        for(Giant giant:Giant.values()) { assertEquals(index*index*990.0,value.fragmentValues().get(giant),0.0001);index++; }
        assertEquals(792,value.otherItems(),0.0001);
        assertEquals(1148.4,value.essence(),0.0001);
        assertEquals(80_000,value.coins());
        assertEquals(value.total(),value.fragmentValues().values().stream().mapToDouble(Double::doubleValue).sum()+value.otherItems()+value.essence()+value.coins(),0.0001);
    }
    @Test void salvageToggleMovesGearValueBetweenEssenceAndOtherItemsWithoutDuplicatingIt() {
        var ledger=new LootLedger();ledger.addItem("ARMOR","Armor",2,"ESSENCE_UNDEAD",15,500);
        ledger.addEssence("ESSENCE_UNDEAD",4);
        var prices=Map.of("ESSENCE_UNDEAD",10.0);
        var on=ledger.value(prices,0,true);var off=ledger.value(prices,0,false);
        assertEquals(340,on.essence());assertEquals(0,on.otherItems());assertEquals(340,on.total());
        assertEquals(40,off.essence());assertEquals(1000,off.otherItems());assertEquals(1040,off.total());
    }
    @Test void breakdownRevaluesWithMarketAndKeepsZeroCategoriesWhenQuotesAreUnavailable() {
        var ledger=new LootLedger();ledger.addItem(Giant.DIAMANTE.itemId,"Handle",2,null,0,0);
        var unavailable=ledger.value(Map.of(),0,true);
        assertEquals(4,unavailable.fragmentValues().size());assertEquals(0,unavailable.total());assertEquals(1,unavailable.unpriced());
        assertEquals(200_000,ledger.value(Map.of(Giant.DIAMANTE.itemId,100_000.0),0,true).fragmentValues().get(Giant.DIAMANTE));
        assertEquals(400_000,ledger.value(Map.of(Giant.DIAMANTE.itemId,200_000.0),0,true).fragmentValues().get(Giant.DIAMANTE));
    }
}
