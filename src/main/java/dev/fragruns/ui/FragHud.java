package dev.fragruns.ui;

import dev.fragruns.FragRunsClient;
import dev.fragruns.core.Giant;
import dev.fragruns.core.HudSettings;
import dev.fragruns.core.LootLedger;
import dev.fragruns.core.Session;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import static dev.fragruns.core.HudSettings.*;

public final class FragHud {
    private FragHud() {}

    public static void render(GuiGraphicsExtractor g, FragRunsClient mod) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof DashboardScreen
                || mc.gui.screen() instanceof LayoutScreen) return;
        var config = mod.config();
        if (!config.showHud || (!mod.session().active() && !config.showStoppedHud)) return;
        var content = config.hudSections.content();
        if (content.blocks().isEmpty()) return;
        boolean prompt = mod.session().confirmingStop();
        var window = mc.getWindow();
        var bounds = config.hudSections.bounds(config.hud, window.getGuiScaledWidth(), window.getGuiScaledHeight(), prompt);
        g.pose().pushMatrix();
        g.pose().translate((float)bounds.x(), (float)bounds.y());
        g.pose().scale((float)bounds.scale(), (float)bounds.scale());
        draw(g, mod, content, prompt);
        g.pose().popMatrix();
    }

    static void draw(GuiGraphicsExtractor g, FragRunsClient mod, HudSettings.Content content, boolean prompt) {
        if (content.blocks().isEmpty()) return;
        var s = mod.session();
        var config = mod.config();
        var settings = config.hudSections;
        Theme.round(g, 0, 0, WIDTH, content.bodyHeight(), Theme.PANEL);
        boolean needsValue = settings.visible(Section.PROFIT_BREAKDOWN) || settings.visible(Section.TOTAL_PROFIT)
                || settings.visible(Section.HOURLY_PROFIT);
        var profit = needsValue ? s.loot().value(mod.market().prices().values(), config.bazaarTaxPercent, config.includeSalvage) : null;
        for (var block : content.blocks()) {
            int y = block.y();
            if (block.divider()) g.fill(10, y-7, WIDTH-10, y-6, Theme.LINE);
            switch (block.section()) {
                case HEADER -> {
                    g.fill(8, y, WIDTH-8, y+1, Theme.BLUE);
                    Theme.text(g, "FRAG RUNS", 10, y+7, Theme.BLUE);
                    String status = Boolean.getBoolean("fragruns.preview") ? "PREVIEW" : s.paused() ? "PAUSED" : s.running() ? "LIVE" : "SAVED";
                    Theme.right(g, status, WIDTH-10, y+7, s.paused() ? Theme.GOLD : s.running() ? Theme.GREEN : Theme.MUTED);
                }
                case SESSION_TIMER -> row(g, "Session", Theme.time(s.elapsedMs()), y, Theme.TEXT);
                case RUN_TIMER -> row(g, "Current run", Theme.time(s.runMs()), y, Theme.GOLD);
                case RUN_RECORDS -> {
                    row(g, "Fastest", Theme.time(s.fastest()), y, Theme.GREEN);
                    row(g, "Slowest", Theme.time(s.slowest()), y+ROW, Theme.TEXT);
                    row(g, "Last", Theme.time(s.last()), y+2*ROW, Theme.TEXT);
                    row(g, "Average", Theme.time(s.average()), y+3*ROW, Theme.TEXT);
                }
                case RUN_COUNTS -> {
                    row(g, "Total runs", Integer.toString(s.totalRuns()), y, Theme.TEXT);
                    int finished = s.completedRuns();
                    Theme.fit(g, finished+" finished • "+(s.totalRuns()-finished)+" early", 10, y+ROW, WIDTH-20, Theme.MUTED, 1);
                }
                case WATCHER_GIANTS -> giants(g, s, y);
                case PROFIT_BREAKDOWN -> breakdown(g, s.loot(), profit, settings.combinedFragments(), y);
                case TOTAL_PROFIT -> row(g, "Est. profit", Theme.coins(profit.total()), y, Theme.GOLD);
                case HOURLY_PROFIT -> row(g, config.expectedFragmentProfit ? "Coins / h (expected)" : "Coins / h (collected)",
                        Theme.coins(mod.hourlyProfit(profit)), y, Theme.GREEN);
            }
        }
        if (prompt) {
            int y = content.bodyHeight()+3;
            Theme.round(g, 0, y, WIDTH, 20, 0xF03F2932);
            Theme.fit(g, "Press "+mod.sessionKeyName()+" again to stop", 8, y+6, WIDTH-16, Theme.RED, 1);
        }
    }

    private static void giants(GuiGraphicsExtractor g, Session session, int y) {
        var stats = session.giantStats();
        for (Giant giant : Giant.values()) {
            var stat = stats.get(giant);
            Theme.text(g, giant == Giant.JOLLY_PINK ? "Jolly Pink" : giant.label, 10, y, giant.color);
            Theme.right(g, stat.encounters()+"  ("+Theme.percent(stat.frequency())+")", WIDTH-10, y, Theme.TEXT);
            Theme.fit(g, giant.fragmentName, 14, y+DROP_OFFSET, 132, Theme.MUTED, 1);
            Theme.right(g, stat.fragments()+" ("+Theme.percent(stat.dropRate())+")", WIDTH-10, y+DROP_OFFSET, Theme.MUTED);
            y += GIANT_ROW;
        }
    }

    private static void breakdown(GuiGraphicsExtractor g, LootLedger loot, LootLedger.Valuation profit, boolean combined, int y) {
        Theme.text(g, "PROFIT BREAKDOWN", 10, y, Theme.BLUE);
        y += FIRST_PROFIT_ROW;
        for (var fragment : FragmentProfitRow.rows(loot, profit, combined)) {
            row(g, fragment.label(), Theme.coins(fragment.coins()), y, fragment.color());
            y += ROW;
        }
        row(g, "All essence", Theme.coins(profit.essence()), y, Theme.TEXT);
        row(g, "Kill coins", Theme.coins(profit.coins()), y+ROW, Theme.TEXT);
        row(g, "Other items", Theme.coins(profit.otherItems()), y+2*ROW, Theme.TEXT);
    }

    private static void row(GuiGraphicsExtractor g, String label, String value, int y, int color) {
        int labelWidth = Math.max(1, WIDTH - 26 - Minecraft.getInstance().font.width(value));
        Theme.fit(g, label, 10, y, labelWidth, Theme.MUTED, 1);
        Theme.right(g, value, WIDTH-10, y, color);
    }
}
