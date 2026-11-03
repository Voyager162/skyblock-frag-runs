package dev.fragruns.core;

/** One action per physical press; a release, rather than a time delay, permits the next press. */
public final class PressLatch {
    private boolean held;
    public boolean press() {
        if (held) return false;
        held = true;
        return true;
    }
    public void release() { held = false; }
}
