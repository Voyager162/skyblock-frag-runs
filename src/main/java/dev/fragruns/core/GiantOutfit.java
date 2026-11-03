package dev.fragruns.core;

/** Watcher giants can all have the same mob label. Armor arrives in pieces and LASR changes dye. */
public final class GiantOutfit {
    public enum Gear { DIAMOND, LEATHER, BOOTS_ONLY, UNKNOWN }
    private int firstDye=-1;
    private boolean cycling;
    private Gear previous=Gear.UNKNOWN;
    private long since;

    public Giant observe(Gear gear,int dye,long now,boolean finalSample) {
        if (previous!=gear) { since=now; previous=gear; }
        if (gear==Gear.DIAMOND) return Giant.DIAMANTE;
        if (gear==Gear.BOOTS_ONLY && (finalSample || now-since>=250)) return Giant.BIGFOOT;
        if (gear!=Gear.LEATHER || dye<0) return null;
        if (firstDye<0) firstDye=dye;
        if (firstDye!=dye) cycling=true;
        if (cycling) return Giant.LASR;
        int r=dye>>16 & 255, g=dye>>8 & 255, b=dye & 255;
        boolean pink=r>=145 && r>g+20 && b>g && b>=80;
        if (pink && (finalSample || now-since>=450)) return Giant.JOLLY_PINK;
        if (!pink && (r>g+45 && b<80 || now-since>=450)) return Giant.LASR;
        return null;
    }
}
