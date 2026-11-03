package dev.fragruns.core;

import java.util.Arrays;
import java.util.Locale;

public enum Giant {
    DIAMANTE("Diamante", "Diamante's Handle", "GIANT_FRAGMENT_DIAMOND", 0xFF55FFFF),
    LASR("L.A.S.R.", "L.A.S.R.'s Eye", "GIANT_FRAGMENT_LASER", 0xFFAAFFAA),
    BIGFOOT("Bigfoot", "Bigfoot's Bola", "GIANT_FRAGMENT_BIGFOOT", 0xFFFFBB66),
    JOLLY_PINK("Jolly Pink Giant", "Jolly Pink Rock", "GIANT_FRAGMENT_BOULDER", 0xFFFF99CC);

    public final String label, fragmentName, itemId;
    public final int color;

    Giant(String label, String fragmentName, String itemId, int color) {
        this.label = label;
        this.fragmentName = fragmentName;
        this.itemId = itemId;
        this.color = color;
    }

    public static Giant fromItem(String id) {
        return Arrays.stream(values()).filter(g -> g.itemId.equals(id)).findFirst().orElse(null);
    }

    public static Giant fromName(String name) {
        String s = Signals.clean(name).toLowerCase(Locale.ROOT);
        if (s.contains("diamante") || s.contains("diamond giant")) return DIAMANTE;
        if (s.contains("l.a.s.r") || s.contains("lasr")) return LASR;
        if (s.contains("bigfoot")) return BIGFOOT;
        if (s.contains("jolly pink")) return JOLLY_PINK;
        return null;
    }
}
