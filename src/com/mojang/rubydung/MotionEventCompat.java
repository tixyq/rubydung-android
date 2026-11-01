package com.mojang.rubydung;

import android.view.MotionEvent;
import java.lang.reflect.Method;

/**
 * Multi-touch arrived in Android 1.5 (API 5). Android 1.0/1.1 only have
 * getX()/getY(), so pointer methods are looked up reflectively once and the
 * class degrades to single-touch when they are missing.
 */
public class MotionEventCompat {
    private static Method mPointerCount;
    private static Method mPointerId;
    private static Method mX;
    private static Method mY;
    private static Method mSource;
    private static Method mToolType;
    private static Method mAxisValue;
    private static Method mButtonState;
    private static Method mActionButton;

    static {
        // Look each method up independently. Newer input APIs must not disable
        // multi-touch on Android 1.5-2.2 just because, for example, mouse
        // methods do not exist yet.
        mPointerCount = findMethod("getPointerCount", new Class[0]);
        mPointerId = findMethod("getPointerId", new Class[]{int.class});
        mX = findMethod("getX", new Class[]{int.class});
        mY = findMethod("getY", new Class[]{int.class});
        mSource = findMethod("getSource", new Class[0]);
        mToolType = findMethod("getToolType", new Class[]{int.class});
        mAxisValue = findMethod("getAxisValue", new Class[]{int.class});
        mButtonState = findMethod("getButtonState", new Class[0]);
        mActionButton = findMethod("getActionButton", new Class[0]);
    }

    private static Method findMethod(String name, Class[] parameterTypes) {
        try {
            return MotionEvent.class.getMethod(name, parameterTypes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int getPointerCount(MotionEvent event) {
        if (mPointerCount != null) {
            try {
                Integer value = (Integer) mPointerCount.invoke(event, new Object[0]);
                return value.intValue();
            } catch (Throwable ignored) {
            }
        }
        return 1;
    }

    public static int getPointerId(MotionEvent event, int index) {
        if (mPointerId != null) {
            try {
                Integer value = (Integer) mPointerId.invoke(event,
                        new Object[]{Integer.valueOf(index)});
                return value.intValue();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    public static float getX(MotionEvent event, int index) {
        if (mX != null) {
            try {
                Float value = (Float) mX.invoke(event, new Object[]{Integer.valueOf(index)});
                return value.floatValue();
            } catch (Throwable ignored) {
            }
        }
        return event.getX();
    }

    public static float getY(MotionEvent event, int index) {
        if (mY != null) {
            try {
                Float value = (Float) mY.invoke(event, new Object[]{Integer.valueOf(index)});
                return value.floatValue();
            } catch (Throwable ignored) {
            }
        }
        return event.getY();
    }

    /** Input source (mouse, gamepad, ...), or 0 on old Android versions. */
    public static int getSource(MotionEvent event) {
        if (mSource != null) {
            try {
                Integer value = (Integer) mSource.invoke(event, new Object[0]);
                return value.intValue();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    /** Tool type (finger, mouse, ...), or -1 when the API is unavailable. */
    public static int getToolType(MotionEvent event, int index) {
        if (mToolType != null) {
            try {
                Integer value = (Integer) mToolType.invoke(event,
                        new Object[]{Integer.valueOf(index)});
                return value.intValue();
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    /** Generic-motion axis value, or 0 when the API is unavailable. */
    public static float getAxisValue(MotionEvent event, int axis) {
        if (mAxisValue != null) {
            try {
                Float value = (Float) mAxisValue.invoke(event,
                        new Object[]{Integer.valueOf(axis)});
                return value.floatValue();
            } catch (Throwable ignored) {
            }
        }
        return 0.0F;
    }

    /** Current mouse/gamepad button state, or 0 when unavailable. */
    public static int getButtonState(MotionEvent event) {
        if (mButtonState != null) {
            try {
                Integer value = (Integer) mButtonState.invoke(event, new Object[0]);
                return value.intValue();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    /** Button that caused ACTION_BUTTON_PRESS/RELEASE, or 0 when unavailable. */
    public static int getActionButton(MotionEvent event) {
        if (mActionButton != null) {
            try {
                Integer value = (Integer) mActionButton.invoke(event, new Object[0]);
                return value.intValue();
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }
}
