package dev.fragruns.ui;

import dev.fragruns.FragRunsClient;
import dev.fragruns.core.Session;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Transient gameplay HUD. It never creates a screen or captures input. */
public final class RunReportHud {
    private static final int WIDTH=207;
    private RunReportHud() {}
    public static void render(GuiGraphicsExtractor g,FragRunsClient mod) {
        var mc=Minecraft.getInstance();
        if (mc.player==null || mc.gui.hud.isHidden() || mc.gui.screen()!=null || mc.gui.overlay()!=null || !mod.config().showRunReport) return;
        var report=mod.tracker().report();
        var s=mod.session();
        Session.Run r=report.showing(s);
        if (r==null) return;
        var value=report.valuation(r,mod.market().prices().values(),mod.config());
        int height=value.unpriced()>0?192:180;
        var window=mc.getWindow();
        int vw=window.getGuiScaledWidth(),vh=window.getGuiScaledHeight();
        float scale=(float)Math.min(1.0,Math.min((vw-12.0)/(WIDTH*1.75),(vh-34.0)/height));
        if (scale<=0) return;
        float x=vw-8-WIDTH*scale,y=Math.min(28,Math.max(6,vh-height*scale-6));
        var config=mod.config();
        if (config.showHud && (s.active() || config.showStoppedHud) && !config.hudSections.content().blocks().isEmpty()) {
            var hud=config.hudSections.bounds(config.hud,vw,vh,s.confirmingStop());
            if (x<hud.x()+hud.width() && x+WIDTH*scale>hud.x() && y<hud.y()+hud.height() && y+height*scale>hud.y()) {
                if (hud.y()+hud.height()+height*scale+12<=vh) y=(float)(hud.y()+hud.height()+6);
                else if (8+WIDTH*scale<hud.x()) x=8;
            }
        }
        g.pose().pushMatrix();
        g.pose().translate(x,y);
        g.pose().scale(scale,scale);
        Theme.round(g,1,2,WIDTH,height,0x66000000);
        Theme.round(g,0,0,WIDTH,height,Theme.PANEL);
        g.fill(10,5,WIDTH-10,6,Theme.LINE);
        g.fill(10,5,10+(int)((WIDTH-20)*report.remaining(mod.config().reportSeconds)),6,Theme.BLUE);
        Theme.text(g,"F7  /  RUN "+report.number(),10,12,Theme.BLUE);
        Theme.right(g,r.completed()?"FINISHED":"PARTIAL",WIDTH-10,12,r.completed()?Theme.GREEN:Theme.MUTED);
        Theme.fit(g,Theme.time(r.milliseconds()),10,27,78,Theme.TEXT,1.45f);
        Theme.right(g,r.giant()==null?"Giant unseen":r.giant().label,WIDTH-10,30,r.giant()==null?Theme.MUTED:r.giant().color);
        String drop=r.fragments()>0?r.fragments()+" × "+(r.giant()==null?"fragment":r.giant().fragmentName):
                s.stash().shared(r.details().id())?"Shared stash drop":mod.store().hasStashChoice(s.id(),r.details().id())?"Stash drop • assign in log":
                s.stash().awaiting(r.details().id())?"Stash unverified":
                r.details().giantDefeated()?"No fragment drop":"No fragment recorded";
        Theme.fit(g,drop,10,46,WIDTH-20,r.fragments()>0?Theme.GOLD:Theme.MUTED,1);
        g.fill(10,60,WIDTH-10,61,Theme.LINE);
        Theme.text(g,"RUN PROFIT",10,67,Theme.BLUE);
        row(g,"Giant fragments",value.fragmentValues().values().stream().mapToDouble(Double::doubleValue).sum(),82);
        row(g,"Kill coins",value.coins(),95);
        row(g,"Dropped essence",value.directEssence(),108);
        row(g,"Salvage essence",value.salvage(),121);
        row(g,"Other items",value.otherItems(),134);
        if (value.unpriced()>0) Theme.text(g,value.unpriced()+" unpriced loot "+(value.unpriced()==1?"lot":"lots"),10,148,Theme.MUTED);
        int totalY=height-21;
        Theme.round(g,6,totalY-6,WIDTH-12,24,0xFF2C3040);
        Theme.text(g,"Total profit",12,totalY+2,Theme.GOLD);
        Theme.right(g,Theme.coins(value.total()),WIDTH-12,totalY+2,Theme.GOLD);
        g.pose().popMatrix();
    }
    private static void row(GuiGraphicsExtractor g,String label,double coins,int y) {
        Theme.text(g,label,10,y,Theme.MUTED);
        Theme.right(g,Theme.coins(coins),WIDTH-10,y,Theme.TEXT);
    }
}
