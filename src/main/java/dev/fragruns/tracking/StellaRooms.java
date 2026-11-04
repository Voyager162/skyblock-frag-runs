package dev.fragruns.tracking;

import dev.fragruns.data.DungeonLayout;
import java.awt.Color;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.*;

/** Optional bridge to StellaNav's explored room model. Its Kotlin types are absent in a plain Fabric install. */
public final class StellaRooms {
    private final Object dungeon,settings,config;
    private final Method configValue;
    private WeakReference<Object> world=new WeakReference<>(null);
    private Set<Object> observed=Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<Object> departed=Set.of();
    private static final ClassValue<Map<String,Method>> GETTERS=new ClassValue<>() {
        @Override protected Map<String,Method> computeValue(Class<?> type) {
            var methods=new HashMap<String,Method>();
            for (Method m:type.getMethods()) if (m.getParameterCount()==0) methods.put(m.getName(),m);
            return methods;
        }
    };
    StellaRooms() throws ReflectiveOperationException {
        dungeon=singleton("co.stellarskys.stella.api.dungeons.Dungeon");
        settings=singleton("co.stellarskys.stella.features.stellanav.Map");
        config=Class.forName("co.stellarskys.stella.utils.ConfigKt").getMethod("getConfig").invoke(null);
        configValue=config.getClass().getMethod("get",String.class);
    }
    private static Object singleton(String type) throws ReflectiveOperationException {
        return Class.forName(type).getField("INSTANCE").get(null);
    }
    private static Object get(Object target,String name) throws ReflectiveOperationException {
        Method method=GETTERS.get(target.getClass()).get(name);
        if (method==null) throw new NoSuchMethodException(name);
        return method.invoke(target);
    }
    private boolean setting(String name) throws ReflectiveOperationException { return (boolean)get(settings,name); }
    /** Stella loads these icons from its asset directory and registers them as dynamic textures. */
    public static net.minecraft.resources.Identifier checkmarkTexture(String name) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("stella")) return null;
        try {
            for (Object mark:Class.forName("co.stellarskys.stella.api.dungeons.utils.Checkmark").getEnumConstants()) {
                if (((Enum<?>)mark).name().equals(name)) return (net.minecraft.resources.Identifier)get(mark,"getTexture");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { /* The diagram has built-in marks. */ }
        return null;
    }
    DungeonLayout read(Object level) throws ReflectiveOperationException {
        if (world.get()!=level) {
            if (!observed.isEmpty()) departed=observed;
            observed=Collections.newSetFromMap(new IdentityHashMap<>());
            world=new WeakReference<>(level);
        }
        if (!Boolean.TRUE.equals(configValue.invoke(config,"mapEnabled")) || Boolean.TRUE.equals(get(dungeon,"getInBoss"))) return null;
        Object floor=get(dungeon,"getFloorNumber");
        if (floor!=null && !floor.equals(7)) return null;
        boolean hidden=setting("getHiddenRooms");
        var rooms=new ArrayList<DungeonLayout.Room>();
        for (Object room:(Collection<?>)get(dungeon,"getUniqueRooms")) {
            // The provider can reset a tick after the level changes. Never file its old rooms under a new run.
            if (departed.contains(room)) continue;
            var type=get(room,"getType");
            var color=(Color)get(type,"getColor");
            if (color==null || !hidden && !(boolean)get(room,"isVisible")) continue;
            var cells=new TreeSet<Integer>();
            for (Object pair:(List<?>)get(room,hidden?"getComponents":"getVisibleComponents")) {
                int x=(int)get(pair,"getFirst"), y=(int)get(pair,"getSecond");
                if (x>=0 && x<6 && y>=0 && y<6) cells.add(6*y+x);
            }
            if (cells.isEmpty()) continue;
            observed.add(room);
            boolean puzzle=((Enum<?>)type).name().equals("PUZZLE");
            var state=(Enum<?>)get(room,"getCheckmark");
            boolean checks=setting(puzzle?"getPuzzleCheck":"getRoomCheck");
            String mark=checks?state.name():"NONE";
            Object label=setting(puzzle?"getPuzzleName":"getRoomName")?get(room,"getName"):null;
            if (!(boolean)get(room,"getExplored")) color=color.darker();
            rooms.add(new DungeonLayout.Room(List.copyOf(cells),color.getRGB(),Objects.toString(label,""),mark,((Enum<?>)type).name()));
        }
        if (rooms.isEmpty()) return null;
        rooms.sort(Comparator.comparingInt(r->r.tiles().getFirst()));
        var doors=new ArrayList<DungeonLayout.Door>();
        for (Object door:(Collection<?>)get(dungeon,"getUniqueDoors")) {
            if (!hidden && !((Enum<?>)get(door,"getState")).name().equals("DISCOVERED")) continue;
            Object pair=get(door,"getComp");
            int x=(int)get(pair,"getFirst"), y=(int)get(pair,"getSecond");
            if (x<0 || y<0 || x>10 || y>10 || (x+y)%2!=1) continue;
            Color color=(Color)((boolean)get(door,"getOpened")?get(settings,"getNormalDoorColor"):get(get(door,"getType"),"getColor"));
            if (color!=null) doors.add(new DungeonLayout.Door(x,y,color.getRGB()));
        }
        doors.sort(Comparator.comparingInt(DungeonLayout.Door::y).thenComparingInt(DungeonLayout.Door::x));
        return new DungeonLayout(1,"",rooms,doors,List.of());
    }
}
