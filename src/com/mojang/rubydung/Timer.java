package com.mojang.rubydung;

/**
 * Fixed 60 Hz tick clock. The original used an LWJGL timer which returned
 * how many 1/60 s steps had elapsed; this is the same idea with nanoTime.
 */
public class Timer {
    /**
     * Maximum simulation steps per frame. Capping catch-up keeps a slow device
     * from building a huge burst of chunks after a long frame.
     */
    private static final int MAX_TICKS_PER_FRAME = 6;

    private long last = System.nanoTime();
    private double accumulator;
    public int ticks;
    public float a;

    public void advance() {
        long now = System.nanoTime();
        double elapsed = (now - last) / 1000000000.0;
        last = now;
        if (elapsed < 0.0) {
            elapsed = 0.0;
        }
        if (elapsed > 0.25) {
            elapsed = 0.25;
        }
        accumulator += elapsed;
        ticks = 0;
        while (accumulator >= 1.0 / 60.0 && ticks < MAX_TICKS_PER_FRAME) {
            ticks++;
            accumulator -= 1.0 / 60.0;
        }
        if (ticks >= MAX_TICKS_PER_FRAME && accumulator >= 1.0 / 60.0) {
            accumulator = 0.0;
        }
        a = (float) (accumulator * 60.0);
        if (a < 0.0F) {
            a = 0.0F;
        }
        if (a > 1.0F) {
            a = 1.0F;
        }
    }
}
