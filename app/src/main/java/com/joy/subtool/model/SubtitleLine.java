package com.joy.subtool.model;

/**
 * One subtitle cue. Times in milliseconds relative to media start.
 * {@code startMs} and {@code endMs} are -1 when the timestamp has not
 * been assigned yet.
 */
public class SubtitleLine {
    public long startMs;
    public long endMs;
    public String text;

    public SubtitleLine(long startMs, long endMs, String text) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
    }

    public boolean hasTimestamp() {
        return startMs >= 0 && endMs >= 0;
    }

    public boolean contains(long currentMs) {
        return hasTimestamp() && currentMs >= startMs && currentMs < endMs;
    }

    public long durationMs() {
        return hasTimestamp() ? endMs - startMs : 0;
    }
}
