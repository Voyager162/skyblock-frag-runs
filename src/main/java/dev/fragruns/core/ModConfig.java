package dev.fragruns.core;

public final class ModConfig {
    public int schema = 1;
    public Layout dashboard = new Layout(0.5,0.5,1);
    public Layout hud = new Layout(0,0.35,0.85);
    public boolean showHud = true;
    public boolean includeSalvage = true;
    public boolean expectedFragmentProfit = true;
    public boolean showStoppedHud = false;
    public double bazaarTaxPercent = 1.25;
    public int priceRefreshSeconds = 60;
    public void sanitize() {
        if (dashboard == null) dashboard = new Layout();
        if (hud == null) hud = new Layout(0,0.35,0.85);
        dashboard.sanitize(); hud.sanitize();
        bazaarTaxPercent = Double.isFinite(bazaarTaxPercent) ? Math.clamp(bazaarTaxPercent,0,10) : 1.25;
        priceRefreshSeconds = Math.clamp(priceRefreshSeconds,30,600);
    }
}
