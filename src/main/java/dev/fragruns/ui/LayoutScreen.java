package dev.fragruns.ui;

import dev.fragruns.FragRunsClient;
import dev.fragruns.core.Layout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.Locale;

public final class LayoutScreen extends Screen {
    private final FragRunsClient mod;
    private final boolean hud;
    private final Screen parent;
    private final DashboardScreen preview;
    private boolean dragging,resizing;
    private double offsetX,offsetY,resizeStart,resizeScale;
    public LayoutScreen(FragRunsClient mod,boolean hud,Screen parent) {
        super(Component.literal("Edit Frag Runs Layout")); this.mod=mod;this.hud=hud;this.parent=parent;preview=new DashboardScreen(mod,false);
    }
    private Layout layout() { return hud?mod.config().hud:mod.config().dashboard; }
    private int baseWidth() { return hud?FragHud.WIDTH:DashboardScreen.WIDTH; }
    private int baseHeight() { return hud?FragHud.HEIGHT:DashboardScreen.HEIGHT; }
    private Layout.Bounds bounds() { return layout().fit(width,height,baseWidth(),baseHeight()); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0x70090C15);
        g.fill(width/2,0,width/2+1,height,0x405F8291);g.fill(0,height/2,width,height/2+1,0x405F8291);
        var b=bounds();
        g.pose().pushMatrix();g.pose().translate((float)b.x(),(float)b.y());g.pose().scale((float)b.scale(),(float)b.scale());
        if(hud) FragHud.draw(g,mod);else preview.preview(g);
        g.fill(0,0,baseWidth(),1,Theme.BLUE);g.fill(0,baseHeight()-1,baseWidth(),baseHeight(),Theme.BLUE);
        g.fill(0,0,1,baseHeight(),Theme.BLUE);g.fill(baseWidth()-1,0,baseWidth(),baseHeight(),Theme.BLUE);
        g.fill(baseWidth()-9,baseHeight()-9,baseWidth()+2,baseHeight()+2,Theme.BLUE);
        g.pose().popMatrix();
        Theme.round(g,width/2-170,4,340,29,0xF01B1D29);
        Theme.centered(g,"EDIT "+(hud?"HUD":"DASHBOARD")+"  •  "+String.format(Locale.ROOT,"%.0f%%",b.scale()*100),width/2,8,Theme.BLUE);
        Theme.centered(g,"Drag to move • Scroll or +/- to scale • Corner to resize",width/2,21,Theme.MUTED);
        Theme.round(g,width/2-154,height-28,308,24,0xF01B1D29);
        Theme.centered(g,"R: reset     Arrows: move     Esc / Enter: save",width/2,height-20,Theme.TEXT);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl) {
        if(e.button()!=0) return false;
        var b=bounds();
        if(e.x()>=b.x()+b.width()-12&&e.x()<=b.x()+b.width()+6&&e.y()>=b.y()+b.height()-12&&e.y()<=b.y()+b.height()+6) {
            resizing=true; resizeStart=e.x();resizeScale=b.scale(); return true;
        }
        if(b.contains(e.x(),e.y())) { dragging=true;offsetX=e.x()-b.x();offsetY=e.y()-b.y();return true; }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy) {
        if(dragging) { layout().move(e.x()-offsetX,e.y()-offsetY,width,height,bounds());return true; }
        if(resizing) { layout().scale=resizeScale+(e.x()-resizeStart)/baseWidth();layout().sanitize();return true; }
        return false;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { dragging=false;resizing=false;mod.saveConfig();return true; }
    @Override public boolean mouseScrolled(double x,double y,double h,double v) { layout().scale=bounds().scale()+v*0.05;layout().sanitize();return true; }
    @Override public boolean keyPressed(KeyEvent e) {
        if(mod.handleScreenKey(e)) return true;
        int key=e.key();
        if(key==GLFW.GLFW_KEY_ESCAPE||key==GLFW.GLFW_KEY_ENTER) { onClose();return true; }
        if(key==GLFW.GLFW_KEY_R) { layout().x=hud?0:0.5;layout().y=hud?0.35:0.5;layout().scale=hud?0.85:1;return true; }
        if(key==GLFW.GLFW_KEY_EQUAL||key==GLFW.GLFW_KEY_KP_ADD) { layout().scale=bounds().scale()+0.05;layout().sanitize();return true; }
        if(key==GLFW.GLFW_KEY_MINUS||key==GLFW.GLFW_KEY_KP_SUBTRACT) { layout().scale=bounds().scale()-0.05;layout().sanitize();return true; }
        var b=bounds();int dx=key==GLFW.GLFW_KEY_LEFT?-2:key==GLFW.GLFW_KEY_RIGHT?2:0;int dy=key==GLFW.GLFW_KEY_UP?-2:key==GLFW.GLFW_KEY_DOWN?2:0;
        if(dx!=0||dy!=0) { layout().move(b.x()+dx,b.y()+dy,width,height,b);return true; }
        return super.keyPressed(e);
    }
    @Override public boolean keyReleased(KeyEvent e) { mod.screenKeyReleased(e);return super.keyReleased(e); }
    @Override public void onClose() { mod.saveConfig();minecraft.gui.setScreen(parent); }
}
