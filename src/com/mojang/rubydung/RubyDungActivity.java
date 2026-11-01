package com.mojang.rubydung;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

/**
 * Entry point. The original RubyDung had no menu: it booted straight into a
 * random flat world. This port does the same; there is no pause menu.
 */
public class RubyDungActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.view = new GameView(this);
        this.setContentView(this.view);
        this.view.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (this.view != null) {
            this.view.saveAndReset();
        }
    }

    @Override
    protected void onDestroy() {
        if (this.view != null) {
            this.view.shutdownAndSave();
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (this.view != null) {
                this.view.shutdownAndSave();
            }
            this.finish();
            return true;
        }
        // Fallback for devices that keep focus on the activity rather than
        // the SurfaceView; otherwise hardware keys would do nothing.
        if (this.view != null && this.view.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (this.view != null && this.view.onKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        // Same fallback as the keys: some devices deliver the ball to the
        // activity instead of the focused SurfaceView.
        if (this.view != null && this.view.onTrackballEvent(event)) {
            return true;
        }
        return super.onTrackballEvent(event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && this.view != null) {
            this.view.requestFocus();
        }
    }
}
