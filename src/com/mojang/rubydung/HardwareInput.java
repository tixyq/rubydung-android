package com.mojang.rubydung;

import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Physical keyboard, mouse and gamepad input.
 *
 * Touch is the default. As soon as a keyboard, trackball, gamepad or mouse is
 * used, the on-screen controls are hidden and disabled and the game uses
 * PC-style controls. A single finger touch on the screen brings the whole
 * touch interface back, whichever device was used before.
 *
 * Keyboard (also phones with a slide-out/QWERTY keyboard, API 1+):
 *   W/A/S/D        move              Space          jump
 *   arrow keys     look around (like moving a mouse)
 *   trackball      look around (like moving a mouse); self-calibrating
 *   Enter / short trackball press   place a block (left click)
 *   Del            break a block (right click)
 *   C / long trackball press        recalibrate the trackball direction
 *
 * Android 1.0 has no mouse API. Every newer API is accessed through
 * MotionEventCompat/reflection, so loading this class on API 1 stays safe.
 */
public class HardwareInput {
    private static final int SOURCE_MOUSE = 0x00002002;
    private static final int SOURCE_GAMEPAD = 0x00000401;
    private static final int SOURCE_JOYSTICK = 0x01000010;
    private static final int SOURCE_TOUCHPAD = 0x00100008;
    /** Relative mouse delivered while pointer capture is active (Android 8+). */
    private static final int SOURCE_MOUSE_RELATIVE = 0x00020004;
    private static final int TOOL_TYPE_MOUSE = 3;

    // Key codes newer than API 1 are spelled out so the class still compiles
    // against the Android 1.x stubs. Unknown codes simply never arrive there.
    private static final int KEYCODE_FORWARD_DEL = 112;
    private static final int KEYCODE_NUMPAD_ENTER = 160;

    private static final int ACTION_HOVER_MOVE = 7;
    private static final int ACTION_HOVER_ENTER = 9;
    private static final int ACTION_HOVER_EXIT = 10;
    private static final int ACTION_BUTTON_PRESS = 11;
    private static final int ACTION_BUTTON_RELEASE = 12;

    private static final int BUTTON_PRIMARY = 1;
    private static final int BUTTON_SECONDARY = 2;

    // MotionEvent axis numbers. AXIS_X/Y are the left stick on a gamepad.
    private static final int AXIS_X = 0;
    private static final int AXIS_Y = 1;
    private static final int AXIS_Z = 11;
    private static final int AXIS_RX = 12;
    private static final int AXIS_RY = 13;
    private static final int AXIS_RZ = 14;
    private static final int AXIS_RELATIVE_X = 27;
    private static final int AXIS_RELATIVE_Y = 28;

    /** rd-132211's PC mouse sensitivity (0.15 degrees per pixel). */
    private static final float MOUSE_SENSITIVITY = 0.15F;
    private static final float PAD_DEAD_ZONE = 0.35F;
    private static final float PAD_LOOK_SPEED = 2.0F;

    /** Arrow keys look around like a mouse: degrees per second, ramping up. */
    private static final float ARROW_LOOK_MIN = 70.0F;
    private static final float ARROW_LOOK_MAX = 200.0F;
    private static final float ARROW_LOOK_RAMP = 0.8F;
    /**
     * One trackball "unit" (a MotionEvent x/y of 1.0) in mouse pixels, so it
     * is turned by MOUSE_SENSITIVITY like a real mouse: 20 px = 3 degrees.
     */
    private static final float TRACKBALL_PIXELS = 20.0F;
    /** Calibration: roll this far (trackball units) the "right" way... */
    private static final float CALIB_DISTANCE = 3.0F;
    /** ...mostly along one axis (major must beat minor by this factor). */
    private static final float CALIB_DOMINANCE = 1.6F;
    private static final long BALL_LONG_PRESS_MS = 1000L;
    private static final String PREF_BALL_ROT = "trackball_rot_";

    private final Game game;
    private final boolean[] keys = new boolean[512];

    private boolean mouseDetected;
    private boolean mouseJustDetected;
    private boolean pointerCaptured;
    private boolean hardwareMode;
    private boolean hardwareJustActivated;

    private float mouseX;
    private float mouseY;
    private float mouseDx;
    private float mouseDy;
    private float lastMouseX;
    private float lastMouseY;
    private boolean haveMousePosition;
    private boolean mouseLeftDown;
    private boolean mouseRightDown;

    private int pendingPlace;
    private int pendingBreak;

    private float padMoveX;
    private float padMoveY;
    private float padLookX;
    private float padLookY;

    private SharedPreferences prefs;
    private int displayRotation;
    /** Quarter turns from raw ball axes to screen axes; -1 = not calibrated. */
    private int ballRot = -1;
    private boolean calibrating;
    private float calibX;
    private float calibY;
    private long ballDownTime = -1L;

    private long lastUpdateNanos;
    private float arrowHeldSeconds;

    private long lastMouseEventTime = -1L;
    private int lastMouseAction = -1;
    private float lastMouseEventX;
    private float lastMouseEventY;

    public HardwareInput(Game game) {
        this.game = game;
    }

    public boolean isMouseEvent(MotionEvent event) {
        int source = MotionEventCompat.getSource(event);
        if ((source & SOURCE_MOUSE) == SOURCE_MOUSE) {
            return true;
        }
        return MotionEventCompat.getToolType(event, 0) == TOOL_TYPE_MOUSE;
    }

    public synchronized boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return false;
        }
        if (!isHardwareKey(keyCode)) {
            return false;
        }

        boolean firstPress = event.getRepeatCount() == 0;
        if (keyCode >= 0 && keyCode < this.keys.length) {
            this.keys[keyCode] = true;
        }
        boolean gameKey = isGameKey(keyCode);
        if (firstPress && gameKey) {
            if (keyCode == KeyEvent.KEYCODE_C) {
                this.startCalibration();
            } else if (this.calibrating) {
                // Any other key: stop asking, use the unrotated default.
                this.cancelCalibration(true);
            }
            // Only keys the game actually uses hide the touch interface;
            // Menu/Search/Camera must not.
            this.activateHardware();
        }
        if (firstPress) {
            if (isPlaceKey(keyCode)) {
                this.pendingPlace++;
            } else if (isBreakKey(keyCode)) {
                this.pendingBreak++;
            }
        }
        return gameKey;
    }

    public synchronized boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return false;
        }
        if (!isHardwareKey(keyCode)) {
            return false;
        }
        if (keyCode >= 0 && keyCode < this.keys.length) {
            this.keys[keyCode] = false;
        }
        return isGameKey(keyCode);
    }

    /**
     * Trackball (Android 1.0+, View.onTrackballEvent). Rolling it looks around
     * exactly like moving a mouse; a short press is a left click (place
     * block), a long press (about a second) restarts the calibration.
     * Handling the event here also stops Android from turning the ball into
     * D-pad key presses.
     *
     * Phones disagree about which way "right" is on the ball: it depends on
     * where the ball sits, on whether the system already rotated it for the
     * landscape screen, and on which landscape the phone is held. So the
     * first roll on a phone (per screen rotation) starts a calibration: an
     * arrow pointing right is shown and the player rolls the ball that way.
     * The answer is stored and used from then on.
     */
    public synchronized boolean onTrackballEvent(MotionEvent event) {
        // Only a real trackball may use this path. Some firmwares hand a mouse
        // or touchpad over as a "trackball" event; those must neither start
        // the calibration nor be rotated, so leave them to the mouse code.
        if (!isRealTrackball(event)) {
            return false;
        }
        this.activateHardware();
        if (this.ballRot < 0 && !this.calibrating) {
            this.startCalibration();
        }
        int action = event.getAction() & 0xFF;
        if (action == MotionEvent.ACTION_DOWN) {
            this.ballDownTime = event.getEventTime();
        } else if (action == MotionEvent.ACTION_UP) {
            long down = this.ballDownTime;
            this.ballDownTime = -1L;
            if (down >= 0L && !this.calibrating) {
                if (event.getEventTime() - down >= BALL_LONG_PRESS_MS) {
                    this.startCalibration();
                } else {
                    this.pendingPlace++;
                }
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            // Trackball events batch their movement as history; sum it all.
            float x = event.getX();
            float y = event.getY();
            int n = event.getHistorySize();
            for (int i = 0; i < n; i++) {
                x += event.getHistoricalX(i);
                y += event.getHistoricalY(i);
            }
            if (this.calibrating) {
                this.feedCalibration(x, y);
            } else {
                this.applyBall(x, y);
            }
        }
        return true;
    }

    private boolean isRealTrackball(MotionEvent event) {
        int source = MotionEventCompat.getSource(event);
        if ((source & SOURCE_MOUSE) == SOURCE_MOUSE
                || (source & SOURCE_MOUSE_RELATIVE) == SOURCE_MOUSE_RELATIVE
                || (source & SOURCE_TOUCHPAD) == SOURCE_TOUCHPAD) {
            return false;
        }
        return !this.isMouseEvent(event);
    }

    /** Where the calibration answers are stored (may be null in tests). */
    public synchronized void setPreferences(SharedPreferences prefs) {
        this.prefs = prefs;
        this.loadBallRotation();
    }

    /**
     * Surface rotation (0..3). The ball may be reported differently when the
     * phone is held in the other landscape, so each rotation has its own
     * calibration.
     */
    public synchronized void setDisplayRotation(int rotation) {
        if (rotation != this.displayRotation) {
            this.displayRotation = rotation;
            this.calibrating = false;
            this.loadBallRotation();
        }
    }

    public synchronized boolean isCalibratingTrackball() {
        return this.calibrating;
    }

    /** 0..1 while calibrating, -1 otherwise. For the on-screen hint. */
    public synchronized float getCalibrationProgress() {
        if (!this.calibrating) {
            return -1.0F;
        }
        float m = Math.max(Math.abs(this.calibX), Math.abs(this.calibY));
        return Math.min(1.0F, m / CALIB_DISTANCE);
    }

    private void loadBallRotation() {
        this.ballRot = this.prefs == null ? -1
                : this.prefs.getInt(PREF_BALL_ROT + this.displayRotation, -1);
    }

    private void startCalibration() {
        this.calibrating = true;
        this.calibX = 0.0F;
        this.calibY = 0.0F;
    }

    private void cancelCalibration(boolean useDefault) {
        this.calibrating = false;
        if (useDefault && this.ballRot < 0) {
            this.saveBallRotation(0);
        }
    }

    private void saveBallRotation(int rot) {
        this.ballRot = rot;
        if (this.prefs != null) {
            this.prefs.edit().putInt(PREF_BALL_ROT + this.displayRotation, rot).commit();
        }
    }

    /**
     * The player was asked to roll the ball to the right of the screen. Find
     * which raw direction that was and remember how to turn it into "right".
     */
    private void feedCalibration(float x, float y) {
        this.calibX += x;
        this.calibY += y;
        float ax = Math.abs(this.calibX);
        float ay = Math.abs(this.calibY);
        float major = Math.max(ax, ay);
        if (major < CALIB_DISTANCE) {
            return;
        }
        if (major < CALIB_DOMINANCE * Math.min(ax, ay)) {
            // Too diagonal to tell; ask again.
            this.calibX = 0.0F;
            this.calibY = 0.0F;
            return;
        }
        int rot;
        if (ax >= ay) {
            rot = this.calibX > 0.0F ? 0 : 2;
        } else {
            rot = this.calibY > 0.0F ? 1 : 3;
        }
        this.calibrating = false;
        this.saveBallRotation(rot);
    }

    /** Rotates a raw ball movement into screen space and turns the view. */
    private void applyBall(float x, float y) {
        float sx;
        float sy;
        switch (this.ballRot) {
            case 1:
                sx = y;
                sy = -x;
                break;
            case 2:
                sx = -x;
                sy = -y;
                break;
            case 3:
                sx = -y;
                sy = x;
                break;
            default:
                sx = x;
                sy = y;
                break;
        }
        this.mouseDx += sx * TRACKBALL_PIXELS;
        this.mouseDy += sy * TRACKBALL_PIXELS;
    }

    /**
     * A real finger on the screen means the touch interface is wanted again,
     * no matter whether a keyboard, trackball, gamepad or mouse hid it.
     *
     * @return true if this call switched the game back from hardware/mouse
     *         input (the caller should then release pointer capture)
     */
    public synchronized boolean onTouchInput() {
        this.calibrating = false; // asked again on the next ball roll
        if (!this.hardwareMode && !this.mouseDetected) {
            return false;
        }
        this.hardwareMode = false;
        this.hardwareJustActivated = false;
        this.mouseDetected = false;
        this.mouseJustDetected = false;
        this.pointerCaptured = false;
        this.haveMousePosition = false;
        // Drop everything the old device was holding so nothing keeps
        // walking or turning by itself under the restored touch controls.
        this.clearKeys();
        return true;
    }

    /** Mouse click delivered through onTouchEvent as a touch-style event. */
    public synchronized boolean onMouseTouch(MotionEvent event) {
        if (!isMouseEvent(event)) {
            return this.mouseDetected;
        }
        detectMouse();

        int action = event.getAction() & 0xFF;
        float x = event.getX();
        float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            updateMousePosition(x, y, false);
            int buttons = MotionEventCompat.getButtonState(event);
            if ((buttons & BUTTON_SECONDARY) != 0 && (buttons & BUTTON_PRIMARY) == 0) {
                if (!this.mouseRightDown) {
                    this.mouseRightDown = true;
                    this.pendingBreak++;
                }
            } else if (!this.mouseLeftDown) {
                this.mouseLeftDown = true;
                this.pendingPlace++;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            updateMousePosition(x, y, true);
        } else if (action == MotionEvent.ACTION_UP) {
            updateMousePosition(x, y, false);
            this.mouseLeftDown = false;
            this.mouseRightDown = false;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            this.mouseLeftDown = false;
            this.mouseRightDown = false;
            this.haveMousePosition = false;
        }
        return true;
    }

    /**
     * Handles mouse hover/buttons and gamepad sticks. onHoverEvent and
     * onGenericMotionEvent can both deliver the same hover, hence the small
     * de-duplication check.
     */
    public synchronized boolean onGenericMotionEvent(MotionEvent event) {
        int action = event.getAction() & 0xFF;
        int source = MotionEventCompat.getSource(event);

        if (isMouseEvent(event)) {
            detectMouse();
            float x = event.getX();
            float y = event.getY();

            if (action == ACTION_HOVER_ENTER) {
                updateMousePosition(x, y, false);
                return true;
            }
            if (action == ACTION_HOVER_EXIT) {
                this.haveMousePosition = false;
                return true;
            }
            if (action == ACTION_HOVER_MOVE || action == MotionEvent.ACTION_MOVE) {
                if (isDuplicateMouseEvent(action, x, y, event.getEventTime())) {
                    return true;
                }
                updateMousePosition(x, y, true);
                return true;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                updateMousePosition(x, y, false);
                int buttons = MotionEventCompat.getButtonState(event);
                if ((buttons & BUTTON_SECONDARY) != 0 && (buttons & BUTTON_PRIMARY) == 0) {
                    if (!this.mouseRightDown) {
                        this.mouseRightDown = true;
                        this.pendingBreak++;
                    }
                } else if (!this.mouseLeftDown) {
                    this.mouseLeftDown = true;
                    this.pendingPlace++;
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                updateMousePosition(x, y, false);
                this.mouseLeftDown = false;
                this.mouseRightDown = false;
                return true;
            }
            if (action == ACTION_BUTTON_PRESS) {
                updateMousePosition(x, y, false);
                int button = MotionEventCompat.getActionButton(event);
                if (button == BUTTON_SECONDARY) {
                    if (!this.mouseRightDown) {
                        this.mouseRightDown = true;
                        this.pendingBreak++;
                    }
                } else if (button == BUTTON_PRIMARY || button == 0) {
                    if (!this.mouseLeftDown) {
                        this.mouseLeftDown = true;
                        this.pendingPlace++;
                    }
                }
                return true;
            }
            if (action == ACTION_BUTTON_RELEASE) {
                int button = MotionEventCompat.getActionButton(event);
                if (button == BUTTON_SECONDARY) {
                    this.mouseRightDown = false;
                } else if (button == BUTTON_PRIMARY) {
                    this.mouseLeftDown = false;
                } else {
                    this.mouseLeftDown = false;
                    this.mouseRightDown = false;
                }
                return true;
            }
            // Scroll and other mouse actions still count as mouse detection.
            updateMousePosition(x, y, false);
            return true;
        }

        if (isGamepadSource(source)) {
            float leftX = MotionEventCompat.getAxisValue(event, AXIS_X);
            float leftY = MotionEventCompat.getAxisValue(event, AXIS_Y);
            if (leftX == 0.0F && leftY == 0.0F) {
                // getAxisValue() only exists from API 12; getX/getY still
                // expose the left stick on older generic-motion devices.
                leftX = event.getX();
                leftY = event.getY();
            }
            float rightX = pickAxis(event, AXIS_RX, AXIS_Z);
            float rightY = pickAxis(event, AXIS_RY, AXIS_RZ);

            this.padMoveX = leftX;
            this.padMoveY = leftY;
            this.padLookX = rightX;
            this.padLookY = rightY;

            if (Math.abs(leftX) > PAD_DEAD_ZONE || Math.abs(leftY) > PAD_DEAD_ZONE
                    || Math.abs(rightX) > PAD_DEAD_ZONE || Math.abs(rightY) > PAD_DEAD_ZONE) {
                this.activateHardware();
            }
            return true;
        }
        return false;
    }

    /**
     * Pointer capture (Android 8+) hides the system cursor and delivers mouse
     * movement as relative deltas, which is the PC-style behaviour. Mouse
     * coordinates here are deltas, not screen positions; touchpads use the
     * dedicated relative axes.
     */
    public synchronized boolean onCapturedPointerEvent(MotionEvent event) {
        detectMouse();
        this.pointerCaptured = true;
        int action = event.getAction() & 0xFF;

        if (action == MotionEvent.ACTION_MOVE || action == ACTION_HOVER_MOVE) {
            int source = MotionEventCompat.getSource(event);
            if ((source & SOURCE_TOUCHPAD) == SOURCE_TOUCHPAD) {
                // Captured touchpads report absolute finger positions in
                // getX/getY and the actual movement in the relative axes.
                this.mouseDx += MotionEventCompat.getAxisValue(event, AXIS_RELATIVE_X);
                this.mouseDy += MotionEventCompat.getAxisValue(event, AXIS_RELATIVE_Y);
            } else {
                this.mouseDx += event.getX();
                this.mouseDy += event.getY();
            }
            int buttons = MotionEventCompat.getButtonState(event);
            if ((buttons & BUTTON_PRIMARY) != 0 && !this.mouseLeftDown) {
                this.mouseLeftDown = true;
                this.pendingPlace++;
            }
            if ((buttons & BUTTON_SECONDARY) != 0 && !this.mouseRightDown) {
                this.mouseRightDown = true;
                this.pendingBreak++;
            }
            return true;
        }
        if (action == ACTION_BUTTON_PRESS) {
            int button = MotionEventCompat.getActionButton(event);
            if (button == BUTTON_SECONDARY) {
                if (!this.mouseRightDown) {
                    this.mouseRightDown = true;
                    this.pendingBreak++;
                }
            } else if (button == BUTTON_PRIMARY || button == 0) {
                if (!this.mouseLeftDown) {
                    this.mouseLeftDown = true;
                    this.pendingPlace++;
                }
            }
            return true;
        }
        if (action == ACTION_BUTTON_RELEASE) {
            int button = MotionEventCompat.getActionButton(event);
            if (button == BUTTON_SECONDARY) {
                this.mouseRightDown = false;
            } else if (button == BUTTON_PRIMARY) {
                this.mouseLeftDown = false;
            } else {
                this.mouseLeftDown = false;
                this.mouseRightDown = false;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            int buttons = MotionEventCompat.getButtonState(event);
            if ((buttons & BUTTON_SECONDARY) != 0 && (buttons & BUTTON_PRIMARY) == 0) {
                if (!this.mouseRightDown) {
                    this.mouseRightDown = true;
                    this.pendingBreak++;
                }
            } else if (!this.mouseLeftDown) {
                this.mouseLeftDown = true;
                this.pendingPlace++;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            this.mouseLeftDown = false;
            this.mouseRightDown = false;
            return true;
        }
        return true;
    }

    public synchronized void setPointerCaptured(boolean captured) {
        this.pointerCaptured = captured;
        if (!captured) {
            this.haveMousePosition = false;
            this.mouseLeftDown = false;
            this.mouseRightDown = false;
        }
    }

    public synchronized boolean isPointerCaptured() {
        return this.pointerCaptured;
    }

    /** Applies pending hardware input on the GL thread, once per frame. */
    public synchronized void update(Game game, TouchControls touch,
                                    int width, int height, float a) {
        boolean justMouse = this.mouseJustDetected;
        boolean justHardware = this.hardwareJustActivated;
        this.mouseJustDetected = false;
        this.hardwareJustActivated = false;
        if (justMouse || justHardware) {
            // Do not leave a hidden finger pressed or a look pointer active
            // after switching input devices.
            touch.cancelAll();
        }

        float dx = this.mouseDx;
        float dy = this.mouseDy;
        this.mouseDx = 0.0F;
        this.mouseDy = 0.0F;
        if (dx != 0.0F || dy != 0.0F) {
            // Screen/mouse Y grows downward, and positive xRot looks down.
            game.player.turn(dx * MOUSE_SENSITIVITY, dy * MOUSE_SENSITIVITY);
        }
        if (Math.abs(this.padLookX) > PAD_DEAD_ZONE
                || Math.abs(this.padLookY) > PAD_DEAD_ZONE) {
            game.player.turn(this.padLookX * PAD_LOOK_SPEED,
                    this.padLookY * PAD_LOOK_SPEED);
        }

        this.applyArrowLook(game);

        boolean forward = touch.isPressed(TouchControls.N)
                || touch.isPressed(TouchControls.NW)
                || touch.isPressed(TouchControls.NE)
                || key(KeyEvent.KEYCODE_W)
                || this.padMoveY < -PAD_DEAD_ZONE;
        boolean back = touch.isPressed(TouchControls.S)
                || touch.isPressed(TouchControls.SW)
                || touch.isPressed(TouchControls.SE)
                || key(KeyEvent.KEYCODE_S)
                || this.padMoveY > PAD_DEAD_ZONE;
        boolean left = touch.isPressed(TouchControls.W)
                || touch.isPressed(TouchControls.NW)
                || touch.isPressed(TouchControls.SW)
                || key(KeyEvent.KEYCODE_A)
                || this.padMoveX < -PAD_DEAD_ZONE;
        boolean right = touch.isPressed(TouchControls.E)
                || touch.isPressed(TouchControls.NE)
                || touch.isPressed(TouchControls.SE)
                || key(KeyEvent.KEYCODE_D)
                || this.padMoveX > PAD_DEAD_ZONE;
        boolean jump = touch.isPressed(TouchControls.JUMP)
                || key(KeyEvent.KEYCODE_SPACE)
                || key(KeyEvent.KEYCODE_BUTTON_A);

        game.input.forward = forward;
        game.input.back = back;
        game.input.left = left;
        game.input.right = right;
        game.input.jump = jump;

        if (width > 0 && height > 0) {
            if (this.pendingPlace > 0) {
                this.aim(width, height, a);
                game.placeBlock();
            }
            if (this.pendingBreak > 0) {
                this.aim(width, height, a);
                game.breakBlock();
            }
        }
        this.pendingPlace = 0;
        this.pendingBreak = 0;

        touch.visible = !this.mouseDetected && !this.hardwareMode;
    }

    /** Arrow keys turn the view like a mouse, at a speed independent of FPS. */
    private void applyArrowLook(Game game) {
        long now = System.nanoTime();
        float dt = this.lastUpdateNanos == 0L ? 0.0F
                : (now - this.lastUpdateNanos) / 1.0e9F;
        this.lastUpdateNanos = now;
        if (dt > 0.1F) {
            dt = 0.1F;
        }

        float lx = 0.0F;
        float ly = 0.0F;
        if (key(KeyEvent.KEYCODE_DPAD_LEFT)) lx -= 1.0F;
        if (key(KeyEvent.KEYCODE_DPAD_RIGHT)) lx += 1.0F;
        if (key(KeyEvent.KEYCODE_DPAD_UP)) ly -= 1.0F;
        if (key(KeyEvent.KEYCODE_DPAD_DOWN)) ly += 1.0F;

        if (lx == 0.0F && ly == 0.0F) {
            this.arrowHeldSeconds = 0.0F;
            return;
        }
        this.arrowHeldSeconds += dt;
        float t = Math.min(1.0F, this.arrowHeldSeconds / ARROW_LOOK_RAMP);
        float speed = ARROW_LOOK_MIN + (ARROW_LOOK_MAX - ARROW_LOOK_MIN) * t;
        // Same signs as mouse movement: right/down = positive.
        game.player.turn(lx * speed * dt, ly * speed * dt);
    }

    private void activateHardware() {
        if (!this.hardwareMode) {
            this.hardwareMode = true;
            this.hardwareJustActivated = true;
        }
    }

    private void aim(int width, int height, float a) {
        if (this.mouseDetected && !this.pointerCaptured) {
            this.game.updateHitAt(this.mouseX, this.mouseY, width, height, a);
        } else {
            this.game.updateHitAt(width * 0.5F, height * 0.5F, width, height, a);
        }
    }

    private boolean key(int keyCode) {
        return keyCode >= 0 && keyCode < this.keys.length && this.keys[keyCode];
    }

    private void detectMouse() {
        if (!this.mouseDetected) {
            this.mouseDetected = true;
            this.mouseJustDetected = true;
        }
    }

    private void updateMousePosition(float x, float y, boolean turn) {
        this.mouseX = x;
        this.mouseY = y;
        if (!this.haveMousePosition) {
            this.lastMouseX = x;
            this.lastMouseY = y;
            this.haveMousePosition = true;
            return;
        }
        if (turn) {
            this.mouseDx += x - this.lastMouseX;
            this.mouseDy += y - this.lastMouseY;
        }
        this.lastMouseX = x;
        this.lastMouseY = y;
    }

    private boolean isDuplicateMouseEvent(int action, float x, float y, long time) {
        if (time == this.lastMouseEventTime
                && action == this.lastMouseAction
                && x == this.lastMouseEventX
                && y == this.lastMouseEventY) {
            return true;
        }
        this.lastMouseEventTime = time;
        this.lastMouseAction = action;
        this.lastMouseEventX = x;
        this.lastMouseEventY = y;
        return false;
    }

    private static float pickAxis(MotionEvent event, int primary, int fallback) {
        float value = MotionEventCompat.getAxisValue(event, primary);
        if (Math.abs(value) < 0.01F) {
            value = MotionEventCompat.getAxisValue(event, fallback);
        }
        return value;
    }

    private static boolean isGamepadSource(int source) {
        return (source & SOURCE_GAMEPAD) == SOURCE_GAMEPAD
                || (source & SOURCE_JOYSTICK) == SOURCE_JOYSTICK;
    }

    private static boolean isHardwareKey(int keyCode) {
        return keyCode != KeyEvent.KEYCODE_VOLUME_UP
                && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
                && keyCode != KeyEvent.KEYCODE_POWER;
    }

    private static boolean isPlaceKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER // trackball click
                || keyCode == KeyEvent.KEYCODE_BUTTON_X;
    }

    private static boolean isBreakKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DEL
                || keyCode == KEYCODE_FORWARD_DEL
                || keyCode == KeyEvent.KEYCODE_BUTTON_B;
    }

    private static boolean isGameKey(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_SPACE
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_BUTTON_A
                || isPlaceKey(keyCode)
                || isBreakKey(keyCode);
    }

    public synchronized void clearKeys() {
        for (int i = 0; i < this.keys.length; i++) {
            this.keys[i] = false;
        }
        this.padMoveX = 0.0F;
        this.padMoveY = 0.0F;
        this.padLookX = 0.0F;
        this.padLookY = 0.0F;
        this.mouseLeftDown = false;
        this.mouseRightDown = false;
        this.pendingPlace = 0;
        this.pendingBreak = 0;
        this.mouseDx = 0.0F;
        this.mouseDy = 0.0F;
        this.arrowHeldSeconds = 0.0F;
    }

    public synchronized boolean isMouseDetected() {
        return this.mouseDetected;
    }

    public synchronized boolean isHardwareMode() {
        return this.hardwareMode;
    }

    public synchronized boolean touchControlsVisible() {
        return !this.mouseDetected && !this.hardwareMode;
    }

    public synchronized float getMouseX() {
        return this.mouseX;
    }

    public synchronized float getMouseY() {
        return this.mouseY;
    }
}
