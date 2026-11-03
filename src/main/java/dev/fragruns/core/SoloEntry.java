package dev.fragruns.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Identify the server's solo entry control in the reply to our specific Floor VII request. */
public final class SoloEntry {
    public record Option(int slot,String name,String lore) {}
    private static final Pattern FLOOR=Pattern.compile("\\bfloor\\s+(vii|viii|vi|iv|iii|ii|v|i|[0-9]+)\\b|\\b([fm])([0-9]+)\\b");
    private static final Pattern SOLO=Pattern.compile("(?:(?:join|enter|play|queue|start)(?: as| in)? )?solo(?: (?:mode|queue|dungeon|run))?(?: \\((?:1 player|solo)\\))?[!>]?");
    private SoloEntry() {}
    private static String clean(String text) {
        return Signals.clean(text).toLowerCase(Locale.ROOT).replaceAll("^[▶»➜➤ ]+","").strip();
    }
    public static int slot(String title,List<Option> options) {
        String heading=clean(title);
        var floor=FLOOR.matcher(heading);
        boolean f7=false;
        if (floor.find()) {
            f7="vii".equals(floor.group(1)) || "7".equals(floor.group(1)) || "f".equals(floor.group(2)) && "7".equals(floor.group(3));
            if (!f7) return -1;
        }
        if (heading.contains("master") || heading.contains("party finder") || heading.contains("kuudra")) return -1;
        boolean dungeon=heading.contains("dungeon") || heading.contains("catacombs") || f7;
        boolean chooser=heading.matches("(?:select|choose) (?:a |your )?(?:mode|party|queue type|playstyle)|party mode|queue options|confirm(?:ation)?|how (?:do|would) you (?:want|like) to play\\??");
        boolean partyAlternative=options.stream().map(o->clean(o.name())).anyMatch(n->n.matches("(?:join |find |queue as )?(?:a )?party|party finder|group finder|play with (?:a )?party"));
        int solo=-1,confirm=-1;
        for (var option:options) {
            String name=clean(option.name()),lore=clean(option.lore());
            if (lore.contains("not unlocked") || lore.contains("unavailable") || lore.contains("cannot enter")) continue;
            if (SOLO.matcher(name).matches()) {
                if (solo>=0) return -1;
                boolean dungeonLore=lore.contains("dungeon") || lore.contains("catacombs");
                if (dungeon || chooser && (partyAlternative || dungeonLore) || partyAlternative && dungeonLore && !heading.equals("chest"))
                    solo=option.slot();
            } else if (f7 && name.matches("confirm|(?:enter|start|join) dungeon")) {
                if (confirm>=0) return -1;
                confirm=option.slot();
            }
        }
        return solo>=0?solo:confirm;
    }
}
