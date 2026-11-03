package dev.fragruns.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Locale;

/** Original drawing code; SBA's palette and the active Minecraft font keep this familiar. */
public final class Theme {
    public static final int BLUE=0xFFA0E1E5, TEXT=0xFFF0F2F7, MUTED=0xFF9AA5B8, GOLD=0xFFFFD16A,
            GREEN=0xFF8DE3A3, RED=0xFFFF8B91, PANEL=0xEE1B1D29, CARD=0xEF222634, LINE=0xFF343B4E;
    private Theme() {}
    public static void round(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) {
        g.fill(x+3,y,x+w-3,y+1,color); g.fill(x+1,y+1,x+w-1,y+3,color);
        g.fill(x,y+3,x+w,y+h-3,color);g.fill(x+1,y+h-3,x+w-1,y+h-1,color);g.fill(x+3,y+h-1,x+w-3,y+h,color);
    }
    public static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h) { round(g,x,y,w,h,CARD); }
    public static void text(GuiGraphicsExtractor g,String text,int x,int y,int color) { g.text(Minecraft.getInstance().font,text,x,y,color,true); }
    public static void right(GuiGraphicsExtractor g,String text,int x,int y,int color) { text(g,text,x-Minecraft.getInstance().font.width(text),y,color); }
    public static void centered(GuiGraphicsExtractor g,String text,int x,int y,int color) { g.centeredText(Minecraft.getInstance().font,text,x,y,color); }
    public static void fit(GuiGraphicsExtractor g,String text,int x,int y,int width,int color,float maxScale) {
        float scale=Math.min(maxScale,(float)width/Math.max(1,Minecraft.getInstance().font.width(text)));
        g.pose().pushMatrix(); g.pose().translate(x,y); g.pose().scale(scale,scale);
        text(g,text,0,0,color); g.pose().popMatrix();
    }
    public static String time(long ms) {
        long total=ms/1000;
        return total>=3600 ? String.format(Locale.ROOT,"%d:%02d:%02d",total/3600,total/60%60,total%60)
                : String.format(Locale.ROOT,"%d:%02d.%d",total/60,total%60,ms/100%10);
    }
    public static String coins(double value) {
        if(Math.abs(value)>=1_000_000_000) return String.format(Locale.ROOT,"%.2fB",value/1_000_000_000);
        if(Math.abs(value)>=1_000_000) return String.format(Locale.ROOT,"%.2fM",value/1_000_000);
        if(Math.abs(value)>=1_000) return String.format(Locale.ROOT,"%.1fk",value/1_000);
        return String.format(Locale.ROOT,"%,.0f",value);
    }
    public static String percent(double value) { return String.format(Locale.ROOT,"%.1f%%",value); }
}
