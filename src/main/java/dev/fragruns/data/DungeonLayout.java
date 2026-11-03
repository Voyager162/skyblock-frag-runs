package dev.fragruns.data;

import java.util.Base64;
import java.util.List;

/** Map-space data only. Coordinates are independent of the world camera, HUD and GUI scale. */
public record DungeonLayout(int version,String palette,List<Room> rooms,List<Door> doors,List<Marker> markers) {
    public record Room(List<Integer> tiles,int color,String name,String mark) {
        public Room {
            tiles=List.copyOf(tiles);
            if (tiles.isEmpty() || tiles.size()>36 || tiles.stream().anyMatch(i->i<0 || i>=36))
                throw new IllegalArgumentException("Invalid room footprint");
            if (name==null || name.length()>128 || mark==null) throw new IllegalArgumentException("Invalid room label");
        }
    }
    public record Door(int x,int y,int color) {
        public Door {
            if (x<0 || y<0 || x>10 || y>10 || (x+y)%2!=1) throw new IllegalArgumentException("Invalid door");
        }
    }
    public record Marker(int x,int y,int rotation,String sprite) {
        public Marker {
            if (x< -128 || x>127 || y< -128 || y>127 || rotation<0 || rotation>15 || sprite==null)
                throw new IllegalArgumentException("Invalid map marker");
        }
    }
    public DungeonLayout {
        rooms=List.copyOf(rooms); doors=List.copyOf(doors); markers=List.copyOf(markers);
        if (version!=1 || rooms.size()>36 || doors.size()>60 || markers.size()>256 || palette==null
                || (!palette.isEmpty() && (palette.length()!=21_848 || Base64.getDecoder().decode(palette).length!=128*128))
                || (palette.isEmpty()==rooms.isEmpty())) throw new IllegalArgumentException("Invalid dungeon layout");
    }
    public boolean schematic() { return !rooms.isEmpty(); }
    public int tileCount() { return rooms.stream().mapToInt(room->room.tiles.size()).sum(); }
}
