package dev.fragruns;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fragruns.core.ModConfig;
import dev.fragruns.core.PressLatch;
import dev.fragruns.core.Session;
import dev.fragruns.data.ItemCatalog;
import dev.fragruns.data.MarketService;
import dev.fragruns.data.SessionStore;
import dev.fragruns.tracking.GameTracker;
import dev.fragruns.ui.DashboardScreen;
import dev.fragruns.ui.FragHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fragruns","session"));
        sessionKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fragruns.session",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F7,category));
        dashboardKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fragruns.dashboard",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F9,category));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.gui.screen()==null && !sessionKey.isDown()) sessionLatch.release();
            while(sessionKey.consumeClick()) if(client.player!=null && sessionLatch.press()) sessionKeyPressed();
            while(dashboardKey.consumeClick()) if(client.player!=null) openDashboard(false);
            tracker.tick(client);
            if(session.running() || client.gui.screen() instanceof DashboardScreen || client.gui.screen() instanceof dev.fragruns.ui.LayoutScreen) market.tick(config.priceRefreshSeconds);
            long now=System.nanoTime()/1_000_000;
            if(session.running() && now-savedAt>=10_000) { store.saveSession(session); savedAt=now; }
        });
        ClientReceiveMessageEvents.GAME.register((message,overlay)->tracker.chat(message.getString(),overlay));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ tracker.disconnected(); store.saveSession(session); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{
            if(session.running()) { tracker.finishSession(); session.stop(); }
            store.saveSession(session); store.saveConfig(config); market.close();
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
        if(!session.running()) { store.archive(session); tracker.reset(); }
        if(session.confirmingStop()) tracker.finishSession();
        var result=session.pressSessionKey();
        if(result==Session.KeyResult.STARTED) {
            modShowHud(); tracker.tick(Minecraft.getInstance());
            var client=Minecraft.getInstance();
            if(client.player!=null && (client.gui.screen() instanceof DashboardScreen || client.gui.screen() instanceof dev.fragruns.ui.LayoutScreen)) client.gui.setScreen(null);
            notifyPlayer("Frag run session started. "+dashboardKeyName()+" opens settings.");
        }
        if(result==Session.KeyResult.CONFIRM_STOP) notifyPlayer("Press "+sessionKey.getTranslatedKeyMessage().getString()+" again within 4 seconds to stop your session.");
        if(result==Session.KeyResult.STOPPED) { store.saveSession(session); store.archive(session); notifyPlayer("Frag run session saved."); }
    }
    private void modShowHud() { config.showHud=true; store.saveConfig(config); }
    private void notifyPlayer(String text) {
        var player=Minecraft.getInstance().player;
        if(player!=null) player.sendOverlayMessage(Component.literal(text));
    }
    public void openDashboard(boolean settings) { Minecraft.getInstance().gui.setScreen(new DashboardScreen(this,settings)); }
    public boolean handleScreenKey(net.minecraft.client.input.KeyEvent event) {
        if(sessionKey.matches(event)) { if(sessionLatch.press()) sessionKeyPressed(); return true; }
        if(dashboardKey.matches(event)) { Minecraft.getInstance().gui.setScreen(null); return true; }
        return false;
    }
    public void screenKeyReleased(net.minecraft.client.input.KeyEvent event) { if(sessionKey.matches(event)) sessionLatch.release(); }
    public void saveConfig() { store.saveConfig(config); }
    public Session session() { return session; }
    public ModConfig config() { return config; }
    public MarketService market() { return market; }
    public GameTracker tracker() { return tracker; }
    public SessionStore store() { return store; }
    public String sessionKeyName() { return sessionKey.getTranslatedKeyMessage().getString(); }
    public String dashboardKeyName() { return dashboardKey.getTranslatedKeyMessage().getString(); }
}
