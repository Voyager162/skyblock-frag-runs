package dev.fragruns.data;

import com.google.gson.JsonParser;
import dev.fragruns.core.Giant;
import dev.fragruns.core.ModConfig;
import dev.fragruns.core.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DataTest {
    @TempDir Path directory;
    @Test void bundledItemCatalogContainsCurrentGiantFragmentsAndDungeonGear() {
        var c=new ItemCatalog();assertTrue(c.size()>2000);
        for(var giant:Giant.values()) assertEquals(giant.fragmentName,c.get(giant.itemId).name());
        assertEquals("ESSENCE_UNDEAD",c.get("SKELETON_MASTER_CHESTPLATE").essenceId());
        assertEquals("ESSENCE_WITHER",c.get("SILENT_DEATH").essenceId());
    }
    @Test void salvageUsesItemFamilyAndPerfectQualityBonus() {
        var item=new ItemCatalog().get("SKELETON_MASTER_CHESTPLATE");
        assertEquals(15,item.salvageAmount("EPIC",49));assertEquals(15,item.salvageAmount("RARE",49));assertEquals(25,item.salvageAmount("LEGENDARY",50));
        assertEquals(35,item.salvageAmount("LEGENDARY",50,5));
        assertEquals(5,new ItemCatalog().get("MACHINE_GUN_BOW").salvageAmount("EPIC",0));
    }
    @Test void nonGearDoesNotTurnIntoImaginaryEssence() {
        var c=new ItemCatalog();assertEquals(0,c.get(Giant.DIAMANTE.itemId).salvageAmount("EPIC",0));
        assertEquals(15,c.get("SCARF_STUDIES").salvageAmount("RARE",0));
    }
    @Test void bazaarUsesInstantSellOrdersNotThePurchasePrice() {
        var json=JsonParser.parseString("""
            {"success":true,"lastUpdated":1700000000000,"products":{
            "GIANT_FRAGMENT_DIAMOND":{"sell_summary":[{"pricePerUnit":100}],"buy_summary":[{"pricePerUnit":200}],"quick_status":{"sellPrice":90}},
            "NO_BUYERS":{"sell_summary":[]}}}
            """).getAsJsonObject();
        var p=MarketService.parseBazaar(json);assertEquals(100,p.values().get("GIANT_FRAGMENT_DIAMOND"));assertFalse(p.values().containsKey("NO_BUYERS"));
    }
    @Test void emptyOrUnsuccessfulApiResponseCannotWipeGoodPrices() {
        assertThrows(IllegalArgumentException.class,()->MarketService.parseBazaar(JsonParser.parseString("{\"success\":false}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class,()->MarketService.parseBazaar(JsonParser.parseString("{\"success\":true,\"products\":{}}").getAsJsonObject()));
    }
    @Test void sessionFileRoundtripIsCompleteAndStopped() {
        var clock=new AtomicLong(0);var s=new Session(clock::get);s.start();s.enterDungeon();clock.set(60_000);s.collectFragment(Giant.DIAMANTE,1);s.loot().addCoins(75_000);s.leaveDungeon("Warp",false);
        var store=new SessionStore(directory);store.saveSession(s);var restored=new Session(clock::get);store.loadSession(restored);
        assertEquals(s.snapshot(),restored.snapshot());assertFalse(restored.running());
    }
    @Test void corruptSessionRecoversBackupWithoutDeletingEvidence() throws Exception {
        var store=new SessionStore(directory);var s=new Session(()->0);s.start();store.saveSession(s);store.saveSession(s);
        Files.writeString(directory.resolve("session.json"),"{broken json");
        var restored=new Session(()->0);store.loadSession(restored);
        assertEquals(s.id(),restored.id());assertTrue(store.warning().contains("backup"));
        try(var paths=Files.list(directory)) { assertTrue(paths.anyMatch(p->p.getFileName().toString().contains(".corrupt-"))); }
    }
    @Test void settingsSurviveRestartAndClampOutOfRangeLayouts() {
        var c=new ModConfig();c.dashboard.scale=8;c.hud.x=0.75;c.showHud=false;c.bazaarTaxPercent=1.1;
        var store=new SessionStore(directory);store.saveConfig(c);var loaded=store.loadConfig();
        assertEquals(2.5,loaded.dashboard.scale);assertEquals(0.75,loaded.hud.x);assertFalse(loaded.showHud);assertEquals(1.1,loaded.bazaarTaxPercent);
    }
    @Test void cacheLoadsWithoutNetworkAndReportsItsAge() throws Exception {
        JsonFiles.write(directory.resolve("prices.json"),new MarketService.Prices(1700000000000L,Map.of("FRAG",1.0)));
        try(var market=new MarketService(directory,new ItemCatalog())) { assertEquals(1,market.prices().values().get("FRAG"));assertTrue(market.stale()); }
    }
    @Test void invalidSemanticSaveDoesNotPartiallyOverwriteSession() {
        var s=new Session(()->0);s.start();var original=s.snapshot();
        assertThrows(IllegalArgumentException.class,()->s.restore(new Session.Snapshot(1,"bad",-1,java.util.List.of(),original.loot())));
        assertEquals(original,s.snapshot());
    }
    @Test void newInstallDefaultsToExpectedFragmentIncome() {
        assertTrue(new SessionStore(directory).loadConfig().expectedFragmentProfit);
    }
    @Test void oldSettingsGainExpectedDefaultWithoutLosingOtherPreferences() throws Exception {
        Files.writeString(directory.resolve("settings.json"),"""
            {"schema":1,"hud":{"x":0.8,"y":0.2,"scale":1.3},"includeSalvage":false,"bazaarTaxPercent":1.1}
            """);
        var store=new SessionStore(directory);var config=store.loadConfig();
        assertTrue(config.expectedFragmentProfit);assertFalse(config.includeSalvage);
        assertEquals(0.8,config.hud.x);assertEquals(1.3,config.hud.scale);assertEquals(1.1,config.bazaarTaxPercent);
        assertTrue(store.warning().isEmpty());
    }
    @Test void selectingCollectedModeSurvivesReloadAndCanSwitchBack() {
        var store=new SessionStore(directory);var config=store.loadConfig();config.expectedFragmentProfit=false;store.saveConfig(config);
        var restored=store.loadConfig();assertFalse(restored.expectedFragmentProfit);
        restored.expectedFragmentProfit=true;store.saveConfig(restored);assertTrue(store.loadConfig().expectedFragmentProfit);
    }
}
