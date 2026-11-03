package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HuntingBoxTest {
    private final HuntingBox box = new HuntingBox();
    private final HuntingShards.Shard shard = HuntingShards.byLabel("Zombie Soldier Shard");
    private final LootLedger loot = new LootLedger();
    private void add(HuntingShards.Shard s,int count) { loot.addItem(s.productId(),s.name()+" Shard",count,null,0,0); }
    private void message(String text,long time) {
        var receipt=HuntingShards.receipt(text);
        assertNotNull(receipt); add(receipt.shard(),box.receipt(receipt,true,time));
    }
    @Test void resolvesAliasAndOnlyPersonalEarnedMessages() {
        assertEquals("SHARD_WITHER_SPECTER",HuntingShards.byLabel("§aWither Spectre Shard x2").productId());
        assertEquals(shard,HuntingShards.byId("ATTRIBUTE_SHARD_UNDEAD;1"));
        assertEquals(2,HuntingShards.receipt("You caught x2 Zombie Soldier Shards!").count());
        assertEquals(2,HuntingShards.receipt("LOOT SHARE! You received 2x Zombie Soldier Shards for assisting Test!").count());
        assertNull(HuntingShards.receipt("Party > Player: You caught a Zombie Soldier Shard!"));
        assertNull(HuntingShards.receipt("FUSION! You obtained a Zombie Soldier Shard!"));
        assertTrue(HuntingShards.isBox("(2/5) Hunting Box")); assertEquals(1234,HuntingShards.owned("§7Owned: 1,234 Shards"));
    }
    @Test void captureInBoxRepricesUnderOtherItemsExactlyOnce() {
        box.observe(shard,20,false,0);
        message("CHARM! You charmed the Zombie Soldier and received 2 Zombie Soldier Shards!",10);
        box.observe(shard,22,true,100);box.settle(3_000,false,this::add);
        assertEquals(2,loot.itemCount(shard.productId()));
        assertEquals(1_975,loot.value(Map.of(shard.productId(),1_000.0),1.25,true).otherItems());
        assertEquals(3_950,loot.value(Map.of(shard.productId(),2_000.0),1.25,true).total());
    }
    @Test void boxOnlyGainsManualDepositsAndLateMessagesAreReconciled() {
        box.observe(shard,12,true,0); box.settle(2_000,false,this::add);
        assertEquals(0,loot.itemCount(shard.productId()));
        box.observe(shard,15,true,5_000); message("You sent 2 Zombie Soldier Shards to your Hunting Box.",5_100);
        box.settle(7_000,false,this::add); assertEquals(1,loot.itemCount(shard.productId()));
        message("You caught a Zombie Soldier Shard!",7_100); assertEquals(1,loot.itemCount(shard.productId()));
        box.observe(shard,15,true,9_000); box.settle(12_000,false,this::add);assertEquals(1,loot.itemCount(shard.productId()));
    }
    @Test void boxOpeningOldPagesAndResettingAfterPauseCannotCreateProfit() {
        box.observe(shard,300,true,0); box.observe(shard,300,true,2_000);box.reset();
        box.observe(shard,390,true,6_000);box.settle(8_000,false,this::add);
        assertEquals(0,loot.itemCount(shard.productId()));
    }
}
