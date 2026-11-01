package com.mojang.rubydung;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Process;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

/**
 * SurfaceView with a hand-written EGL 1.0 / OpenGL ES 1.0 context.
 *
 * GLSurfaceView was only added in Android 1.5 (API 3), so it is deliberately
 * not used. Everything here exists in Android 1.0.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "RubyDung";
    /** EGL_OPENGL_ES_BIT; EGL10 on Android does not expose this constant. */
    private static final int EGL_OPENGL_ES_BIT = 0x0001;
    private static final long CHUNK_BUILD_BUDGET_NANOS = 3500000L;

    private volatile Thread thread;
    private volatile boolean running;
    /** Set when Android backgrounds the activity; handled on the GL thread. */
    private volatile boolean saveRequested;
    private volatile boolean resetRequested;

    private volatile int surfaceWidth;
    private volatile int surfaceHeight;

    private volatile Game game;
    private volatile Renderer renderer;
    private volatile TouchControls controls;
    private volatile HardwareInput hardware;
    private boolean pointerCaptureRequested;
    private boolean pointerCaptureUnavailable;

    private ArrayList<TouchEvent> events = new ArrayList<TouchEvent>();
    private final Object eventLock = new Object();

    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLSurface eglSurface;
    private EGLContext eglContext;

    private static class TouchEvent {
        final int action;
        final int pointerId;
        final float x;
        final float y;
        final long time;

        TouchEvent(int action, int pointerId, float x, float y, long time) {
            this.action = action;
            this.pointerId = pointerId;
            this.x = x;
            this.y = y;
            this.time = time;
        }
    }

    public GameView(Context context) {
        super(context);
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.RGB_565);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        this.surfaceWidth = getWidth();
        this.surfaceHeight = getHeight();
        if (this.thread == null) {
            this.running = true;
            this.thread = new Thread(this, "RubyDung");
            this.thread.start();
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.surfaceWidth = width;
        this.surfaceHeight = height;
        Renderer r = this.renderer;
        if (r != null) {
            r.onSizeChanged(width, height);
        }
        TouchControls c = this.controls;
        if (c != null) {
            c.setSize(width, height);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        this.stopThread();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.surfaceWidth = w;
        this.surfaceHeight = h;
        Renderer r = this.renderer;
        if (r != null) {
            r.onSizeChanged(w, h);
        }
        TouchControls c = this.controls;
        if (c != null) {
            c.setSize(w, h);
        }
    }

    private void stopThread() {
        this.running = false;
        Thread t = this.thread;
        this.thread = null;
        if (t != null) {
            try {
                t.join(4000L);
            } catch (InterruptedException ignored) {
            }
        }
        Game g = this.game;
        if (g != null) {
            g.save();
        }
    }

    /**
     * Android lifecycle only. It requests a save on the GL thread, where the
     * block array is not being edited, and clears any touches that Android
     * cancelled while the window lost focus. There is no pause screen.
     */
    public void saveAndReset() {
        HardwareInput hw = this.hardware;
        if (hw != null) {
            hw.clearKeys();
        }
        this.releaseMouseCapture();
        this.saveRequested = true;
        this.resetRequested = true;
        if (this.thread == null) {
            this.saveNow();
        }
    }

    public void saveNow() {
        Game g = this.game;
        if (g != null) {
            g.save();
        }
    }

    /**
     * Called on the UI thread when the activity is really going away. Joins
     * the GL thread first so the block array cannot change while saving.
     */
    public void shutdownAndSave() {
        this.releaseMouseCapture();
        this.stopThread();
        this.saveNow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        HardwareInput hw = this.hardware;
        if (hw != null) {
            if (hw.isMouseEvent(event)) {
                hw.onMouseTouch(event);
                this.requestMouseCapture();
                return true;
            }
            // A real finger always brings the touch interface back, whether
            // a keyboard, trackball, gamepad or mouse had hidden it. If a
            // mouse had captured the pointer, give the cursor back too.
            if (hw.onTouchInput()) {
                this.releaseMouseCapture();
            }
        }

        int action = event.getAction();
        int masked = action & 0xFF;
        int index = (action >> 8) & 0xFF;
        long time = event.getEventTime();

        if (masked == TouchControls.ACTION_MOVE) {
            int count = MotionEventCompat.getPointerCount(event);
            for (int i = 0; i < count; i++) {
                this.enqueue(new TouchEvent(TouchControls.ACTION_MOVE,
                        MotionEventCompat.getPointerId(event, i),
                        MotionEventCompat.getX(event, i),
                        MotionEventCompat.getY(event, i), time));
            }
        } else if (masked == TouchControls.ACTION_DOWN
                || masked == TouchControls.ACTION_POINTER_DOWN
                || masked == TouchControls.ACTION_UP
                || masked == TouchControls.ACTION_POINTER_UP) {
            this.enqueue(new TouchEvent(masked,
                    MotionEventCompat.getPointerId(event, index),
                    MotionEventCompat.getX(event, index),
                    MotionEventCompat.getY(event, index), time));
        } else if (masked == TouchControls.ACTION_CANCEL) {
            this.enqueue(new TouchEvent(TouchControls.ACTION_CANCEL, -1, 0.0F, 0.0F, time));
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return super.onKeyDown(keyCode, event);
        }
        HardwareInput hw = this.hardware;
        if (hw != null && hw.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            return super.onKeyUp(keyCode, event);
        }
        HardwareInput hw = this.hardware;
        if (hw != null && hw.onKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Surface.ROTATION_0..3 of the screen (Display.getOrientation is API 1). */
    private int displayRotation() {
        try {
            WindowManager wm = (WindowManager) this.getContext()
                    .getSystemService(Context.WINDOW_SERVICE);
            return wm.getDefaultDisplay().getOrientation();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Trackball roll = mouse look, trackball press = place block. */
    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        HardwareInput hw = this.hardware;
        if (hw != null) {
            hw.setDisplayRotation(this.displayRotation());
        }
        if (hw != null && hw.onTrackballEvent(event)) {
            return true;
        }
        return super.onTrackballEvent(event);
    }

    /**
     * Mouse hover/buttons and gamepad sticks. The superclass methods appeared
     * after Android 1.0, so they are deliberately not called here.
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        HardwareInput hw = this.hardware;
        if (hw == null) {
            return false;
        }
        boolean handled = hw.onGenericMotionEvent(event);
        if (hw.isMouseDetected()) {
            this.requestMouseCapture();
        }
        return handled;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        HardwareInput hw = this.hardware;
        if (hw != null && hw.isMouseEvent(event)) {
            // Some devices deliver hover only here; onGenericMotionEvent may
            // see the same event, and HardwareInput de-duplicates it.
            hw.onGenericMotionEvent(event);
            this.requestMouseCapture();
            return true;
        }
        return false;
    }

    /**
     * Pointer capture is Android 8.0+ only, so it is requested reflectively.
     * If it is unavailable (or refused), the game keeps working with normal
     * hover mouse-look.
     */
    private void requestMouseCapture() {
        if (this.pointerCaptureRequested || this.pointerCaptureUnavailable) {
            return;
        }
        try {
            Method method = GameView.class.getMethod("requestPointerCapture", new Class[0]);
            this.pointerCaptureRequested = true;
            method.invoke(this, new Object[0]);
        } catch (NoSuchMethodException e) {
            this.pointerCaptureUnavailable = true;
        } catch (Throwable t) {
            this.pointerCaptureRequested = false;
        }
    }

    private void releaseMouseCapture() {
        if (!this.pointerCaptureRequested) {
            return;
        }
        this.pointerCaptureRequested = false;
        try {
            Method method = GameView.class.getMethod("releasePointerCapture", new Class[0]);
            method.invoke(this, new Object[0]);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onCapturedPointerEvent(MotionEvent event) {
        HardwareInput hw = this.hardware;
        return hw != null && hw.onCapturedPointerEvent(event);
    }

    @Override
    public void onPointerCaptureChange(boolean hasCapture) {
        HardwareInput hw = this.hardware;
        if (hw != null) {
            hw.setPointerCaptured(hasCapture);
        }
        if (!hasCapture) {
            this.pointerCaptureRequested = false;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        HardwareInput hw = this.hardware;
        if (hw != null && !hasWindowFocus) {
            hw.clearKeys();
            this.releaseMouseCapture();
        }
    }

    private void enqueue(TouchEvent event) {
        synchronized (this.eventLock) {
            if (this.events.size() > 256) {
                this.events.clear();
            }
            this.events.add(event);
        }
    }

    public void run() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        } catch (Throwable ignored) {
        }
        try {
            this.initEgl();
            GL10 gl = (GL10) this.eglContext.getGL();

            Renderer r = new Renderer(this.getContext().getAssets());
            r.onSurfaceCreated(gl, this.surfaceWidth, this.surfaceHeight);
            this.renderer = r;

            Game g = this.game;
            if (g == null) {
                g = new Game(this.getContext().getFilesDir());
                this.game = g;
            }
            TouchControls c = new TouchControls(g);
            c.setSize(this.surfaceWidth, this.surfaceHeight);
            this.controls = c;
            HardwareInput hw = new HardwareInput(g);
            hw.setPreferences(this.getContext().getSharedPreferences("rubydung_input", 0));
            hw.setDisplayRotation(this.displayRotation());
            this.hardware = hw;

            Timer timer = new Timer();
            long lastFrame = System.nanoTime();

            while (this.running) {
                // surfaceChanged can race with the start of this thread. Keep
                // the renderer and touch layout in sync every frame; these are
                // just int/float assignments.
                if (this.surfaceWidth > 0 && this.surfaceHeight > 0) {
                    r.onSizeChanged(this.surfaceWidth, this.surfaceHeight);
                    if (c.width != this.surfaceWidth || c.height != this.surfaceHeight) {
                        c.setSize(this.surfaceWidth, this.surfaceHeight);
                    }
                }

                ArrayList<TouchEvent> batch = null;
                synchronized (this.eventLock) {
                    if (!this.events.isEmpty()) {
                        batch = this.events;
                        this.events = new ArrayList<TouchEvent>();
                    }
                }
                if (batch != null) {
                    for (int i = 0; i < batch.size(); i++) {
                        TouchEvent e = batch.get(i);
                        c.handleTouch(e.action, e.pointerId, e.x, e.y, e.time);
                    }
                }

                if (this.resetRequested) {
                    this.resetRequested = false;
                    c.cancelAll();
                }
                if (this.saveRequested) {
                    this.saveRequested = false;
                    g.save();
                }

                timer.advance();
                int ticks = timer.ticks;
                hw.update(g, c, c.width, c.height, timer.a);
                for (int i = 0; i < ticks; i++) {
                    c.tick();
                    g.tick();
                }
                g.updateWorld(CHUNK_BUILD_BUDGET_NANOS);

                // MCPE freeform controls: the targeted block is under the
                // finger (the centre of the feedback circle), not the centre
                // of the screen. A mouse aims at the pointer; keyboard/gamepad
                // aim through the centre.
                if (hw.isMouseDetected()) {
                    if (hw.isPointerCaptured()) {
                        g.updateHitAt(c.width * 0.5F, c.height * 0.5F,
                                c.width, c.height, timer.a);
                    } else {
                        g.updateHitAt(hw.getMouseX(), hw.getMouseY(),
                                c.width, c.height, timer.a);
                    }
                } else if (c.isLooking()) {
                    g.updateHitAt(c.feedbackX, c.feedbackY, c.width, c.height, timer.a);
                } else if (hw.isHardwareMode()) {
                    g.updateHitAt(c.width * 0.5F, c.height * 0.5F,
                            c.width, c.height, timer.a);
                } else {
                    g.hit = null;
                }

                r.render(gl, g, c, timer.a, hw.getCalibrationProgress());

                if (!this.egl.eglSwapBuffers(this.eglDisplay, this.eglSurface)) {
                    Log.w(TAG, "eglSwapBuffers failed: " + this.egl.eglGetError());
                }

                long now = System.nanoTime();
                long elapsed = now - lastFrame;
                lastFrame = now;
                long sleep = 16000000L - elapsed;
                if (sleep > 0L) {
                    try {
                        Thread.sleep(sleep / 1000000L, (int) (sleep % 1000000L));
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "render loop failed", t);
        } finally {
            Game g = this.game;
            if (g != null) {
                g.save();
            }
            this.cleanupEgl();
            this.thread = null;
        }
    }

    private void initEgl() {
        this.egl = (EGL10) EGLContext.getEGL();
        this.eglDisplay = this.egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (this.eglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed");
        }
        int[] version = new int[2];
        if (!this.egl.eglInitialize(this.eglDisplay, version)) {
            throw new RuntimeException("eglInitialize failed: " + this.egl.eglGetError());
        }
        int[][] attempts = this.configAttempts();
        for (int i = 0; i < attempts.length; i++) {
            EGLConfig config = this.chooseConfig(attempts[i]);
            if (config != null && this.createContextAndSurface(config)) {
                return;
            }
        }
        throw new RuntimeException("no usable EGL config: " + this.egl.eglGetError());
    }

    private boolean createContextAndSurface(EGLConfig config) {
        EGLContext context = this.egl.eglCreateContext(this.eglDisplay, config,
                EGL10.EGL_NO_CONTEXT, new int[]{EGL10.EGL_NONE});
        if (context == null || context == EGL10.EGL_NO_CONTEXT) {
            return false;
        }
        EGLSurface surface = this.egl.eglCreateWindowSurface(this.eglDisplay, config,
                this.getHolder(), null);
        if (surface == null || surface == EGL10.EGL_NO_SURFACE) {
            this.egl.eglDestroyContext(this.eglDisplay, context);
            return false;
        }
        if (!this.egl.eglMakeCurrent(this.eglDisplay, surface, surface, context)) {
            this.egl.eglDestroySurface(this.eglDisplay, surface);
            this.egl.eglDestroyContext(this.eglDisplay, context);
            return false;
        }
        this.eglContext = context;
        this.eglSurface = surface;
        return true;
    }

    private int[][] configAttempts() {
        return new int[][]{
                {
                        EGL10.EGL_RED_SIZE, 5,
                        EGL10.EGL_GREEN_SIZE, 6,
                        EGL10.EGL_BLUE_SIZE, 5,
                        EGL10.EGL_DEPTH_SIZE, 24,
                        EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                        EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES_BIT,
                        EGL10.EGL_NONE
                },
                {
                        EGL10.EGL_RED_SIZE, 5,
                        EGL10.EGL_GREEN_SIZE, 6,
                        EGL10.EGL_BLUE_SIZE, 5,
                        EGL10.EGL_DEPTH_SIZE, 16,
                        EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                        EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES_BIT,
                        EGL10.EGL_NONE
                },
                {
                        EGL10.EGL_RED_SIZE, 5,
                        EGL10.EGL_GREEN_SIZE, 6,
                        EGL10.EGL_BLUE_SIZE, 5,
                        EGL10.EGL_DEPTH_SIZE, 16,
                        EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                        EGL10.EGL_NONE
                },
                {
                        EGL10.EGL_DEPTH_SIZE, 16,
                        EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                        EGL10.EGL_NONE
                }
        };
    }

    private EGLConfig chooseConfig(int[] attribs) {
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        boolean ok = this.egl.eglChooseConfig(this.eglDisplay, attribs, configs, 1, count);
        if (ok && count[0] > 0) {
            return configs[0];
        }
        return null;
    }

    private void cleanupEgl() {
        if (this.egl != null && this.eglDisplay != null
                && this.eglDisplay != EGL10.EGL_NO_DISPLAY) {
            this.egl.eglMakeCurrent(this.eglDisplay, EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            if (this.eglSurface != null && this.eglSurface != EGL10.EGL_NO_SURFACE) {
                this.egl.eglDestroySurface(this.eglDisplay, this.eglSurface);
            }
            if (this.eglContext != null && this.eglContext != EGL10.EGL_NO_CONTEXT) {
                this.egl.eglDestroyContext(this.eglDisplay, this.eglContext);
            }
            this.egl.eglTerminate(this.eglDisplay);
        }
        this.eglSurface = null;
        this.eglContext = null;
        this.eglDisplay = null;
    }
}
