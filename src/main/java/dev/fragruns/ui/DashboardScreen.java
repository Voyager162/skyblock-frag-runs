package dev.fragruns.ui;

import dev.fragruns.FragRunsClient;
import dev.fragruns.core.Giant;
import dev.fragruns.core.Layout;
import dev.fragruns.core.LootLedger;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DashboardScreen extends Screen {
    public static final int WIDTH=550,HEIGHT=380;
    private final FragRunsClient mod;
    private final Screen parent;
    private int tab,page,focus=-1;
    private final List<Control> controls=new ArrayList<>();
    private double mouseX,mouseY;
    private String tooltip;
    private record Control(int x,int y,int w,int h,String label,Runnable action) {
        boolean hit(double px,double py) { return px>=x && py>=y && px<x+w && py<y+h; }
    }
    public DashboardScreen(FragRunsClient mod,boolean settings) { this(mod,settings,null); }
    public DashboardScreen(FragRunsClient mod,boolean settings,Screen parent) { super(Component.literal("Skyblock Frag Runs")); this.mod=mod; this.tab=settings?3:0; this.parent=parent; }
    public Layout.Bounds bounds() { return mod.config().dashboard.fit(width,height,WIDTH,HEIGHT); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mx,int my,float delta) { /* Keep the game visible without forcing blur. */ }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0x70090C15);
        var b=bounds();
        mouseX=b.localX(mx); mouseY=b.localY(my); tooltip=null; controls.clear();
        g.pose().pushMatrix();g.pose().translate((float)b.x(),(float)b.y());g.pose().scale((float)b.scale(),(float)b.scale());
        draw(g,true);
        g.pose().popMatrix();
        if(tooltip!=null) g.setTooltipForNextFrame(font,Component.literal(tooltip),mx,my);
    }
    public void preview(GuiGraphicsExtractor g) { controls.clear(); mouseX=-100; mouseY=-100; draw(g,false); }
    private void draw(GuiGraphicsExtractor g,boolean interactive) {
        var s=mod.session();
        Theme.round(g,2,3,WIDTH,HEIGHT,0x60000000);
        Theme.round(g,0,0,WIDTH,HEIGHT,Theme.PANEL);
        Theme.fit(g,"Skyblock Frag Runs",14,13,310,Theme.BLUE,1.5f);
        Theme.text(g,"FLOOR VII  /  SESSION TRACKER",15,34,Theme.MUTED);
        String badge=Boolean.getBoolean("fragruns.preview") ? "PREVIEW DATA" : s.running() ? "SESSION LIVE" : s.elapsedMs()>0 ? "SESSION SAVED" : "READY TO RUN";
        Theme.right(g,badge,WIDTH-15,16,s.running()?Theme.GREEN:Theme.MUTED);
        Theme.right(g,"v1.0.0  •  Fabric 26.2",WIDTH-15,32,Theme.MUTED);
        String[] tabs={"Overview","Loot & profit","Run history","Settings"};
        for(int i=0;i<tabs.length;i++) {
            final int target=i;
            button(g,14+i*132,51,126,20,tabs[i],tab==i?Theme.BLUE:Theme.MUTED,()->{tab=target;page=0;focus=-1;},"",tab==i);
        }
        switch(tab) { case 0->overview(g); case 1->loot(g); case 2->history(g); default->settings(g); }
        g.fill(14,326,WIDTH-14,327,Theme.LINE);
        Theme.fit(g,"Session: "+mod.sessionKeyName()+" • Settings: "+mod.dashboardKeyName(),15,334,355,Theme.MUTED,1);
        Theme.right(g,String.format(Locale.ROOT,"%.2f%% tax",mod.config().bazaarTaxPercent),WIDTH-15,334,Theme.MUTED);
        String action=!s.running()?"Start session":s.confirmingStop()?"Press again to stop":"Stop session";
        button(g,14,352,142,20,action,s.confirmingStop()?Theme.RED:s.running()?Theme.GOLD:Theme.GREEN,mod::sessionKeyPressed,
                "Start: "+mod.sessionKeyName()+". Stopping needs two separate presses within 4 seconds.",false);
        button(g,164,352,130,20,"Edit HUD",Theme.BLUE,()->minecraft.gui.setScreen(new LayoutScreen(mod,true,this)),"Drag and resize the in-game overlay.",false);
        button(g,420,352,116,20,"Close",Theme.MUTED,this::onClose,"Esc closes this menu; the session keeps running.",false);
        if(s.confirmingStop()) {
            Theme.round(g,156,297,380,23,0xFF49303C);
            Theme.fit(g,"Press "+mod.sessionKeyName()+" again to stop • "+String.format(Locale.ROOT,"%.1fs",s.confirmRemainingMs()/1000.0),164,305,363,Theme.RED,1);
        }
        if(!interactive) controls.clear();
    }
    private void overview(GuiGraphicsExtractor g) {
        var s=mod.session(); var value=valuation();
        metric(g,14,"SESSION",Theme.time(s.elapsedMs()),Theme.TEXT,s.running()?"Running":"Session saved");
        metric(g,146,"CURRENT RUN",Theme.time(s.runMs()),Theme.GOLD,s.inDungeon()?"Enter to exit":"Waiting for F7");
        metric(g,278,"EST. PROFIT",Theme.coins(value.total()),Theme.GOLD,"After sell tax");
        metric(g,410,"COINS / HOUR",Theme.coins(s.profitPerHour(value.total())),Theme.GREEN,"Full session time");
        Theme.panel(g,14,149,190,169);
        Theme.text(g,"RUN TIMES",25,161,Theme.BLUE);
        stat(g,"Fastest",Theme.time(s.fastest()),183,Theme.GREEN);
        stat(g,"Slowest",Theme.time(s.slowest()),202,Theme.TEXT);
        stat(g,"Last run",Theme.time(s.last()),221,Theme.TEXT);
        stat(g,"Average",Theme.time(s.average()),240,Theme.TEXT);
        g.fill(25,258,193,259,Theme.LINE);
        stat(g,"Total runs",Integer.toString(s.totalRuns()),268,Theme.TEXT);
        Theme.fit(g,s.completedRuns()+" finished • "+(s.totalRuns()-s.completedRuns())+" early / unverified",25,289,168,Theme.MUTED,0.85f);
        Theme.panel(g,214,149,322,169);
        Theme.text(g,"WATCHER GIANTS",226,161,Theme.BLUE);
        Theme.right(g,"SEEN  /  SHARE",524,161,Theme.MUTED);
        int y=181;
        for(Giant giant:Giant.values()) {
            var stat=s.giantStats().get(giant);
            Theme.text(g,giant.label,226,y,giant.color);
            Theme.right(g,stat.encounters()+"  ("+Theme.percent(stat.frequency())+")",524,y,Theme.TEXT);
            Theme.fit(g,giant.fragmentName,234,y+13,190,Theme.MUTED,1);
            Theme.fit(g,stat.fragments()+"  ("+Theme.percent(stat.dropRate())+" drop)",432,y+13,92,Theme.MUTED,1);
            y+=33;
        }
        if(mouseX>=214&&mouseX<=536&&mouseY>=149&&mouseY<=318) tooltip="Giant stats commit when you leave after a confirmed giant kill. Early exits are excluded. Drop rate is runs with a fragment / finished encounters of that giant.";
        if(mouseX>=14&&mouseX<=204&&mouseY>=149&&mouseY<=318) tooltip="Times and total runs include every exit, including early resets. A live run is added when you leave Floor VII.";
    }
    private void metric(GuiGraphicsExtractor g,int x,String label,String value,int color,String caption) {
        Theme.panel(g,x,82,126,57); Theme.text(g,label,x+9,91,Theme.MUTED);
        Theme.fit(g,value,x+9,106,108,color,1.6f);
        Theme.fit(g,caption,x+9,126,108,Theme.MUTED,1);
    }
    private void stat(GuiGraphicsExtractor g,String label,String value,int y,int color) { Theme.text(g,label,25,y,Theme.MUTED); Theme.right(g,value,193,y,color); }
    private LootLedger.Valuation valuation() { return mod.session().loot().value(mod.market().prices().values(),mod.config().bazaarTaxPercent,mod.config().includeSalvage); }
    private record LootRow(String name,String detail,long quantity,double value) {}
    private void loot(GuiGraphicsExtractor g) {
        var value=valuation();
        Theme.panel(g,14,82,214,236);
        Theme.text(g,"PROFIT BREAKDOWN",24,94,Theme.BLUE);
        int y=117;
        for(Giant giant:Giant.values()) {
            profitRow(g,giant.fragmentName,value.fragmentValues().get(giant),y,giant.color);
            y+=22;
        }
        profitRow(g,"All essence",value.essence(),205,Theme.TEXT);
        profitRow(g,"Kill coins",value.coins(),227,Theme.TEXT);
        profitRow(g,"Other items",value.otherItems(),249,Theme.TEXT);
        g.fill(24,271,216,272,Theme.LINE);
        profitRow(g,"Est. profit",value.total(),282,Theme.GOLD);
        Theme.text(g,"After sell tax",24,301,Theme.MUTED);
        if(mouseX>=14&&mouseX<=228&&mouseY>=82&&mouseY<=318) tooltip="All essence includes found essence and estimated gear salvage. Other items includes Spirit Leaps, Bonzo/Livid/dragon fragments and remaining drops. Kill coins uses net F7 purse gains, excluding observed transactions.";
        Theme.panel(g,238,82,298,236);
        Theme.text(g,"COLLECTED LOOT",248,94,Theme.BLUE);
        Theme.right(g,"QTY       VALUE",524,110,Theme.MUTED);
        List<LootRow> rows=new ArrayList<>();
        for(var lot:mod.session().loot().entries().entrySet()) {
            var e=lot.getValue();
            String detail=mod.config().includeSalvage&&e.essencePerItem()>0 ? e.essencePerItem()+" "+e.essenceId().replace("ESSENCE_","").toLowerCase(Locale.ROOT)+" essence each" : Giant.fromItem(e.itemId())!=null ? "Giant fragment" : "Other items";
            rows.add(new LootRow(e.name(),detail,e.quantity(),value.lineValues().getOrDefault(lot.getKey(),0.0)));
        }
        for(var e:mod.session().loot().essence().entrySet()) rows.add(new LootRow(e.getKey().replace("ESSENCE_","")+" essence","Found in dungeon",e.getValue(),value.lineValues().getOrDefault(e.getKey(),0.0)));
        rows.sort(Comparator.comparingDouble(LootRow::value).reversed().thenComparing(LootRow::name));
        int pages=Math.max(1,(rows.size()+4)/5);page=Math.clamp(page,0,pages-1);
        for(int i=page*5;i<Math.min(rows.size(),page*5+5);i++) {
            int rowY=129+(i%5)*32; var row=rows.get(i);
            if(i%2==0) g.fill(244,rowY-4,530,rowY+24,0x552F3546);
            Theme.fit(g,row.name,248,rowY,173,Theme.TEXT,1);
            Theme.fit(g,row.detail,248,rowY+12,173,Theme.MUTED,1);
            Theme.right(g,Long.toString(row.quantity),451,rowY+4,Theme.TEXT);
            Theme.right(g,row.value>0?Theme.coins(row.value):"Unpriced",524,rowY+4,row.value>0?Theme.GOLD:Theme.MUTED);
        }
        if(rows.isEmpty()) { Theme.centered(g,"Your drops will appear here.",387,187,Theme.MUTED);Theme.centered(g,"Ready for your first run.",387,207,Theme.MUTED); }
        Theme.fit(g,value.unpriced()>0?value.unpriced()+" unpriced lots":"All collected loot",248,302,150,Theme.MUTED,1);
        pager(g,pages);
    }
    private void profitRow(GuiGraphicsExtractor g,String label,double value,int y,int color) {
        Theme.fit(g,label,24,y,133,Theme.MUTED,1);
        Theme.right(g,Theme.coins(value),216,y,color);
    }
    private void history(GuiGraphicsExtractor g) {
        var runs=mod.session().runs();
        Theme.panel(g,14,82,522,236);
        Theme.text(g,"#",25,95,Theme.MUTED);Theme.text(g,"RUN TIME",60,95,Theme.MUTED);Theme.text(g,"GIANT",163,95,Theme.MUTED);Theme.text(g,"FRAGMENTS",321,95,Theme.MUTED);Theme.text(g,"RESULT",430,95,Theme.MUTED);
        int pages=Math.max(1,(runs.size()+7)/8);page=Math.clamp(page,0,pages-1);
        for(int row=0;row<8;row++) {
            int index=runs.size()-1-page*8-row;if(index<0) break;
            var run=runs.get(index);int y=117+row*21;
            if(row%2==0) g.fill(21,y-4,529,y+14,0x552F3546);
            Theme.text(g,Integer.toString(index+1),25,y,Theme.MUTED);Theme.text(g,Theme.time(run.milliseconds()),60,y,Theme.TEXT);
            Theme.fit(g,run.giant()==null?"Not observed":run.giant().label,163,y,150,run.giant()==null?Theme.MUTED:run.giant().color,1);
            Theme.text(g,Integer.toString(run.fragments()),340,y,Theme.TEXT);Theme.fit(g,run.completed()?"Finished":"Early / partial",430,y,98,run.completed()?Theme.GREEN:Theme.MUTED,0.9f);
        }
        if(runs.isEmpty()) { Theme.centered(g,"A fresh session. Your first run starts in Floor VII.",WIDTH/2,183,Theme.MUTED);Theme.centered(g,"Run timing stops when you leave the dungeon.",WIDTH/2,204,Theme.MUTED); }
        Theme.fit(g,"Newest first • sessions are saved locally",25,301,340,Theme.MUTED,0.85f);pager(g,pages);
    }
    private void pager(GuiGraphicsExtractor g,int pages) {
        button(g,406,295,24,20,"<",Theme.BLUE,()->page=Math.max(0,page-1),"Previous page",false);
        Theme.centered(g,(page+1)+" / "+pages,468,302,Theme.MUTED);
        button(g,506,295,24,20,">",Theme.BLUE,()->page=Math.min(pages-1,page+1),"Next page",false);
    }
    private void settings(GuiGraphicsExtractor g) {
        Theme.panel(g,14,82,254,114);Theme.panel(g,278,82,258,114);
        Theme.text(g,"LAYOUT & APPEARANCE",25,94,Theme.BLUE);
        Theme.fit(g,"Drag to position. Scroll to change scale.",25,112,232,Theme.MUTED,0.85f);
        button(g,25,133,232,22,"Edit dashboard layout",Theme.BLUE,()->minecraft.gui.setScreen(new LayoutScreen(mod,false,this)),"Move and resize this menu independently from the HUD.",false);
        button(g,25,163,232,22,"Edit in-game HUD layout",Theme.BLUE,()->minecraft.gui.setScreen(new LayoutScreen(mod,true,this)),"Move and resize the overlay you see while playing.",false);
        Theme.text(g,"DISPLAY",289,94,Theme.BLUE);
        button(g,289,114,236,22,"HUD  "+(mod.config().showHud?"ON":"OFF"),mod.config().showHud?Theme.GREEN:Theme.MUTED,()->{mod.config().showHud=!mod.config().showHud;mod.saveConfig();},"Show the compact overlay while the session runs.",false);
        button(g,289,144,236,22,"Show saved HUD  "+(mod.config().showStoppedHud?"ON":"OFF"),Theme.BLUE,()->{mod.config().showStoppedHud=!mod.config().showStoppedHud;mod.saveConfig();},"Keep the last session visible after stopping.",false);
        Theme.fit(g,"Font follows your active resource pack.",289,177,236,Theme.MUTED,0.8f);
        Theme.panel(g,14,206,522,112);Theme.text(g,"PROFIT & CONTROLS",25,217,Theme.BLUE);
        button(g,25,236,238,22,"Include salvage  "+(mod.config().includeSalvage?"ON":"OFF"),Theme.BLUE,()->{mod.config().includeSalvage=!mod.config().includeSalvage;mod.saveConfig();},"Value eligible mob gear as essence. Turning this off uses item Bazaar or NPC value. No items are automatically salvaged.",false);
        button(g,279,236,28,22,"-",Theme.BLUE,()->{mod.config().bazaarTaxPercent=Math.max(0,mod.config().bazaarTaxPercent-0.05);mod.saveConfig();},"Decrease sell tax by 0.05%",false);
        Theme.centered(g,String.format(Locale.ROOT,"Tax: %.2f%%",mod.config().bazaarTaxPercent),402,243,Theme.TEXT);
        button(g,497,236,28,22,"+",Theme.BLUE,()->{mod.config().bazaarTaxPercent=Math.min(10,mod.config().bazaarTaxPercent+0.05);mod.saveConfig();},"Increase sell tax by 0.05%",false);
        Theme.fit(g,"Session key: "+mod.sessionKeyName()+" • Rebind in Options > Controls > Key Binds",25,270,500,Theme.MUTED,0.9f);
        Theme.fit(g,"Open settings: "+mod.dashboardKeyName()+" or /fragruns settings",25,285,500,Theme.MUTED,0.85f);
        Theme.fit(g,mod.store().warning().isEmpty()?"Two presses within 4 seconds to stop. Esc cancels the confirmation.":mod.store().warning(),25,301,500,mod.store().warning().isEmpty()?Theme.MUTED:Theme.RED,0.85f);
    }
    private void button(GuiGraphicsExtractor g,int x,int y,int w,int h,String label,int color,Runnable action,String tip,boolean selected) {
        int index=controls.size();Control c=new Control(x,y,w,h,label,action);controls.add(c);
        boolean hover=c.hit(mouseX,mouseY),focused=focus==index;
        Theme.round(g,x,y,w,h,selected?0xFF354853:hover||focused?0xFF384153:0xFF282E3E);
        if(selected||focused) g.fill(x+4,y+h-2,x+w-4,y+h-1,color);
        Theme.centered(g,label,x+w/2,y+(h-8)/2,color);
        if(hover&&!tip.isEmpty()) tooltip=tip;
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick) {
        if(e.button()!=0) return super.mouseClicked(e,doubleClick);
        var b=bounds();double x=b.localX(e.x()),y=b.localY(e.y());
        for(Control control:List.copyOf(controls)) if(control.hit(x,y)) { control.action.run();return true; }
        return super.mouseClicked(e,doubleClick);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(tab==1||tab==2) { page=Math.max(0,page+(vertical<0?1:-1));return true; }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if(mod.handleScreenKey(e)) return true;
        if(e.key()==GLFW.GLFW_KEY_ESCAPE && mod.session().confirmingStop()) { mod.session().cancelConfirmation();return true; }
        if(e.key()==GLFW.GLFW_KEY_TAB) { focus=Math.floorMod(focus+((e.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),Math.max(1,controls.size())); return true; }
        if((e.key()==GLFW.GLFW_KEY_ENTER||e.key()==GLFW.GLFW_KEY_SPACE)&&focus>=0&&focus<controls.size()) { controls.get(focus).action.run();return true; }
        return super.keyPressed(e);
    }
    @Override public boolean keyReleased(KeyEvent e) { mod.screenKeyReleased(e);return super.keyReleased(e); }
    @Override public void onClose() { mod.saveConfig(); minecraft.gui.setScreen(parent); }
}
