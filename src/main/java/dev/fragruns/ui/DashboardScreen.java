package dev.fragruns.ui;

import dev.fragruns.FragRunsClient;
import dev.fragruns.core.Giant;
import dev.fragruns.core.HudSettings.Section;
import dev.fragruns.core.Layout;
import dev.fragruns.core.ModConfig;
import dev.fragruns.core.LootLedger;
import dev.fragruns.core.Session;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
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
    private int tab,focus=-1;
    int page;
    private final SessionLog log;
    private final List<Control> controls=new ArrayList<>();
    private double mouseX,mouseY;
    private String tooltip;
    private String historyNotice = "";
    private enum SettingsView { GENERAL, HUD, RUNS }
    private SettingsView settingsView=SettingsView.GENERAL;
    private record Control(int x,int y,int w,int h,String label,Runnable action) {
        boolean hit(double px,double py) { return px>=x && py>=y && px<x+w && py<y+h; }
    }
    public DashboardScreen(FragRunsClient mod,boolean settings) { this(mod,settings,null); }
    public DashboardScreen(FragRunsClient mod,boolean settings,Screen parent) { super(Component.literal("Skyblock Frag Runs")); this.mod=mod; this.tab=settings?3:0; this.parent=parent; log=new SessionLog(this,mod); }
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
        Theme.fit(g,tab==2?log.breadcrumb():"FLOOR VII  /  SESSION TRACKER",15,34,345,Theme.MUTED,1);
        String badge=Boolean.getBoolean("fragruns.preview") ? "PREVIEW DATA" : s.paused() ? "SESSION PAUSED" : s.running() ? "SESSION LIVE" : s.elapsedMs()>0 ? "SESSION SAVED" : "READY TO RUN";
        boolean savedView=tab==2 && log.viewingArchive();
        Theme.right(g,savedView?"SESSION ARCHIVE":badge,WIDTH-15,16,savedView?Theme.BLUE:s.paused()?Theme.GOLD:s.running()?Theme.GREEN:Theme.MUTED);
        Theme.right(g,"v"+mod.version()+"  •  Fabric 26.2",WIDTH-15,32,Theme.MUTED);
        if(tab==2 && log.inSession()) log.navigation(g);
        else {
            String[] tabs={"Overview","Loot & profit","Session log","Settings"};
            for(int i=0;i<tabs.length;i++) {
                final int target=i;
                button(g,14+i*132,51,126,20,tabs[i],tab==i?Theme.BLUE:Theme.MUTED,()->{tab=target;resetPage();settingsView=SettingsView.GENERAL;if(tab==2) log.open();},"",tab==i);
            }
        }
        switch(tab) { case 0->overview(g,s); case 1->loot(g,s.loot()); case 2->log.draw(g,historyNotice); default->settings(g); }
        g.fill(14,326,WIDTH-14,327,Theme.LINE);
        Theme.fit(g,tab==2 && log.inSession()?log.footer():"Session: "+mod.sessionKeyName()+" • Settings: "+mod.dashboardKeyName(),15,334,411,Theme.MUTED,1);
        Theme.right(g,String.format(Locale.ROOT,"%.2f%% tax",mod.config().bazaarTaxPercent),WIDTH-15,334,Theme.MUTED);
        String action=!s.active()?"Start session":s.confirmingStop()?"Press again to end":s.paused()?"Resume session":"Pause session";
        if(tab==2 && log.inSession()) log.continueButton(g);
        else {
            button(g,14,352,142,20,action,s.confirmingStop()?Theme.RED:s.running()?Theme.GOLD:Theme.GREEN,mod::sessionKeyPressed,
                    "One press starts, pauses or resumes. Two presses within 1.5 seconds end an active session.",false);
            button(g,164,352,130,20,"Edit HUD",Theme.BLUE,()->minecraft.gui.setScreen(new LayoutScreen(mod,true,this)),"Drag and resize the in-game overlay you see during play.",false);
        }
        button(g,420,352,116,20,"Close",Theme.MUTED,this::onClose,"Esc closes this menu; the session keeps running.",false);
        if(s.confirmingStop()) {
            Theme.round(g,156,297,380,23,0xFF49303C);
            Theme.fit(g,"Press "+mod.sessionKeyName()+" again to stop • "+String.format(Locale.ROOT,"%.1fs",s.confirmRemainingMs()/1000.0),164,305,363,Theme.RED,1);
        }
        if(!interactive) controls.clear();
    }
    void overview(GuiGraphicsExtractor g,Session s) {
        var value=valuation(s.loot());
        metric(g,14,"SESSION",Theme.time(s.elapsedMs()),Theme.TEXT,s.paused()?"Paused":s.running()?"Running":"Session saved");
        if (tab==2 && !s.active()) metric(g,146,"FINISHED RUNS",Integer.toString(s.completedRuns()),Theme.BLUE,"Of "+s.totalRuns()+" recorded");
        else metric(g,146,"CURRENT RUN",Theme.time(s.runMs()),Theme.GOLD,s.paused()?"Paused":s.inDungeon()?"Enter to exit":"Waiting for F7");
        metric(g,278,"EST. PROFIT",Theme.coins(value.total()),Theme.GOLD,"After sell tax");
        metric(g,410,"COINS / HOUR",Theme.coins(mod.hourlyProfit(s,value)),Theme.GREEN,mod.config().expectedFragmentProfit?"Expected fragments":"Collected drops");
        if(mouseX>=410&&mouseX<=536&&mouseY>=82&&mouseY<=139) tooltip=hourlyProfitTip();
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
        var giantStats=s.giantStats();
        for(Giant giant:Giant.values()) {
            var stat=giantStats.get(giant);
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
    LootLedger.Valuation valuation(LootLedger loot) { return loot.value(mod.market().prices().values(),mod.config().bazaarTaxPercent,mod.config().includeSalvage); }
    private record LootRow(String name,String detail,long quantity,double value) {}
    void loot(GuiGraphicsExtractor g,LootLedger ledger) {
        var value=valuation(ledger);
        var fragments = FragmentProfitRow.rows(ledger, value, mod.config().hudSections.combinedFragments());
        int cardHeight = 236 - 22 * (Giant.values().length - fragments.size());
        Theme.panel(g,14,82,214,cardHeight);
        Theme.text(g,"PROFIT BREAKDOWN",24,94,Theme.BLUE);
        int y=117;
        for(var fragment : fragments) {
            profitRow(g,fragment.label(),fragment.coins(),y,fragment.color());
            y+=22;
        }
        profitRow(g,"All essence",value.essence(),y,Theme.TEXT);
        profitRow(g,"Kill coins",value.coins(),y+22,Theme.TEXT);
        profitRow(g,"Other items",value.otherItems(),y+44,Theme.TEXT);
        g.fill(24,y+66,216,y+67,Theme.LINE);
        profitRow(g,"Est. profit",value.total(),y+77,Theme.GOLD);
        Theme.text(g,"After sell tax",24,y+96,Theme.MUTED);
        if(mouseX>=14&&mouseX<=228&&mouseY>=82&&mouseY<=82+cardHeight) {
            tooltip="Other items includes Hunting Box shards, Spirit Leaps and other dungeon drops. Fragment subtotals use the collected quantity and current sell price after tax.";
            for (int i=0; i<fragments.size(); i++) {
                int rowY = 117 + i*22;
                if (mouseY >= rowY-4 && mouseY < rowY+16) {
                    Giant giant = fragments.get(i).giant();
                    if (giant == null) {
                        tooltip = "Collected value of all four giant fragments after sell tax.";
                        break;
                    }
                    Double quote = mod.market().prices().values().get(giant.itemId);
                    if (quote != null) tooltip = giant.fragmentName+" sells for "+String.format(Locale.ROOT,"%,.1f",quote)+" Bazaar coins each before tax.";
                }
            }
        }
        Theme.panel(g,238,82,298,236);
        Theme.text(g,"COLLECTED LOOT",248,94,Theme.BLUE);
        Theme.right(g,"QTY       VALUE",524,110,Theme.MUTED);
        List<LootRow> rows=new ArrayList<>();
        for(var lot:ledger.entries().entrySet()) {
            var e=lot.getValue();
            String detail=mod.config().includeSalvage&&e.essencePerItem()>0 ? e.essencePerItem()+" "+e.essenceId().replace("ESSENCE_","").toLowerCase(Locale.ROOT)+" essence each" : Giant.fromItem(e.itemId())!=null ? "Giant fragment" : "Other items";
            rows.add(new LootRow(e.name(),detail,e.quantity(),value.lineValues().getOrDefault(lot.getKey(),0.0)));
        }
        for(var e:ledger.essence().entrySet()) rows.add(new LootRow(e.getKey().replace("ESSENCE_","")+" essence","Found in dungeon",e.getValue(),value.lineValues().getOrDefault(e.getKey(),0.0)));
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
        if(rows.isEmpty()) Theme.centered(g,"No item drops recorded.",387,194,Theme.MUTED);
        Theme.fit(g,value.unpriced()>0?value.unpriced()+" unpriced lots":"All collected loot",248,302,150,Theme.MUTED,1);
        pager(g,pages);
    }
    private void profitRow(GuiGraphicsExtractor g,String label,double value,int y,int color) {
        Theme.fit(g,label,24,y,133,Theme.MUTED,1);
        Theme.right(g,Theme.coins(value),216,y,color);
    }
    void pager(GuiGraphicsExtractor g,int pages) {
        button(g,406,295,24,20,"<",Theme.BLUE,()->page=Math.max(0,page-1),"Previous page",false);
        Theme.centered(g,(page+1)+" / "+pages,468,302,Theme.MUTED);
        button(g,506,295,24,20,">",Theme.BLUE,()->page=Math.min(pages-1,page+1),"Next page",false);
    }
    private void settings(GuiGraphicsExtractor g) {
        if (settingsView==SettingsView.HUD) { hudSections(g); return; }
        if (settingsView==SettingsView.RUNS) { runSettings(g); return; }
        Theme.panel(g,14,82,254,114);Theme.panel(g,278,82,258,114);
        Theme.text(g,"LAYOUT & APPEARANCE",25,94,Theme.BLUE);
        button(g,25,114,232,22,"HUD sections…",Theme.BLUE,()->{settingsView=SettingsView.HUD;focus=-1;},"Choose visible sections and group giant fragment profit.",false);
        button(g,25,142,232,22,"Edit in-game HUD layout",Theme.BLUE,()->minecraft.gui.setScreen(new LayoutScreen(mod,true,this)),"Move and resize the overlay you see while playing.",false);
        button(g,25,170,232,20,"Edit dashboard layout",Theme.BLUE,()->minecraft.gui.setScreen(new LayoutScreen(mod,false,this)),"Move and resize this menu independently from the HUD.",false);
        Theme.text(g,"DISPLAY",289,94,Theme.BLUE);
        button(g,289,114,236,22,"HUD  "+(mod.config().showHud?"ON":"OFF"),mod.config().showHud?Theme.GREEN:Theme.MUTED,()->{mod.config().showHud=!mod.config().showHud;mod.saveConfig();},"Show the compact overlay while the session runs.",false);
        button(g,289,144,236,22,"Show saved HUD  "+(mod.config().showStoppedHud?"ON":"OFF"),Theme.BLUE,()->{mod.config().showStoppedHud=!mod.config().showStoppedHud;mod.saveConfig();},"Keep the last session visible after stopping.",false);
        button(g,289,170,236,20,"Clear session history…",Theme.RED,this::confirmClearHistory,"Delete saved sessions for this profile.",false);
        Theme.panel(g,14,206,522,112);Theme.text(g,"PROFIT & CONTROLS",25,217,Theme.BLUE);
        button(g,25,236,238,22,"Include salvage  "+(mod.config().includeSalvage?"ON":"OFF"),Theme.BLUE,()->{mod.config().includeSalvage=!mod.config().includeSalvage;mod.saveConfig();},"Value eligible mob gear as essence. Turning this off uses item Bazaar or NPC value. No items are automatically salvaged.",false);
        button(g,279,236,28,22,"-",Theme.BLUE,()->{mod.config().bazaarTaxPercent=Math.max(0,mod.config().bazaarTaxPercent-0.05);mod.saveConfig();},"Decrease sell tax by 0.05%",false);
        Theme.centered(g,String.format(Locale.ROOT,"Tax: %.2f%%",mod.config().bazaarTaxPercent),402,243,Theme.TEXT);
        button(g,497,236,28,22,"+",Theme.BLUE,()->{mod.config().bazaarTaxPercent=Math.min(10,mod.config().bazaarTaxPercent+0.05);mod.saveConfig();},"Increase sell tax by 0.05%",false);
        button(g,25,265,238,22,"Giant profit/h  "+(mod.config().expectedFragmentProfit?"Expected":"Collected"),Theme.BLUE,()->{mod.config().expectedFragmentProfit=!mod.config().expectedFragmentProfit;mod.saveConfig();},hourlyProfitTip(),false);
        button(g,279,265,246,22,"Run actions & report…",Theme.BLUE,()->{settingsView=SettingsView.RUNS;focus=-1;},"Configure giant-kill travel, solo F7 entry and the timed run report.",false);
        Theme.fit(g,"Session: "+mod.sessionKeyName()+" • Settings: "+mod.dashboardKeyName()+" • Rebind in Options > Controls > Key Binds",25,291,500,Theme.MUTED,0.85f);
        Theme.fit(g,mod.store().warning().isEmpty()?"One press pauses or resumes. Two presses within 1.5 seconds end the session.":mod.store().warning(),25,301,500,mod.store().warning().isEmpty()?Theme.MUTED:Theme.RED,0.85f);
    }
    private void hudSections(GuiGraphicsExtractor g) {
        var settings = mod.config().hudSections;
        settingsBack(g);
        Theme.text(g,"HUD SECTIONS",116,88,Theme.TEXT);
        Theme.right(g,settings.content().blocks().size()+" / "+Section.values().length+" shown",524,88,Theme.MUTED);
        Theme.panel(g,14,110,254,208);
        Theme.panel(g,278,110,258,208);
        Theme.text(g,"SESSION & RUNS",25,122,Theme.BLUE);
        Theme.text(g,"PROFIT",289,122,Theme.BLUE);
        sectionSwitch(g,25,141,Section.HEADER);
        sectionSwitch(g,25,169,Section.SESSION_TIMER);
        sectionSwitch(g,25,197,Section.RUN_TIMER);
        sectionSwitch(g,25,225,Section.RUN_RECORDS);
        sectionSwitch(g,25,253,Section.RUN_COUNTS);
        sectionSwitch(g,25,281,Section.WATCHER_GIANTS);
        sectionSwitch(g,289,141,Section.PROFIT_BREAKDOWN);
        sectionSwitch(g,289,169,Section.TOTAL_PROFIT);
        sectionSwitch(g,289,197,Section.HOURLY_PROFIT);
        g.fill(289,229,521,230,Theme.LINE);
        Theme.text(g,"GIANT FRAGMENT PROFIT",289,241,Theme.BLUE);
        button(g,289,258,232,22,settings.combinedFragments()?"One combined total":"Separate by giant",Theme.BLUE,()->{
            settings.toggleFragmentGrouping();
            mod.saveConfig();
        },"Switch between four collected fragment subtotals and their sum.",false);
        Theme.fit(g,"Applies to HUD and Loot & profit.",289,292,232,Theme.MUTED,0.9f);
    }
    private void settingsBack(GuiGraphicsExtractor g) {
        button(g,14,82,90,20,"‹ Settings",Theme.BLUE,()->{settingsView=SettingsView.GENERAL;focus=-1;},"Return to general settings.",false);
    }
    private void runSettings(GuiGraphicsExtractor g) {
        var c=mod.config();
        settingsBack(g);Theme.text(g,"RUN ACTIONS & REPORT",116,88,Theme.TEXT);
        Theme.panel(g,14,110,254,208);Theme.panel(g,278,110,258,208);
        Theme.text(g,"AUTOMATIC COMMANDS",25,122,Theme.BLUE);
        runSwitch(g,25,142,"Warp to Dungeon Hub",c.warpOnGiantKill,()->c.warpOnGiantKill=!c.warpOnGiantKill);
        Theme.fit(g,"After the Watcher giant dies in F7.",26,170,232,Theme.MUTED,1);
        Theme.text(g,"Warp delay",26,190,Theme.MUTED);
        button(g,132,183,24,22,"−",c.warpDelayMs>0?Theme.BLUE:Theme.MUTED,()->{c.warpDelayMs-=ModConfig.WARP_DELAY_STEP_MS;mod.saveConfig();},"Shorten the loot delay by 0.1 seconds. At zero, warp as soon as the kill is confirmed.",false);
        Theme.centered(g,String.format(Locale.ROOT,"%.1f s",c.warpDelayMs/1000.0),194,190,Theme.TEXT);
        button(g,233,183,24,22,"+",c.warpDelayMs<ModConfig.MAX_WARP_DELAY_MS?Theme.BLUE:Theme.MUTED,()->{c.warpDelayMs+=ModConfig.WARP_DELAY_STEP_MS;mod.saveConfig();},"Wait 0.1 seconds longer for loot, up to 10 seconds.",false);
        runSwitch(g,25,212,"Queue solo Floor VII",c.queueSoloF7,()->c.queueSoloF7=!c.queueSoloF7);
        Theme.fit(g,"On return to the hub during a session.",26,240,232,Theme.MUTED,1);
        g.fill(25,254,257,255,Theme.LINE);
        Theme.text(g,"HYPIXEL RULES",25,265,Theme.GOLD);
        Theme.fit(g,"Hypixel prohibits automated gameplay.",25,282,232,Theme.TEXT,1);
        Theme.fit(g,"Using either command can lead to a ban.",25,297,232,Theme.MUTED,1);
        Theme.text(g,"END-OF-RUN REPORT",289,122,Theme.BLUE);
        runSwitch(g,289,142,"Show run report",c.showRunReport,()->c.showRunReport=!c.showRunReport);
        Theme.text(g,"Time on screen",289,181,Theme.MUTED);
        button(g,289,198,32,24,"−",c.reportSeconds>1?Theme.BLUE:Theme.MUTED,()->{c.reportSeconds=Math.max(1,c.reportSeconds-1);mod.saveConfig();},"One second shorter",false);
        Theme.centered(g,c.reportSeconds+(c.reportSeconds==1?" second":" seconds"),405,206,Theme.TEXT);
        button(g,489,198,32,24,"+",c.reportSeconds<30?Theme.BLUE:Theme.MUTED,()->{c.reportSeconds=Math.min(30,c.reportSeconds+1);mod.saveConfig();},"One second longer",false);
        g.fill(289,239,525,240,Theme.LINE);
        Theme.text(g,"AFTER EACH EXIT",289,250,Theme.BLUE);
        Theme.fit(g,"Run time, giant, fragment and actual loot.",289,268,234,Theme.TEXT,1);
        Theme.fit(g,"Reopen the full breakdown in Session log.",289,283,234,Theme.MUTED,1);
    }
    private void runSwitch(GuiGraphicsExtractor g,int x,int y,String label,boolean enabled,Runnable toggle) {
        control(g,x,y,232,22,label,Theme.BLUE,()->{toggle.run();mod.saveConfig();},"",false);
        Theme.text(g,label,x+8,y+7,Theme.TEXT);
        Theme.round(g,x+190,y+4,36,14,enabled?0xFF29473E:0xFF363947);
        Theme.centered(g,enabled?"ON":"OFF",x+208,y+7,enabled?Theme.GREEN:Theme.MUTED);
    }
    private void sectionSwitch(GuiGraphicsExtractor g, int x, int y, Section section) {
        var settings = mod.config().hudSections;
        boolean shown = settings.visible(section);
        control(g,x,y,232,22,section.label,Theme.BLUE,()->{
            settings.toggle(section);
            mod.saveConfig();
        },section.description,false);
        Theme.text(g,section.label,x+9,y+7,shown?Theme.TEXT:Theme.MUTED);
        Theme.round(g,x+190,y+4,36,14,shown?0xFF29473E:0xFF363947);
        Theme.centered(g,shown?"ON":"OFF",x+208,y+7,shown?Theme.GREEN:Theme.MUTED);
    }
    void confirmClearHistory() {
        boolean active = mod.session().active();
        String sessionId = mod.session().id();
        String explanation = active
                ? "Permanently delete previous sessions in this profile? Your current session will be kept."
                : "Permanently delete all saved sessions in this profile, including the last session and its runs and loot?";
        var dialog = new ConfirmScreen(confirmed -> {
            if (confirmed) {
                if (active != mod.session().active() || !sessionId.equals(mod.session().id())) {
                    confirmClearHistory();
                    return;
                }
                try {
                    mod.store().clearHistory(mod.session());
                    historyNotice = active ? "Past sessions cleared" : "Session history cleared";
                } catch (java.io.IOException e) {
                    historyNotice = "Clear failed. Check file access and retry.";
                }
                page = 0;
                tab = 2;
                log.open();
            }
            focus = -1;
            minecraft.gui.setScreen(this);
        }, Component.literal("Clear session history?"), Component.literal(explanation),
                Component.literal("Clear history"), Component.literal("Cancel"));
        minecraft.gui.setScreen(dialog);
        dialog.setDelay(10);
    }
    private String hourlyProfitTip() {
        return mod.config().expectedFragmentProfit
                ? "Giant fragment coins/h uses the expected drop value per finished encounter, your average run time and the share of runs you finish. Other loot uses collected value per session hour."
                : "Collected: all collected loot value / full session hours, as in version 1.0.0. Click in Settings to estimate only giant fragments from fixed drop chances.";
    }
    void resetPage() { page=0; focus=-1; }
    void button(GuiGraphicsExtractor g,int x,int y,int w,int h,String label,int color,Runnable action,String tip,boolean selected) {
        control(g,x,y,w,h,label,color,action,tip,selected);
        Theme.centered(g,label,x+w/2,y+(h-8)/2,color);
    }
    void control(GuiGraphicsExtractor g,int x,int y,int w,int h,String label,int color,Runnable action,String tip,boolean selected) {
        int index=controls.size();Control c=new Control(x,y,w,h,label,action);controls.add(c);
        boolean hover=c.hit(mouseX,mouseY),focused=focus==index;
        Theme.round(g,x,y,w,h,selected?0xFF354853:hover||focused?0xFF384153:0xFF282E3E);
        if(selected||focused) g.fill(x+4,y+h-2,x+w-4,y+h-1,color);
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
        if(e.key()==GLFW.GLFW_KEY_ESCAPE && settingsView!=SettingsView.GENERAL) { settingsView=SettingsView.GENERAL;focus=-1;return true; }
        if(e.key()==GLFW.GLFW_KEY_ESCAPE && tab==2 && log.inSession()) { log.back();return true; }
        if(e.key()==GLFW.GLFW_KEY_TAB) { focus=Math.floorMod(focus+((e.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),Math.max(1,controls.size())); return true; }
        if((e.key()==GLFW.GLFW_KEY_ENTER||e.key()==GLFW.GLFW_KEY_SPACE)&&focus>=0&&focus<controls.size()) { controls.get(focus).action.run();return true; }
        return super.keyPressed(e);
    }
    @Override public boolean keyReleased(KeyEvent e) { mod.screenKeyReleased(e);return super.keyReleased(e); }
    @Override public void onClose() { mod.saveConfig(); minecraft.gui.setScreen(parent); }
    @Override public void removed() { log.releaseMap(); super.removed(); }
}
