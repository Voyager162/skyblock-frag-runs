package dev.fragruns.core;

public final class Layout {
    public double x = 0.5, y = 0.5, scale = 1;
    public Layout() {}
    public Layout(double x, double y, double scale) { this.x=x; this.y=y; this.scale=scale; }
    public record Bounds(double x, double y, double scale, double width, double height) {
        public double localX(double screenX) { return (screenX-x)/scale; }
        public double localY(double screenY) { return (screenY-y)/scale; }
        public boolean contains(double sx, double sy) { return sx>=x && sy>=y && sx<=x+width && sy<=y+height; }
    }
    public void sanitize() {
        x = Double.isFinite(x) ? Math.clamp(x,0,1) : 0.5;
        y = Double.isFinite(y) ? Math.clamp(y,0,1) : 0.5;
        scale = Double.isFinite(scale) ? Math.clamp(scale,0.5,2.5) : 1;
    }
    public Bounds fit(int viewportW, int viewportH, int baseW, int baseH) {
        return fit(viewportW, viewportH, baseW, baseH, baseH);
    }
    Bounds fit(int viewportW, int viewportH, int baseW, int baseH, int scaleHeight) {
        sanitize();
        double effective = Math.max(0.05, Math.min(scale, Math.min((viewportW-12.0)/baseW, (viewportH-12.0)/scaleHeight)));
        double w = baseW*effective, h = baseH*effective;
        return new Bounds(6 + Math.max(0,viewportW-w-12)*x, 6 + Math.max(0,viewportH-h-12)*y, effective,w,h);
    }
    public void move(double left, double top, int vw, int vh, Bounds b) {
        x = (left-6)/Math.max(1,vw-b.width-12);
        y = (top-6)/Math.max(1,vh-b.height-12);
        sanitize();
    }
}
