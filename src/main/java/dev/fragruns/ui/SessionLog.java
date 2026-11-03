package dev.fragruns.ui;

import dev.fragruns.FragRunsClient;
import dev.fragruns.core.LootLedger;
import dev.fragruns.core.Session;
import dev.fragruns.data.SessionStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class SessionLog {
    private enum View { LIST, RUNS, SUMMARY, LOOT, RUN, RUN_LOOT, MAP }
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("MMM d, uuuu  HH:mm:ss").withZone(ZoneId.systemDefault());
    private final DashboardScreen screen;
    private final FragRunsClient mod;
    private View view=View.LIST;
    private CompletableFuture<SessionStore.Catalog> catalog;
    private CompletableFuture<Session> archive;
    private List<SessionStore.Summary> entries=List.of();
    private SessionStore.Catalog listed;
    private String listedLive,selectedId="",notice="";
    private int listPage,runsPage,index;
    private Session.Details loadedDetails;
    private LootLedger runLoot;
    private DungeonDiagram diagram;
    private boolean attempted,legacyImage;
    private long retryMapAt;

    SessionLog(DashboardScreen screen,FragRunsClient mod) { this.screen=screen; this.mod=mod; }
    private void go(View next) { view=next; screen.resetPage(); notice=""; }
    void open() {
        releaseMap(); go(View.LIST); selectedId=""; archive=null; listed=null; listedLive=null;
        catalog=CompletableFuture.supplyAsync(()->{
            try { return mod.store().catalog(); }
            catch (IOException e) { throw new CompletionException(e); }
        });
    }
    boolean inSession() { return view!=View.LIST; }
    boolean viewingArchive() { return inSession() && (!selectedId.equals(mod.session().id()) || !mod.session().active()); }
    private boolean inRun() { return view==View.RUN || view==View.RUN_LOOT || view==View.MAP; }
    private static String date(SessionStore.Summary summary) {
        return summary.started().equals(Instant.EPOCH)?summary.id():DATE.format(summary.started());
    }
    String breadcrumb() {
        if (!inSession()) return "FLOOR VII  /  SESSION LOG";
        return date(new SessionStore.Summary(selectedId,0,0))+(inRun()?"   /   Run "+(index+1):"   /   Session");
    }
    String footer() { return !notice.isEmpty()?notice:"Collected quantities • Values use current sell prices and your tax settings."; }
    private Session selected() {
        if (selectedId.equals(mod.session().id())) return mod.session();
        return archive==null || !archive.isDone() || archive.isCompletedExceptionally()?null:archive.getNow(null);
    }
    private void select(String id) {
        listPage=screen.page;
        selectedId=id;
        if (id.equals(mod.session().id())) {
            var copy=Session.live();
            copy.restore(mod.session().snapshot());
            archive=CompletableFuture.completedFuture(copy);
        } else archive=CompletableFuture.supplyAsync(()->{
            try { return mod.store().readArchived(id); }
            catch (IOException e) { throw new CompletionException(e); }
        });
        go(View.RUNS);
    }
    private void selectRun(int i) {
        releaseMap(); loadedDetails=null; runLoot=null; runsPage=screen.page; index=i; go(View.RUN);
    }
    void back() {
        if (inRun()) { releaseMap(); go(View.RUNS); screen.page=runsPage; }
        else { open(); screen.page=listPage; }
    }
    void navigation(GuiGraphicsExtractor g) {
        boolean detail=inRun();
        nav(g,0,detail?"‹ Runs":"‹ Session log",this::back,false);
        if (detail) {
            nav(g,1,"Run details",()->go(View.RUN),view==View.RUN);
            nav(g,2,"Itemized loot",()->go(View.RUN_LOOT),view==View.RUN_LOOT);
            nav(g,3,"Dungeon map",()->go(View.MAP),view==View.MAP);
        } else {
            nav(g,1,"Session totals",()->go(View.SUMMARY),view==View.SUMMARY);
            nav(g,2,"Loot & profit",()->go(View.LOOT),view==View.LOOT);
            nav(g,3,"Runs",()->go(View.RUNS),view==View.RUNS);
        }
    }
    private void nav(GuiGraphicsExtractor g,int n,String label,Runnable action,boolean on) {
        screen.button(g,14+n*132,51,126,20,label,on?Theme.BLUE:Theme.MUTED,action,"",on);
    }
    void continueButton(GuiGraphicsExtractor g) {
        if (selected()==null) return;
        if (Minecraft.getInstance().player==null) { Theme.text(g,"Join a world to continue this session.",16,359,Theme.MUTED); return; }
        boolean playing=selectedId.equals(mod.session().id()) && mod.session().running();
        screen.button(g,14,352,150,20,playing?"Return to session":"Continue session",Theme.GREEN,()->continueSelected(),
                "Keep this session's runs, loot and elapsed time.",false);
    }
    private void continueSelected() {
        var live=mod.session();
        if (live.active() && !live.id().equals(selectedId)) {
            String outgoing=live.id();
            Minecraft.getInstance().gui.setScreen(new ConfirmScreen(ok->{
                Minecraft.getInstance().gui.setScreen(screen);
                if (ok && live.id().equals(outgoing)) resume();
            },Component.literal("Save and switch sessions?"),
                    Component.literal("Save your current session, then continue the selected one. An unfinished run in the current session will be saved as partial."),
                    Component.literal("Save and switch"),Component.literal("Cancel")));
        } else resume();
    }
    private void resume() {
        try { mod.continueSession(selectedId); }
        catch (IOException e) { notice="Could not switch sessions. Check file access and reopen the log."; }
    }
    void draw(GuiGraphicsExtractor g,String clearedNotice) {
        if (view==View.LIST) { list(g,clearedNotice); return; }
        var session=selected();
        if (session==null) {
            empty(g,archive!=null && archive.isCompletedExceptionally()?"Could not read this session.":"Opening session…","Use ‹ Session log to return.");
            return;
        }
        switch (view) {
            case RUNS -> runs(g,session);
            case SUMMARY -> screen.overview(g,session);
            case LOOT -> screen.loot(g,session.loot());
            default -> {
                var runs=session.runs();
                if (index>=runs.size()) { empty(g,"Run unavailable","Return to the session log."); return; }
                var run=runs.get(index);
                if (run.details()!=loadedDetails) {
                    loadedDetails=run.details(); runLoot=null;
                    if (loadedDetails!=null) { runLoot=new LootLedger(); runLoot.restore(loadedDetails.loot()); }
                }
                if (view==View.RUN) details(g,run);
                else if (view==View.MAP) map(g,run,14,82,522,236,true);
                else if (runLoot!=null) screen.loot(g,runLoot);
                else empty(g,"Itemized loot was introduced in v1.5.","Open Loot & profit in the session for the saved totals.");
            }
        }
    }
    private void list(GuiGraphicsExtractor g,String clearedNotice) {
        if (catalog==null) open();
        if (!catalog.isDone()) { empty(g,"Reading session log…",""); return; }
        var result=catalog.isCompletedExceptionally()?null:catalog.getNow(null);
        var live=mod.session();
        if (listedLive==null || listed!=result || !live.id().equals(listedLive)) {
            var merged=new ArrayList<SessionStore.Summary>();
            if (result!=null) for (var row:result.sessions()) if (!row.id().equals(live.id())) merged.add(row);
            if (!live.id().isBlank()) merged.add(SessionStore.Summary.of(live));
            merged.sort(Comparator.comparing(SessionStore.Summary::started).reversed().thenComparing(SessionStore.Summary::id));
            listed=result; listedLive=live.id(); entries=List.copyOf(merged);
        }
        Theme.panel(g,14,82,522,236);
        Theme.text(g,"SESSION LOG",25,94,Theme.BLUE);
        Theme.right(g,entries.size()+" saved",524,94,Theme.MUTED);
        Theme.text(g,"STARTED  /  LOCAL TIME",28,116,Theme.MUTED);
        Theme.text(g,"ELAPSED",256,116,Theme.MUTED); Theme.text(g,"RUNS",356,116,Theme.MUTED);
        Theme.text(g,"STATUS",425,116,Theme.MUTED);
        int pages=pages(entries.size(),7);
        for (int i=screen.page*7;i<Math.min(entries.size(),(screen.page+1)*7);i++) {
            var entry=entries.get(i); boolean current=entry.id().equals(live.id());
            int y=132+i%7*22;
            screen.control(g,21,y,508,20,date(entry),Theme.BLUE,()->select(entry.id()),"Open this session's totals and runs.",false);
            Theme.fit(g,date(entry),28,y+6,213,Theme.TEXT,1);
            Theme.text(g,Theme.time(current?live.elapsedMs():entry.elapsedMs()),256,y+6,Theme.TEXT);
            Theme.text(g,Integer.toString(current?live.totalRuns():entry.runs()),356,y+6,Theme.TEXT);
            String status=current && live.active()?(live.paused()?"Paused":"Live"):"Saved";
            Theme.text(g,status,425,y+6,status.equals("Live")?Theme.GREEN:Theme.MUTED);
            Theme.right(g,"›",520,y+5,Theme.BLUE);
        }
        if (entries.isEmpty()) Theme.centered(g,"Your first session will appear here.",275,190,Theme.MUTED);
        screen.button(g,25,294,147,21,"Clear session history…",Theme.RED,screen::confirmClearHistory,"Manage saved sessions for this profile.",false);
        String message=result==null?"Archive folder unavailable":result.unreadable()>0?result.unreadable()+" unreadable archives":clearedNotice.isEmpty()?"Newest first":clearedNotice;
        Theme.fit(g,message,179,301,217,Theme.MUTED,0.9f); screen.pager(g,pages);
    }
    private int pages(int size,int perPage) {
        int count=Math.max(1,(size+perPage-1)/perPage);
        screen.page=Math.clamp(screen.page,0,count-1); return count;
    }
    private void runs(GuiGraphicsExtractor g,Session s) {
        Theme.panel(g,14,82,522,236);
        Theme.text(g,"RUN LOG",25,95,Theme.BLUE);
        Theme.right(g,Theme.time(s.elapsedMs())+"   •   "+s.totalRuns()+" runs   •   "+Theme.coins(screen.valuation(s.loot()).total())+" coins",524,95,Theme.TEXT);
        Theme.text(g,"#",28,117,Theme.MUTED); Theme.text(g,"DURATION",68,117,Theme.MUTED);
        Theme.text(g,"GIANT",166,117,Theme.MUTED); Theme.text(g,"DROPS",320,117,Theme.MUTED); Theme.text(g,"RESULT",410,117,Theme.MUTED);
        var runs=s.runs(); int pages=pages(runs.size(),7);
        for (int n=0;n<7;n++) {
            int i=runs.size()-1-screen.page*7-n; if (i<0) break;
            var r=runs.get(i); int y=132+22*n;
            screen.control(g,21,y,508,20,"Run "+(i+1),Theme.BLUE,()->selectRun(i),"Inspect this run's loot and dungeon map.",false);
            Theme.text(g,Integer.toString(i+1),28,y+6,Theme.MUTED); Theme.text(g,Theme.time(r.milliseconds()),68,y+6,Theme.TEXT);
            Theme.fit(g,r.giant()==null?"Unseen":r.giant().label,166,y+6,147,r.giant()==null?Theme.MUTED:r.giant().color,1);
            Theme.text(g,Integer.toString(r.fragments()),320,y+6,Theme.TEXT);
            Theme.text(g,r.completed()?"Finished":"Partial",410,y+6,r.completed()?Theme.GREEN:Theme.MUTED);
            Theme.right(g,"›",520,y+5,Theme.BLUE);
        }
        if (runs.isEmpty()) Theme.centered(g,"Runs appear here when you leave Floor VII.",275,192,Theme.MUTED);
        Theme.text(g,"Newest first",25,301,Theme.MUTED); screen.pager(g,pages);
    }
    private void details(GuiGraphicsExtractor g,Session.Run r) {
        Theme.panel(g,14,82,289,236);
        Theme.text(g,"RUN "+(index+1),25,94,Theme.BLUE);
        Theme.right(g,r.completed()?"FINISHED":"PARTIAL",291,94,r.completed()?Theme.GREEN:Theme.MUTED);
        Theme.fit(g,Theme.time(r.milliseconds()),25,112,105,Theme.TEXT,1.7f);
        var d=r.details();
        if (d!=null) {
            Theme.right(g,"Start  "+DATE.format(Instant.ofEpochMilli(d.startedAt())),291,109,Theme.MUTED);
            String end=d.endedAt()>0?DATE.format(Instant.ofEpochMilli(d.endedAt())):"Save checkpoint";
            Theme.right(g,"End  "+end,291,122,Theme.MUTED);
        }
        String giant=r.giant()==null?"Unseen":r.giant().label+(d==null?"":d.giantDefeated()?" • defeated":" • sighted");
        line(g,140,"Giant",giant,r.giant()==null?Theme.MUTED:r.giant().color);
        String drop=r.fragments()>0?r.fragments()+" × "+(r.giant()==null?"fragment":r.giant().fragmentName):
                r.completed() || d!=null && d.giantDefeated()?"No drop":"None recorded";
        line(g,157,"Fragment",drop,r.fragments()>0?Theme.GOLD:Theme.TEXT);
        line(g,174,"Exit",r.endReason()==null?"Unknown":r.endReason(),Theme.MUTED);
        g.fill(25,191,291,192,Theme.LINE);
        if (runLoot!=null) {
            var value=screen.valuation(runLoot);
            line(g,202,"Kill coins",Theme.coins(value.coins()),Theme.TEXT);
            line(g,222,"Giant fragments",Theme.coins(value.fragmentValues().values().stream().mapToDouble(Double::doubleValue).sum()),Theme.TEXT);
            line(g,242,"All essence",Theme.coins(value.essence()),Theme.TEXT);
            line(g,262,"Other items",Theme.coins(value.otherItems()),Theme.TEXT);
            g.fill(25,281,291,282,Theme.LINE);
            line(g,295,"Estimated profit",Theme.coins(value.total()),Theme.GOLD);
        } else {
            Theme.text(g,"Per-run profit wasn't recorded.",25,219,Theme.MUTED);
            Theme.fit(g,"See the session's Loot & profit for its totals.",25,239,264,Theme.MUTED,1);
        }
        map(g,r,313,82,223,236,false);
    }
    private static void line(GuiGraphicsExtractor g,int y,String label,String value,int color) {
        Theme.text(g,label,25,y,Theme.MUTED);
        Theme.right(g,value,291,y,color);
    }
    private void map(GuiGraphicsExtractor g,Session.Run r,int x,int y,int width,int height,boolean large) {
        Theme.panel(g,x,y,width,height); Theme.text(g,"DUNGEON MAP",x+11,y+12,Theme.BLUE);
        if (r.details()!=null && diagram==null && (!attempted || System.nanoTime()>=retryMapAt)) loadMap(r.details().id());
        int size=large?200:190, cx=x+width/2, top=y+29;
        Theme.round(g,cx-size/2-2,top-2,size+4,size+4,0xFF151823);
        if (diagram!=null) diagram.draw(g,cx-size/2,top,size);
        else {
            Theme.centered(g,"Room layout unavailable",cx,top+size/2-7,Theme.MUTED);
            Theme.centered(g,legacyImage || r.details()==null?"Recorded by an older version":"No map received for this run",cx,top+size/2+9,Theme.MUTED);
        }
    }
    private void loadMap(UUID id) {
        attempted=true;
        retryMapAt=System.nanoTime()+5_000_000_000L;
        try { diagram=new DungeonDiagram(mod.store().maps().read(id)); }
        catch (IOException e) { legacyImage=mod.store().maps().hasLegacyImage(id); }
    }
    void releaseMap() {
        if (diagram!=null) diagram.close();
        diagram=null; attempted=false; legacyImage=false;
    }
    private void empty(GuiGraphicsExtractor g,String title,String help) {
        Theme.panel(g,14,82,522,236);
        Theme.centered(g,title,275,179,Theme.TEXT); Theme.centered(g,help,275,200,Theme.MUTED);
    }
}
