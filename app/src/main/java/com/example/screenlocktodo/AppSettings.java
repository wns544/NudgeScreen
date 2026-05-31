package com.example.screenlocktodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

final class AppSettings {
    private static final String PREFS = "todo_lock_settings";
    private static final String KEY_LOCK_SCREEN_ENABLED = "lock_screen_enabled";
    private static final String KEY_OVERLAY_OPACITY = "overlay_opacity";
    private static final String KEY_CURTAIN_UNLOCK_BOTH_DIRECTIONS = "curtain_unlock_both_directions";
    private static final String KEY_BATTERY_GUIDE_SHOWN = "battery_guide_shown";
    private static final int DEFAULT_OVERLAY_OPACITY = 40;

    private AppSettings() {
    }

    static boolean lockScreenEnabled(Context context) {
        return prefs(context).getBoolean(KEY_LOCK_SCREEN_ENABLED, true);
    }

    static void setLockScreenEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_LOCK_SCREEN_ENABLED, value).apply();
    }

    static int overlayOpacity(Context context) {
        return prefs(context).getInt(KEY_OVERLAY_OPACITY, DEFAULT_OVERLAY_OPACITY);
    }

    static void setOverlayOpacity(Context context, int value) {
        int bounded = Math.max(0, Math.min(100, value));
        prefs(context).edit().putInt(KEY_OVERLAY_OPACITY, bounded).apply();
    }

    static boolean curtainUnlockBothDirections(Context context) {
        return prefs(context).getBoolean(KEY_CURTAIN_UNLOCK_BOTH_DIRECTIONS, true);
    }

    static void setCurtainUnlockBothDirections(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_CURTAIN_UNLOCK_BOTH_DIRECTIONS, value).apply();
    }

    static boolean batteryGuideShown(Context context) {
        return prefs(context).getBoolean(KEY_BATTERY_GUIDE_SHOWN, false);
    }

    static void setBatteryGuideShown(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_BATTERY_GUIDE_SHOWN, value).apply();
    }

    private static SharedPreferences prefs(Context context) {
        Context storageContext = context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            storageContext = context.createDeviceProtectedStorageContext();
        }
        return storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
