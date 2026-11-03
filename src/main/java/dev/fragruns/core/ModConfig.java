package dev.fragruns.core;

public final class ModConfig {
    public static final int DEFAULT_WARP_DELAY_MS=1_500, MAX_WARP_DELAY_MS=10_000, WARP_DELAY_STEP_MS=100;
    public int schema = 1;
    public Layout dashboard = new Layout(0.5,0.5,1);
    public Layout hud = new Layout(0,0.35,0.85);
    public HudSettings hudSections = new HudSettings();
    public boolean showHud = true;
    public boolean includeSalvage = true;
    public boolean expectedFragmentProfit = true;
    public boolean showStoppedHud = false;
    public boolean warpOnGiantKill = false;
    public int warpDelayMs = DEFAULT_WARP_DELAY_MS;
    public boolean queueSoloF7 = false;
    public boolean showRunReport = true;
    public int reportSeconds = 4;
    public double bazaarTaxPercent = 1.25;
    public int priceRefreshSeconds = 60;
    public void sanitize() {
        if (dashboard == null) dashboard = new Layout();
        if (hud == null) hud = new Layout(0,0.35,0.85);
        if (hudSections == null) hudSections = new HudSettings();
        hudSections.sanitize();
        dashboard.sanitize(); hud.sanitize();
        bazaarTaxPercent = Double.isFinite(bazaarTaxPercent) ? Math.clamp(bazaarTaxPercent,0,10) : 1.25;
        priceRefreshSeconds = Math.clamp(priceRefreshSeconds,30,600);
        reportSeconds = Math.clamp(reportSeconds,1,30);
        int boundedDelay=Math.clamp(warpDelayMs,0,MAX_WARP_DELAY_MS);
        warpDelayMs=((boundedDelay+WARP_DELAY_STEP_MS/2)/WARP_DELAY_STEP_MS)*WARP_DELAY_STEP_MS;
    }
}
