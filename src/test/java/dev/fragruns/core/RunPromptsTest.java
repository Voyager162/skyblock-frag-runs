package dev.fragruns.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RunPromptsTest {
    final RunPrompts prompts=new RunPrompts();
    final UUID run=UUID.randomUUID();
    RunPromptsTest() { prompts.enter(run); }
    @Test void killOffersOnePromptAndOnlyExplicitClickProducesCommand() {
        assertNull(prompts.dungeon(run,false,true));
        var p=prompts.dungeon(run,true,true);
        assertEquals(RunPrompts.Kind.WARP,p.kind());
        for(int i=0;i<100;i++) assertNull(prompts.dungeon(run,true,true));
        assertEquals("warp dhub",prompts.click(p.token(),true,false,100));
        assertNull(prompts.click(p.token(),true,false,101));
    }
    @Test void returnOffersQueueWithoutPartyQueryOrMenuClick() {
        prompts.exit(run,true,0);
        assertNull(prompts.hub(true,false,100));
        var p=prompts.hub(true,true,1000);
        assertEquals(RunPrompts.Kind.QUEUE,p.kind());
        assertNull(prompts.hub(true,true,2000));
        assertEquals("joininstance CATACOMBS_FLOOR_SEVEN",prompts.click(p.token(),false,true,2001));
        assertNull(prompts.click(p.token(),false,true,2002));
    }
    @Test void wrongLocationAndUnrecognizedTokensCannotTravel() {
        var p=prompts.dungeon(run,true,true);
        assertNull(prompts.click("random",true,false,0));
        assertNull(prompts.click(p.token(),false,true,0));
        assertEquals("warp dhub",prompts.click(p.token(),true,false,0));
    }
    @Test void oldRunAndPausedLinksExpire() {
        var p=prompts.dungeon(run,true,true);
        prompts.enter(UUID.randomUUID());
        assertNull(prompts.click(p.token(),true,false,0));
        assertNull(prompts.dungeon(run,true,true));
        prompts.enter(run);p=prompts.dungeon(run,true,true);prompts.clear();
        assertNull(prompts.click(p.token(),true,false,0));
    }
    @Test void manualTravelInvalidatesLinkButStillAllowsNextHubPrompt() {
        var p=prompts.dungeon(run,true,true);prompts.manualTravel();
        assertNull(prompts.click(p.token(),true,false,0));
        prompts.exit(run,true,0);assertNotNull(prompts.hub(true,true,100));
    }
    @Test void queueExpiresOnTimeoutOrLeavingHub() {
        prompts.exit(run,true,0);var p=prompts.hub(true,true,100);
        assertNull(prompts.click(p.token(),false,true,120000));
        prompts.enter(run);prompts.exit(run,true,0);p=prompts.hub(true,true,100);
        prompts.hub(true,false,200);assertNull(prompts.click(p.token(),false,true,300));
    }
    @Test void disabledAndInterruptedVisitsCannotOfferPrompts() {
        assertNull(prompts.dungeon(run,true,false));
        var p=prompts.dungeon(run,true,true);prompts.dungeon(run,true,false);
        assertNull(prompts.click(p.token(),true,false,0));
        prompts.exit(run,false,0);assertNull(prompts.hub(true,true,100));
        prompts.enter(run);prompts.exit(UUID.randomUUID(),true,0);assertNull(prompts.hub(true,true,100));
    }
    @Test void oldAutomationSettingsCannotEnableAutomaticBehavior() {
        var gson=new Gson();
        var c=gson.fromJson("{\"warpOnGiantKill\":true,\"queueSoloF7\":true,\"warpDelayMs\":0,\"reportSeconds\":9}",ModConfig.class);
        c.sanitize();assertTrue(c.showWarpPrompt);assertTrue(c.showQueuePrompt);assertEquals(9,c.reportSeconds);
        c.showWarpPrompt=false;c.showQueuePrompt=false;
        var saved=gson.toJson(c);
        assertFalse(saved.contains("warpOnGiantKill"));assertFalse(saved.contains("warpDelayMs"));
        var loaded=gson.fromJson(saved,ModConfig.class);
        assertFalse(loaded.showWarpPrompt);assertFalse(loaded.showQueuePrompt);
    }
}
