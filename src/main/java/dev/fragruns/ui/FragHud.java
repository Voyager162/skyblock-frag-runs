package dev.fragruns.ui;

import dev.fragruns.FragRunsClient;
import dev.fragruns.core.Giant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FragHud {
    public static final int WIDTH=240,HEIGHT=452;
    private static final int BODY_HEIGHT=428;
    private FragHud() {}
    public static void render(GuiGraphicsExtractor g,FragRunsClient mod) {
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof DashboardScreen || mc.gui.screen() instanceof LayoutScreen) return;
        if(!mod.config().showHud || !mod.session().running() && !mod.config().showStoppedHud) return;
        var b=mod.config().hud.fit(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),WIDTH,HEIGHT);
        g.pose().pushMatrix(); g.pose().translate((float)b.x(),(float)b.y()); g.pose().scale((float)b.scale(),(float)b.scale());
        draw(g,mod); g.pose().popMatrix();
    }
    public static void draw(GuiGraphicsExtractor g,FragRunsClient mod) {
        var s=mod.session();
        var profit=s.loot().value(mod.market().prices().values(),mod.config().bazaarTaxPercent,mod.config().includeSalvage);
        Theme.round(g,0,0,WIDTH,BODY_HEIGHT,Theme.PANEL);
        g.fill(8,6,WIDTH-8,7,Theme.BLUE);
        Theme.text(g,"FRAG RUNS",10,13,Theme.BLUE);
        Theme.right(g,Boolean.getBoolean("fragruns.preview")?"PREVIEW":s.running()?"LIVE":"SAVED",WIDTH-10,13,s.running()?Theme.GREEN:Theme.MUTED);
        row(g,"Session",Theme.time(s.elapsedMs()),31,Theme.TEXT);
        row(g,"Current run",Theme.time(s.runMs()),46,Theme.GOLD);
        g.fill(10,61,WIDTH-10,62,Theme.LINE);
        row(g,"Fastest",Theme.time(s.fastest()),69,Theme.GREEN);
        row(g,"Slowest",Theme.time(s.slowest()),83,Theme.TEXT);
        row(g,"Last",Theme.time(s.last()),97,Theme.TEXT);
        row(g,"Average",Theme.time(s.average()),111,Theme.TEXT);
        row(g,"Total runs",Integer.toString(s.totalRuns()),127,Theme.TEXT);
        Theme.fit(g,s.completedRuns()+" finished • "+(s.totalRuns()-s.completedRuns())+" early",10,141,WIDTH-20,Theme.MUTED,1);
        g.fill(10,154,WIDTH-10,155,Theme.LINE);
        int y=163;
        for(Giant giant:Giant.values()) {
            var stat=s.giantStats().get(giant);
            Theme.text(g,giant==Giant.JOLLY_PINK ? "Jolly Pink" : giant.label,10,y,giant.color);
            Theme.right(g,stat.encounters()+"  ("+Theme.percent(stat.frequency())+")",WIDTH-10,y,Theme.TEXT);
            Theme.fit(g,giant.fragmentName,14,y+12,132,Theme.MUTED,1);
            Theme.right(g,stat.fragments()+" ("+Theme.percent(stat.dropRate())+")",WIDTH-10,y+12,Theme.MUTED);
            y+=27;
        }
        g.fill(10,271,WIDTH-10,272,Theme.LINE);
        Theme.text(g,"PROFIT BREAKDOWN",10,280,Theme.BLUE);
        y=297;
        for(Giant giant:Giant.values()) {
            row(g,giant.fragmentName,Theme.coins(profit.fragmentValues().get(giant)),y,giant.color);
            y+=14;
        }
        row(g,"All essence",Theme.coins(profit.essence()),353,Theme.TEXT);
        row(g,"Kill coins",Theme.coins(profit.coins()),367,Theme.TEXT);
        row(g,"Other items",Theme.coins(profit.otherItems()),381,Theme.TEXT);
        g.fill(10,394,WIDTH-10,395,Theme.LINE);
        row(g,"Est. profit",Theme.coins(profit.total()),402,Theme.GOLD);
        row(g,mod.config().expectedFragmentProfit?"Coins / h (expected)":"Coins / h (collected)",Theme.coins(mod.hourlyProfit(profit)),416,Theme.GREEN);
        if(s.confirmingStop()) {
            Theme.round(g,0,BODY_HEIGHT+3,WIDTH,20,0xF03F2932);
            Theme.fit(g,"Press "+mod.sessionKeyName()+" again to stop",8,BODY_HEIGHT+9,WIDTH-16,Theme.RED,1);
        }
    }
    private static void row(GuiGraphicsExtractor g,String label,String value,int y,int color) { Theme.text(g,label,10,y,Theme.MUTED); Theme.right(g,value,WIDTH-10,y,color); }
}
