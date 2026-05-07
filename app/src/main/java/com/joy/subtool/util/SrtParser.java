package com.joy.subtool.util;

import com.joy.subtool.model.SubtitleLine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SubRip (.srt) text into a list of {@link SubtitleLine}s.
 * Lenient: malformed cues are skipped rather than throwing.
 */
public final class SrtParser {

    private static final Pattern TIMING = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})"
                    + "\\s*-->\\s*"
                    + "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})");

    private SrtParser() {}

    public static List<SubtitleLine> parse(String text) {
        List<SubtitleLine> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;

        if (text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n');

        String[] blocks = normalised.split("\n\\s*\n");
        for (String block : blocks) {
            SubtitleLine line = parseBlock(block);
            if (line != null) out.add(line);
        }
        return out;
    }

    private static SubtitleLine parseBlock(String block) {
        String[] lines = block.split("\n");
        int timingIdx = -1;
        Matcher m = null;
        for (int i = 0; i < lines.length; i++) {
            Matcher candidate = TIMING.matcher(lines[i]);
            if (candidate.find()) {
                m = candidate;
                timingIdx = i;
                break;
            }
        }
        if (m == null) return null;

        long startMs = toMs(m.group(1), m.group(2), m.group(3), m.group(4));
        long endMs = toMs(m.group(5), m.group(6), m.group(7), m.group(8));
        if (endMs <= startMs) return null;

        StringBuilder textBuilder = new StringBuilder();
        for (int i = timingIdx + 1; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isEmpty()) continue;
            if (textBuilder.length() > 0) textBuilder.append(' ');
            textBuilder.append(t);
        }
        String textStr = textBuilder.toString().trim();
        if (textStr.isEmpty()) return null;
        return new SubtitleLine(startMs, endMs, textStr);
    }

    private static long toMs(String hh, String mm, String ss, String mmm) {
        long h = Long.parseLong(hh);
        long m = Long.parseLong(mm);
        long s = Long.parseLong(ss);
        String fracPadded = (mmm + "000").substring(0, 3);
        long ms = Long.parseLong(fracPadded);
        return ((h * 60 + m) * 60 + s) * 1000 + ms;
    }
}
