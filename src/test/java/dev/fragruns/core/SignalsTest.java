package dev.fragruns.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SignalsTest {
    @Test void onlyNormalF7OnSkyblockStartsARun() {
        assertEquals(Signals.Area.F7,Signals.area(List.of("§7⏣ The Catacombs §c(F7)")));
        assertEquals(Signals.Area.OTHER_FLOOR,Signals.area(List.of("The Catacombs (M7)")));
        assertEquals(Signals.Area.OTHER_FLOOR,Signals.area(List.of("The Catacombs (F6)")));
        assertEquals(Signals.Area.UNKNOWN,Signals.area(List.of("Party > Player: The Catacombs (F7)")));
    }
    @Test void purseHandlesCommasAndDecimalsButRejectsRoundedAmounts() {
        assertEquals(1_234_567.8,Signals.purse(List.of("§6Purse: §e1,234,567.8 (+22)" )).orElseThrow());
        assertEquals(600,Signals.purse(List.of("Piggy: 600")).orElseThrow());
        assertTrue(Signals.purse(List.of("Purse: 1.2M")).isEmpty());assertTrue(Signals.purse(List.of()).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"[Bazaar] Sold 2x Bigfoot's Bola for 91,110 coins!","You collected 350,000 coins from selling Armor in an auction!","ALLOWANCE! You earned 50,000 coins!","You have deposited 899.6k coins!","You have withdrawn 100 coins!","Trade completed!"})
    void economyMessagesAreExcluded(String message) { assertTrue(Signals.externalMoney(message)); }
    @ParameterizedTest @ValueSource(strings={"+10 Kill Combo +10 coins per kill","You died and lost 920 coins!","Scavenger +1,500 coins","Party > Someone: I sold 10 coins"})
    void combatAndPlayerChatAreNotTransactions(String message) { assertFalse(Signals.externalMoney(message)); }
    @Test void realFoundEssenceMessageParsesAndSalvageIsIgnored() {
        var e=Signals.essence("You found a Wither Essence! Everyone gains an extra essence!",false);
        assertEquals(new Signals.Essence("ESSENCE_WITHER",1),e);
        assertEquals(new Signals.Essence("ESSENCE_UNDEAD",70),Signals.essence("   70x Undead Essence!",false));
        assertNull(Signals.essence("   70x Undead Essence!",true));
        assertNull(Signals.essence("Party > User: You found a Wither Essence!",false));
    }
    @Test void watcherStartAndOrdinaryDialogueNeverConfirmAKill() {
        assertFalse(Signals.watcherCleared("[BOSS] The Watcher: I'm impressed."));
        assertFalse(Signals.watcherCleared("[BOSS] The Watcher: Ah, we meet again. As I foresaw..."));
        assertTrue(Signals.watcherCleared("[BOSS] The Watcher: You have proven yourself. You may pass."));
    }
    @Test void giantAliasesMapToCurrentApiIds() {
        assertEquals(Giant.DIAMANTE,Giant.fromName("§cThe Diamond Giant §a25M❤"));
        assertEquals(Giant.LASR,Giant.fromName("[Lv100] L.A.S.R. 100❤"));
        assertEquals(Giant.BIGFOOT,Giant.fromItem("GIANT_FRAGMENT_BIGFOOT"));
        assertEquals(Giant.JOLLY_PINK,Giant.fromItem("GIANT_FRAGMENT_BOULDER"));
        assertNull(Giant.fromItem("BONZO_FRAGMENT"));
    }
}
