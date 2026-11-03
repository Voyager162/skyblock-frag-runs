package dev.fragruns.ui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fragruns.data.DungeonLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;
import java.util.*;

/** Draw the saved dungeon in map coordinates. Only a server palette needs a generated texture. */
final class DungeonDiagram implements AutoCloseable {
    private static final Identifier PALETTE=Identifier.fromNamespaceAndPath("fragruns","session_log/server_map");
    private final DungeonLayout layout;
    private final Map<String,Identifier> icons=new HashMap<>();
    DungeonDiagram(DungeonLayout layout) {
        this.layout=layout;
        if (layout.schematic()) {
            for (var r:layout.rooms()) if (!icons.containsKey(r.mark())) icons.put(r.mark(),dev.fragruns.tracking.StellaRooms.checkmarkTexture(r.mark()));
        } else {
            byte[] colors=Base64.getDecoder().decode(layout.palette());
            var image=new NativeImage(128,128,false);
            for (int i=0;i<colors.length;i++) image.setPixel(i%128,i/128,MapColor.getColorFromPackedId(colors[i]&255));
            Minecraft.getInstance().getTextureManager().register(PALETTE,new DynamicTexture(()->"Archived server map",image));
            for (var marker:layout.markers()) {
                var id=Identifier.tryParse(marker.sprite());
                if (id!=null) addIcon(marker.sprite(),id.withPath("textures/map/decorations/"+id.getPath()+".png"));
            }
        }
    }
    private void addIcon(String key,Identifier id) {
        if (id!=null && Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) icons.put(key,id);
    }
    void draw(GuiGraphicsExtractor g,int x,int y,int size) {
        g.pose().pushMatrix();
        g.pose().translate(x,y); g.pose().scale(size/136f,size/136f); g.pose().translate(4,4);
        if (layout.schematic()) {
            for (var door:layout.doors()) {
                int dx=door.x()/2*22,dy=door.y()/2*22;
                if (door.x()%2==1) g.fill(dx+18,dy+6,dx+22,dy+12,door.color());
                else g.fill(dx+6,dy+18,dx+12,dy+22,door.color());
            }
            for (var room:layout.rooms()) room(g,room);
        } else {
            g.blit(PALETTE,0,0,128,128,0,1,0,1);
            for (var marker:layout.markers()) {
                var icon=icons.get(marker.sprite());
                if (icon==null) continue;
                g.pose().pushMatrix();
                g.pose().translate(64+marker.x()/2f,64+marker.y()/2f);
                g.pose().rotate((float)(Math.PI*marker.rotation()/8));
                g.blit(icon,-3,-3,3,3,0,1,0,1);
                g.pose().popMatrix();
            }
        }
        g.pose().popMatrix();
    }
    private void room(GuiGraphicsExtractor g,DungeonLayout.Room room) {
        var cells=room.tiles();
        int sumX=0,sumY=0;
        for (int tile:cells) {
            int col=tile%6,row=tile/6,x=col*22,y=row*22;
            sumX+=col; sumY+=row;
            boolean right=col<5 && cells.contains(tile+1), down=cells.contains(tile+6);
            g.fill(x,y,x+(right?22:18),y+18,room.color());
            if (down) g.fill(x,y+18,x+(right && cells.contains(tile+7)?22:18),y+22,room.color());
        }
        int center=cells.getFirst();
        double nearest=Double.MAX_VALUE;
        for (int tile:cells) {
            double dx=tile%6-(double)sumX/cells.size(),dy=tile/6-(double)sumY/cells.size();
            if (dx*dx+dy*dy<nearest) { nearest=dx*dx+dy*dy;center=tile; }
        }
        int x=center%6*22+9,y=center/6*22+9;
        var icon=icons.get(room.mark());
        int markY=room.name().isBlank()?y:y-3;
        if (icon!=null) g.blit(icon,x-4,markY-4,x+4,markY+4,0,1,0,1);
        else if (room.mark().equals("GREEN") || room.mark().equals("WHITE")) {
            int color=room.mark().equals("GREEN")?0xFF55DA67:0xFFFFFFFF;
            for (int i=0;i<3;i++) g.fill(x-4+i,markY+i,x-2+i,markY+i+2,color);
            for (int i=0;i<5;i++) g.fill(x-1+i,markY+2-i,x+1+i,markY+4-i,color);
        } else if (room.mark().equals("FAILED")) {
            for (int i=-3;i<=3;i++) { g.fill(x+i,markY+i,x+i+1,markY+i+2,Theme.RED); g.fill(x-i,markY+i,x-i+1,markY+i+2,Theme.RED); }
        } else if (room.mark().equals("UNEXPLORED")) Theme.centered(g,"?",x,markY-4,Theme.MUTED);
        if (!room.name().isBlank()) {
            var font=Minecraft.getInstance().font;
            float scale=Math.min(0.55f,19f/Math.max(1,font.width(room.name())));
            g.pose().pushMatrix(); g.pose().translate(x,markY+5); g.pose().scale(scale,scale);
            Theme.centered(g,room.name(),0,0,Theme.TEXT);
            g.pose().popMatrix();
        }
    }
    @Override public void close() { if (!layout.schematic()) Minecraft.getInstance().getTextureManager().release(PALETTE); }
}
