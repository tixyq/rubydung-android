package com.mojang.rubydung;

import android.os.SystemClock;

/**
 * MCPE 0.6.1 style touch controls, ported to Java:
 *
 *  - a 3x3 D-pad in the lower left; jump is the centre key, the four corners
 *    are diagonals;
 *  - drag anywhere else to look;
 *  - a short tap places a block, a 400 ms stationary hold destroys one and
 *    keeps destroying while it is held;
 *  - a white MCPE feedback ring appears under the look finger while holding;
 *    there is no centre crosshair, matching the MCPE touch interface.
 *
 * rd-132211 had no pause menu, so this port has no pause button or pause
 * screen either. Only the control art comes from MCPE 0.6.1 (gui.png); the
 * world textures remain the original rd-132211 terrain.png.
 */
public class TouchControls {
    public static final int NONE = 0;
    public static final int N = 1;
    public static final int NE = 2;
    public static final int E = 3;
    public static final int SE = 4;
    public static final int S = 5;
    public static final int SW = 6;
    public static final int W = 7;
    public static final int NW = 8;
    public static final int JUMP = 9;

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;

    private static final float SENSITIVITY = 0.30F;
    private static final long HOLD_MS = 400L;
    private static final long BREAK_REPEAT_MS = 250L;
    private static final float TAP_MOVE_LIMIT = 14.0F;

    private final Game game;
    private final InputState input;
    public final int[] pressCount = new int[10];
    private final Pointer[] pointers = new Pointer[32];
    private Pointer lookPointer;

    public int width;
    public int height;
    /** False while a hardware keyboard, gamepad or mouse is being used. */
    public boolean visible = true;
    public float padX;
    public float padY;
    public float btn;
    public float breakProgress;
    public boolean destroying;

    /** MCPE-style feedback circle, centred on the active look finger. */
    public boolean feedbackActive;
    public float feedbackX;
    public float feedbackY;

    private static class Pointer {
        int id;
        float downX;
        float downY;
        float lastX;
        float lastY;
        long downTime;
        float totalMove;
        int area;
        int mode; // 0 = deciding, 1 = turning, 2 = destroying
        int moves;
        boolean isPad;
        boolean isLook;
        long nextBreak;
    }

    public TouchControls(Game game) {
        this.game = game;
        this.input = game.input;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        this.btn = Math.min(width, height) * 0.15F;
        if (this.btn < 34.0F) {
            this.btn = 34.0F;
        }
        if (this.btn > 150.0F) {
            this.btn = 150.0F;
        }
        this.padX = 10.0F;
        this.padY = height - 3.0F * this.btn - 10.0F;
    }

    public boolean isPressed(int area) {
        return this.pressCount[area] > 0;
    }

    /** True while a finger is down in the look/interaction area. */
    public boolean isLooking() {
        return this.lookPointer != null;
    }

    /** Block focus is the centre of the feedback circle, not screen centre. */
    private void updateHitAtFinger() {
        this.game.updateHitAt(this.feedbackX, this.feedbackY, this.width, this.height);
    }

    public void handleTouch(int action, int pointerId, float x, float y, long time) {
        if (action == ACTION_CANCEL) {
            this.cancelAll();
            return;
        }

        if (action == ACTION_DOWN || action == ACTION_POINTER_DOWN) {
            this.pointerDown(pointerId, x, y, time);
        } else if (action == ACTION_MOVE) {
            this.pointerMove(pointerId, x, y);
        } else if (action == ACTION_UP || action == ACTION_POINTER_UP) {
            this.pointerUp(pointerId, x, y, time);
        }
        this.updateInput();
    }

    private void pointerDown(int id, float x, float y, long time) {
        Pointer p = new Pointer();
        p.id = id;
        p.downX = x;
        p.downY = y;
        p.lastX = x;
        p.lastY = y;
        p.downTime = time;
        p.mode = 0;

        int area = this.areaAt(x, y);
        if (area != NONE) {
            p.isPad = true;
            p.area = area;
            this.pressCount[area]++;
        } else {
            p.isLook = true;
            if (this.lookPointer == null) {
                this.lookPointer = p;
                this.feedbackX = x;
                this.feedbackY = y;
                this.feedbackActive = true;
            }
        }
        this.pointers[id & 31] = p;
    }

    private void pointerMove(int id, float x, float y) {
        Pointer p = this.pointers[id & 31];
        if (p == null) {
            return;
        }
        if (p.isPad) {
            int area = this.areaAt(x, y);
            if (area != p.area) {
                if (p.area != NONE) {
                    this.pressCount[p.area]--;
                    if (this.pressCount[p.area] < 0) {
                        this.pressCount[p.area] = 0;
                    }
                }
                p.area = area;
                if (area != NONE) {
                    this.pressCount[area]++;
                }
            }
        } else if (p.isLook && p == this.lookPointer) {
            this.feedbackX = x;
            this.feedbackY = y;
            if (p.moves == 0) {
                // Ignore the first move event; it usually contains the jump
                // between "finger touched down" and "finger is now here".
                p.lastX = x;
                p.lastY = y;
                p.moves = 1;
            } else {
                float dx = x - p.lastX;
                float dy = y - p.lastY;
                this.game.player.turn(dx * SENSITIVITY, dy * SENSITIVITY);
                p.totalMove += Math.abs(dx) + Math.abs(dy);
                p.lastX = x;
                p.lastY = y;
            }
        }
    }

    private void pointerUp(int id, float x, float y, long time) {
        Pointer p = this.pointers[id & 31];
        if (p == null) {
            return;
        }
        this.pointers[id & 31] = null;

        if (p.isPad) {
            if (p.area != NONE) {
                this.pressCount[p.area]--;
                if (this.pressCount[p.area] < 0) {
                    this.pressCount[p.area] = 0;
                }
            }
        }
        if (p.isLook && p == this.lookPointer) {
            // ACTION_UP carries the final finger position; use it so a tap
            // targets the block under the finger, not the last MOVE event.
            this.feedbackX = x;
            this.feedbackY = y;
            if (p.mode == 0 && p.totalMove < TAP_MOVE_LIMIT) {
                this.updateHitAtFinger();
                if (time - p.downTime >= HOLD_MS) {
                    // The release beat the next tick; honour the hold.
                    this.game.breakBlock();
                } else {
                    this.game.placeBlock();
                }
            }
            this.lookPointer = null;
            this.breakProgress = 0.0F;
            this.destroying = false;
            this.feedbackActive = false;
        }
    }

    /** Called once per 1/60 s tick. */
    public void tick() {
        long now = SystemClock.uptimeMillis();
        if (this.lookPointer == null) {
            this.breakProgress = 0.0F;
            this.destroying = false;
            this.feedbackActive = false;
            return;
        }
        Pointer p = this.lookPointer;
        long held = now - p.downTime;

        if (p.mode == 0) {
            if (p.totalMove > TAP_MOVE_LIMIT) {
                p.mode = 1;
                this.breakProgress = 0.0F;
            } else if (held >= HOLD_MS) {
                p.mode = 2;
                this.destroying = true;
                this.updateHitAtFinger();
                this.game.breakBlock();
                p.nextBreak = now + BREAK_REPEAT_MS;
                this.breakProgress = 1.0F;
            } else {
                this.breakProgress = held / (float) HOLD_MS;
            }
        } else if (p.mode == 2) {
            if (p.totalMove > TAP_MOVE_LIMIT * 2.0F) {
                p.mode = 1;
                this.destroying = false;
                this.breakProgress = 0.0F;
            } else if (now >= p.nextBreak) {
                this.updateHitAtFinger();
                this.game.breakBlock();
                p.nextBreak = now + BREAK_REPEAT_MS;
                this.breakProgress = 1.0F;
            }
        }
        this.feedbackActive = p.mode != 1;
    }

    private void updateInput() {
        this.input.forward = this.pressCount[N] > 0 || this.pressCount[NW] > 0 || this.pressCount[NE] > 0;
        this.input.back = this.pressCount[S] > 0 || this.pressCount[SW] > 0 || this.pressCount[SE] > 0;
        this.input.left = this.pressCount[W] > 0 || this.pressCount[NW] > 0 || this.pressCount[SW] > 0;
        this.input.right = this.pressCount[E] > 0 || this.pressCount[NE] > 0 || this.pressCount[SE] > 0;
        this.input.jump = this.pressCount[JUMP] > 0;
    }

    public void cancelAll() {
        for (int i = 0; i < this.pressCount.length; i++) {
            this.pressCount[i] = 0;
        }
        for (int i = 0; i < this.pointers.length; i++) {
            this.pointers[i] = null;
        }
        this.lookPointer = null;
        this.input.clear();
        this.breakProgress = 0.0F;
        this.destroying = false;
        this.feedbackActive = false;
    }

    public int areaAt(float x, float y) {
        if (this.btn > 1.0F
                && x >= this.padX && x < this.padX + 3.0F * this.btn
                && y >= this.padY && y < this.padY + 3.0F * this.btn) {
            int col = (int) ((x - this.padX) / this.btn);
            int row = (int) ((y - this.padY) / this.btn);
            if (col < 0) col = 0;
            if (col > 2) col = 2;
            if (row < 0) row = 0;
            if (row > 2) row = 2;
            if (row == 0) {
                return col == 0 ? NW : (col == 1 ? N : NE);
            }
            if (row == 1) {
                return col == 0 ? W : (col == 1 ? JUMP : E);
            }
            return col == 0 ? SW : (col == 1 ? S : SE);
        }
        return NONE;
    }
}
