package dev.fragruns;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fragruns.core.ModConfig;
import dev.fragruns.core.HourlyProfit;
import dev.fragruns.core.LootLedger;
import dev.fragruns.core.PressLatch;
import dev.fragruns.core.Session;
import dev.fragruns.data.ItemCatalog;
import dev.fragruns.data.MarketService;
import dev.fragruns.data.SessionStore;
import dev.fragruns.tracking.GameTracker;
import dev.fragruns.tracking.RunMapRecorder;
import dev.fragruns.ui.DashboardScreen;
import dev.fragruns.ui.FragHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class FragRunsClient implements ClientModInitializer {
    public static FragRunsClient INSTANCE;
    private final Session session=Session.live();
    private final ItemCatalog catalog=new ItemCatalog();
    private SessionStore store;
    private ModConfig config;
    private MarketService market;
    private GameTracker tracker;
    private RunMapRecorder maps;
    private KeyMapping sessionKey,dashboardKey;
    private final PressLatch sessionLatch=new PressLatch();
    private boolean previewOpened;
    private long savedAt;

    @Override public void onInitializeClient() {
        INSTANCE=this;
        store=new SessionStore(FabricLoader.getInstance().getConfigDir().resolve("fragruns"));
        config=store.loadConfig(); store.loadSession(session);
        market=new MarketService(store.directory(),catalog);
        tracker=new GameTracker(session,catalog);
        maps=new RunMapRecorder(session,store.maps());
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fragruns","session"));
        sessionKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fragruns.session",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F7,category));
        dashboardKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fragruns.dashboard",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F9,category));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            // Keyboard presses arrive directly. Vanilla enqueues one click per mouse press for mouse bindings.
            while(sessionKey.consumeClick()) if(client.player!=null && KeyMappingHelper.getBoundKeyOf(sessionKey).getType()==InputConstants.Type.MOUSE) sessionKeyPressed();
            while(dashboardKey.consumeClick()) if(client.player!=null) openDashboard(false);
            tracker.tick(client);
            maps.tick(client,tracker.inBossPhase());
            if(session.active() || config.showStoppedHud && !session.id().isEmpty() || client.gui.screen() instanceof DashboardScreen || client.gui.screen() instanceof dev.fragruns.ui.LayoutScreen) market.tick(config.priceRefreshSeconds);
            long now=System.nanoTime()/1_000_000;
            if(session.running() && now-savedAt>=10_000) { store.saveSession(session); savedAt=now; }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ tracker.disconnected(handler.getConnection()); store.saveSession(session); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{
            if(session.running()) { tracker.finishSession(); session.stop(); }
            store.saveSession(session); store.saveConfig(config); maps.close(); market.close();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("fragruns")
                .executes(ctx->{ Minecraft.getInstance().execute(()->openDashboard(false)); return 1; })
                .then(literal("settings").executes(ctx->{ Minecraft.getInstance().execute(()->openDashboard(true)); return 1; }))
                .then(literal("session").executes(ctx->{ Minecraft.getInstance().execute(this::sessionKeyPressed); return 1; }))));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("fragruns","hud"),
                (graphics,delta)->FragHud.render(graphics,this));
        // A separate development preview has no world and never changes the real session save.
        if(Boolean.getBoolean("fragruns.preview")) ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!previewOpened && client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
                previewOpened=true;
                client.gui.setScreen(new dev.fragruns.ui.LayoutScreen(this,true,new DashboardScreen(this,true)));
            }
        });
    }
    public void sessionKeyPressed() {
        if(!session.active()) {
            try { store.archiveChecked(session); }
            catch (java.io.IOException e) { notifyPlayer("Could not save the previous session. Check file access and retry."); return; }
            tracker.reset();
        }
        if(session.running()) tracker.finishSession();
        var result=session.pressSessionKey();
        if(result==Session.KeyResult.STARTED) {
            modShowHud(); tracker.tick(Minecraft.getInstance());
            var client=Minecraft.getInstance();
            if(client.player!=null && (client.gui.screen() instanceof DashboardScreen || client.gui.screen() instanceof dev.fragruns.ui.LayoutScreen)) client.gui.setScreen(null);
            notifyPlayer("Frag run session started. "+dashboardKeyName()+" opens settings.");
        }
        if(result==Session.KeyResult.PAUSED || result==Session.KeyResult.RESUMED) {
            tracker.resetTrackingBaselines();
            if (result == Session.KeyResult.RESUMED) tracker.tick(Minecraft.getInstance());
            store.saveSession(session);
            String action = result == Session.KeyResult.PAUSED ? "Paused" : "Resumed";
            notifyPlayer(action + ". Press " + sessionKeyName() + " again within 1.5s to end the session.");
        }
        if(result==Session.KeyResult.STOPPED) { store.saveSession(session); store.archive(session); notifyPlayer("Frag run session saved."); }
    }
    private void modShowHud() { config.showHud=true; store.saveConfig(config); }
    public void continueSession(String id) throws java.io.IOException {
        boolean same=session.id().equals(id);
        boolean alreadyRunning=same && session.running();
        if (!same && session.running()) tracker.finishSession();
        store.continueSession(session,id);
        if (!alreadyRunning) {
            if (same && session.inDungeon()) tracker.resetTrackingBaselines(); else tracker.reset();
            modShowHud();
            tracker.tick(Minecraft.getInstance());
            notifyPlayer("Session continued.");
        }
        Minecraft.getInstance().gui.setScreen(null);
    }
    private void notifyPlayer(String text) {
        var player=Minecraft.getInstance().player;
        if(player!=null) player.sendOverlayMessage(Component.literal(text));
    }
    public void openDashboard(boolean settings) { Minecraft.getInstance().gui.setScreen(new DashboardScreen(this,settings)); }
    public boolean keyboardInput(net.minecraft.client.input.KeyEvent event, int action) {
        if (sessionKey == null || !sessionKey.matches(event)) return false;
        if (action == GLFW.GLFW_RELEASE) sessionLatch.release();
        var client = Minecraft.getInstance();
        var screen = client.gui.screen();
        boolean dashboard = screen instanceof DashboardScreen || screen instanceof dev.fragruns.ui.LayoutScreen;
        if (client.player == null || screen != null && !dashboard) return false;
        if (action == GLFW.GLFW_PRESS && sessionLatch.press()) sessionKeyPressed();
        return dashboard;
    }
    public boolean handleScreenKey(net.minecraft.client.input.KeyEvent event) {
        if(sessionKey.matches(event)) return true;
        if(dashboardKey.matches(event)) { Minecraft.getInstance().gui.setScreen(null); return true; }
        return false;
    }
    public void screenKeyReleased(net.minecraft.client.input.KeyEvent event) { if(sessionKey.matches(event)) sessionLatch.release(); }
    public void saveConfig() { store.saveConfig(config); }
    public double hourlyProfit(LootLedger.Valuation collected) {
        return hourlyProfit(session,collected);
    }
    public double hourlyProfit(Session selected,LootLedger.Valuation collected) {
        return HourlyProfit.calculate(selected,collected,market.prices().values(),id->{
            var item=catalog.get(id);return item==null?0:item.npcPrice();
        },config);
    }
    public String version() { return FabricLoader.getInstance().getModContainer("fragruns").orElseThrow().getMetadata().getVersion().getFriendlyString(); }
    public Session session() { return session; }
    public ModConfig config() { return config; }
    public MarketService market() { return market; }
    public GameTracker tracker() { return tracker; }
    public RunMapRecorder maps() { return maps; }
    public SessionStore store() { return store; }
    public String sessionKeyName() { return sessionKey.getTranslatedKeyMessage().getString(); }
    public String dashboardKeyName() { return dashboardKey.getTranslatedKeyMessage().getString(); }
}
