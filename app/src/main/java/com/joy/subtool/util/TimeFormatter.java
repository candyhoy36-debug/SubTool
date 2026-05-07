package com.joy.subtool.util;

import java.util.Locale;

/**
 * Utility for formatting millisecond timestamps to human-readable strings.
 */
public final class TimeFormatter {

    private TimeFormatter() {}

    /**
     * Formats milliseconds to MM:SS or H:MM:SS.
     */
    public static String format(long ms) {
        if (ms < 0) return "--:--";
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    /**
     * Formats milliseconds to MM:SS.mmm for precise timestamp display.
     */
    public static String formatPrecise(long ms) {
        if (ms < 0) return "--:--:---";
        long totalSeconds = ms / 1000;
        long millis = ms % 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
        }
        return String.format(Locale.US, "%d:%02d.%03d", minutes, seconds, millis);
    }
}
