package com.joy.subtool.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Thin wrapper over the app's {@link SharedPreferences}. Holds keys/defaults
 * for user-tunable settings (currently only the rewind/forward step).
 */
public final class AppPrefs {

    private static final String PREFS_NAME = "subtool_prefs";

    /** Step in milliseconds added/subtracted when the user taps the rewind /
     *  forward buttons in the player. */
    public static final String KEY_SKIP_STEP_MS = "skip_step_ms";
    public static final int DEFAULT_SKIP_STEP_MS = 5000;

    /** Min/max allowed for the skip step (in ms). */
    public static final int MIN_SKIP_STEP_MS = 100;
    public static final int MAX_SKIP_STEP_MS = 60_000;

    private AppPrefs() {}

    public static SharedPreferences get(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getSkipStepMs(Context context) {
        int value = get(context).getInt(KEY_SKIP_STEP_MS, DEFAULT_SKIP_STEP_MS);
        if (value < MIN_SKIP_STEP_MS) return MIN_SKIP_STEP_MS;
        if (value > MAX_SKIP_STEP_MS) return MAX_SKIP_STEP_MS;
        return value;
    }

    public static void setSkipStepMs(Context context, int valueMs) {
        int clamped = Math.max(MIN_SKIP_STEP_MS, Math.min(MAX_SKIP_STEP_MS, valueMs));
        get(context).edit().putInt(KEY_SKIP_STEP_MS, clamped).apply();
    }
}
