package com.mojang.rubydung;

/** Movement state filled in by the touch controls. */
public class InputState {
    public boolean forward;
    public boolean back;
    public boolean left;
    public boolean right;
    public boolean jump;

    public void clear() {
        forward = false;
        back = false;
        left = false;
        right = false;
        jump = false;
    }
}
