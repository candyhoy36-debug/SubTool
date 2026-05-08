package com.joy.subtool.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One subtitle cue. Times in milliseconds relative to media start.
 * {@code startMs} and {@code endMs} are -1 when the timestamp has not
 * been assigned yet.
 */
public class SubtitleLine {
    public long startMs;
    public long endMs;
    public String text;
    /** Original indices of the sub-pool entries this line was created from
     *  (if any). Used to restore exactly the right entries when a line is
     *  deleted, avoiding the false-positives of substring matching. */
    public final List<Integer> sourcePoolIndices = new ArrayList<>();

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
