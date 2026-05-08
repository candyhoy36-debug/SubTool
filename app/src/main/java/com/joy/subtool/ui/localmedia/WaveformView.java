package com.joy.subtool.ui.localmedia;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.joy.subtool.R;

public class WaveformView extends View {

    private float[] amplitudes;
    private float progress = 0f; // 0..1
    private Paint wavePlayedPaint;
    private Paint waveUnplayedPaint;
    private Paint centerLinePaint;
    private Paint positionPaint;
    private Paint trimOverlayPaint;
    private Paint markerStartPaint;
    private Paint markerEndPaint;
    private OnSeekListener seekListener;

    /** Trim region tint, expressed as fractions [0..1] of the waveform width.
     *  When -1f the overlay is hidden. */
    private float trimStartFraction = -1f;
    private float trimEndFraction = -1f;

    /** Markers shown while picking start/end via taps. -1f = hidden. */
    private float markerStartFraction = -1f;
    private float markerEndFraction = -1f;

    public interface OnSeekListener {
        void onSeek(float fraction);
    }

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int colorPlayed = getContext().getColor(R.color.waveform_played);
        int colorUnplayed = getContext().getColor(R.color.waveform_unplayed);
        int colorCenter = getContext().getColor(R.color.waveform_center_line);
        int colorPosition = getContext().getColor(R.color.brand_primary);
        int colorTrim = getContext().getColor(R.color.waveform_trim_overlay);
        int colorMarkerStart = getContext().getColor(R.color.waveform_marker_start);
        int colorMarkerEnd = getContext().getColor(R.color.waveform_marker_end);

        wavePlayedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePlayedPaint.setColor(colorPlayed);
        wavePlayedPaint.setStrokeCap(Paint.Cap.ROUND);

        waveUnplayedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        waveUnplayedPaint.setColor(colorUnplayed);
        waveUnplayedPaint.setStrokeCap(Paint.Cap.ROUND);

        centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerLinePaint.setColor(colorCenter);
        centerLinePaint.setStrokeWidth(1f);

        positionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        positionPaint.setColor(colorPosition);
        positionPaint.setStrokeWidth(2.5f);

        trimOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trimOverlayPaint.setColor(colorTrim);
        trimOverlayPaint.setStyle(Paint.Style.FILL);

        markerStartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerStartPaint.setColor(colorMarkerStart);
        markerStartPaint.setStrokeWidth(3f);

        markerEndPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerEndPaint.setColor(colorMarkerEnd);
        markerEndPaint.setStrokeWidth(3f);
    }

    /**
     * Sets the trim region overlay. Pass -1 for either fraction (or
     * {@code start >= end}) to hide it. Values are clamped to [0..1].
     */
    public void setTrimRegion(float startFraction, float endFraction) {
        if (startFraction < 0 || endFraction < 0 || startFraction >= endFraction) {
            this.trimStartFraction = -1f;
            this.trimEndFraction = -1f;
        } else {
            this.trimStartFraction = Math.max(0f, Math.min(1f, startFraction));
            this.trimEndFraction = Math.max(0f, Math.min(1f, endFraction));
        }
        invalidate();
    }

    /** Sets the start marker (green) shown while picking via taps. -1 hides it. */
    public void setStartMarker(float fraction) {
        this.markerStartFraction = (fraction < 0)
                ? -1f : Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    /** Sets the end marker (red) shown while picking via taps. -1 hides it. */
    public void setEndMarker(float fraction) {
        this.markerEndFraction = (fraction < 0)
                ? -1f : Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    public void setAmplitudes(float[] amplitudes) {
        this.amplitudes = amplitudes;
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    public void setOnSeekListener(OnSeekListener listener) {
        this.seekListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float centerY = h / 2f;

        // Draw center line
        canvas.drawLine(0, centerY, w, centerY, centerLinePaint);

        if (amplitudes == null || amplitudes.length == 0) return;

        int barCount = amplitudes.length;
        float totalBarWidth = (float) w / barCount;
        float barWidth = Math.max(1f, totalBarWidth * 0.6f);

        wavePlayedPaint.setStrokeWidth(barWidth);
        waveUnplayedPaint.setStrokeWidth(barWidth);

        float positionX = progress * w;

        // Trim overlay (drawn behind the bars so it tints the kept range).
        if (trimStartFraction >= 0 && trimEndFraction > trimStartFraction) {
            canvas.drawRect(
                    trimStartFraction * w, 0,
                    trimEndFraction * w, h,
                    trimOverlayPaint);
        }

        for (int i = 0; i < barCount; i++) {
            float x = i * totalBarWidth + totalBarWidth / 2f;
            float amp = amplitudes[i];
            float barHeight = Math.max(2f, amp * (h / 2f - 4f));

            Paint paint = (x <= positionX) ? wavePlayedPaint : waveUnplayedPaint;
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, paint);
        }

        // Markers (start/end) drawn over the bars so they're always visible.
        if (markerStartFraction >= 0) {
            float mx = markerStartFraction * w;
            canvas.drawLine(mx, 0, mx, h, markerStartPaint);
        }
        if (markerEndFraction >= 0) {
            float mx = markerEndFraction * w;
            canvas.drawLine(mx, 0, mx, h, markerEndPaint);
        }

        // Playback position indicator (always on top).
        canvas.drawLine(positionX, 0, positionX, h, positionPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (amplitudes == null || amplitudes.length == 0) return false;
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float fraction = event.getX() / getWidth();
            fraction = Math.max(0f, Math.min(1f, fraction));
            // Don't move the playback indicator unconditionally — let the
            // listener decide what to do (seek vs. set start/end marker).
            // Falling back to a local progress update keeps the legacy
            // behaviour when no listener is attached.
            if (seekListener != null) {
                seekListener.onSeek(fraction);
            } else {
                setProgress(fraction);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
