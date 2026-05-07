package com.joy.subtool.util;

import com.joy.subtool.model.SubtitleLine;

import java.util.List;
import java.util.Locale;

/**
 * Exports a list of {@link SubtitleLine}s to standard SubRip (.srt) format.
 */
public final class SrtExporter {

    private SrtExporter() {}

    public static String export(List<SubtitleLine> lines) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (SubtitleLine line : lines) {
            if (!line.hasTimestamp()) continue;
            sb.append(index++).append('\n');
            sb.append(formatTime(line.startMs)).append(" --> ").append(formatTime(line.endMs)).append('\n');
            sb.append(line.text).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String formatTime(long ms) {
        if (ms < 0) return "00:00:00,000";
        long totalSeconds = ms / 1000;
        long millis = ms % 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    public static String formatTimeShort(long ms) {
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
}
