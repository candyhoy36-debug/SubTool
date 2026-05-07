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
    private OnSeekListener seekListener;

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
        float gap = totalBarWidth - barWidth;

        wavePlayedPaint.setStrokeWidth(barWidth);
        waveUnplayedPaint.setStrokeWidth(barWidth);

        float positionX = progress * w;

        for (int i = 0; i < barCount; i++) {
            float x = i * totalBarWidth + totalBarWidth / 2f;
            float amp = amplitudes[i];
            float barHeight = Math.max(2f, amp * (h / 2f - 4f));

            Paint paint = (x <= positionX) ? wavePlayedPaint : waveUnplayedPaint;
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, paint);
        }

        // Draw position indicator
        canvas.drawLine(positionX, 0, positionX, h, positionPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (amplitudes == null || amplitudes.length == 0) return false;
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float fraction = event.getX() / getWidth();
            fraction = Math.max(0f, Math.min(1f, fraction));
            setProgress(fraction);
            if (seekListener != null) {
                seekListener.onSeek(fraction);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
