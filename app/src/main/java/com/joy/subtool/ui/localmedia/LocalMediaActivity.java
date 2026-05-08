package com.joy.subtool.ui.localmedia;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.RangeSlider;
import com.joy.subtool.R;
import com.joy.subtool.ui.SettingsActivity;
import com.joy.subtool.util.AppPrefs;
import com.joy.subtool.SubToolApp;
import com.joy.subtool.data.LocalMediaHistoryDao;
import com.joy.subtool.data.LocalMediaHistoryEntity;
import com.joy.subtool.data.LocalSubtitleDao;
import com.joy.subtool.data.LocalSubtitleEntity;
import com.joy.subtool.model.SubtitleLine;
import com.joy.subtool.util.SrtExporter;
import com.joy.subtool.util.SrtParser;
import com.joy.subtool.util.TimeFormatter;
import com.joy.subtool.util.WaveformExtractor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalMediaActivity extends AppCompatActivity {

    private static final long TICK_MS = 250L;

    // --- Player ---
    @Nullable private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private FrameLayout videoContainer;
    private View audioPlaceholder;
    private WaveformView waveformView;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private ImageButton btnPlayPause;
    private ImageButton btnRewind;
    private ImageButton btnForward;
    private boolean isVideo = false;
    private boolean userSeeking = false;

    // --- Trim ---
    private RangeSlider trimRangeSlider;
    private TextView tvTrimStart;
    private TextView tvTrimEnd;
    private View trimContainer;
    private long trimStartMs = 0;
    private long trimEndMs = Long.MAX_VALUE;
    private boolean trimEnabled = false;
    /** True while we're updating the RangeSlider programmatically — prevents
     *  the addOnChangeListener from re-clobbering trimStartMs/trimEndMs that
     *  we just set in code. */
    private boolean syncingTrimSlider = false;

    // --- Subtitle list ---
    private RecyclerView rvSubtitles;
    private LocalSubtitleAdapter subtitleAdapter;
    private LinearLayoutManager layoutManager;
    private final List<SubtitleLine> subtitleLines = new ArrayList<>();
    private int selectedLineIndex = -1;
    /** Last index that was set as active by playback so we don't redundantly
     *  notify+scroll on every tick when the active line hasn't changed. */
    private int lastActiveIndex = -1;

    // --- Sub pool ---
    private final List<SubPoolAdapter.PoolEntry> subPool = new ArrayList<>();

    // --- Loop ---
    private enum LoopMode { NONE, SINGLE, RANGE, ALL }
    private LoopMode loopMode = LoopMode.NONE;
    private int loopCount = -1; // -1 = infinite
    private int loopRemaining = -1;
    private int loopRangeStart = -1;
    private int loopRangeEnd = -1;

    // --- Playback speed ---
    private float playbackSpeed = 1.0f;

    // --- Action buttons ---
    private TextView btnSetStart;
    private TextView btnSetEnd;
    private TextView btnSpeed;
    private TextView btnLoop;
    private TextView btnLoopRange;
    private TextView btnWaveMode;
    private TextView btnConfirmWaveLine;

    // --- Wave-tap mode (assign Start/End by tapping the audio waveform) ---
    private enum WaveTapTarget { NONE, START, END }
    private boolean waveTapMode = false;
    private WaveTapTarget waveTapTarget = WaveTapTarget.NONE;
    /** Scratch start/end picked via wave taps (-1 = not yet picked). They are
     *  written to the selected line when the user presses Confirm. */
    private long waveTapStartMs = -1;
    private long waveTapEndMs = -1;

    // --- Current file ---
    @Nullable private Uri currentFileUri;
    @Nullable private String currentFileName;
    private long mediaDuration = 0;

    // --- Handler for time ticks ---
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                onTick();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    // --- SAF launchers ---
    private final ActivityResultLauncher<String[]> mediaPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) openMediaFile(uri);
            });

    private final ActivityResultLauncher<String[]> importTextLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importTextFile(uri);
            });

    private final ActivityResultLauncher<String[]> importSrtLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) importSrtFile(uri);
            });

    private final ActivityResultLauncher<String> exportSrtLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/x-subrip"), uri -> {
                if (uri != null) exportSrtToUri(uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_media);

        bindToolbar();
        bindPlayer();
        bindTrim();
        bindActionButtons();
        bindSubtitleList();
        bindBottomActions();

        handler.postDelayed(tickRunnable, TICK_MS);

        // If launched from history (or another activity) with a media URI, open it.
        Uri incoming = getIntent() != null ? getIntent().getData() : null;
        if (incoming != null) {
            openMediaFile(incoming);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri uri = intent != null ? intent.getData() : null;
        if (uri != null) {
            openMediaFile(uri);
        }
    }

    // ======================== TOOLBAR ========================

    private void bindToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_history) {
                startActivity(new Intent(this, LocalMediaHistoryActivity.class));
                return true;
            }
            if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    // ======================== PLAYER ========================

    private void bindPlayer() {
        surfaceView = findViewById(R.id.surface_view);
        videoContainer = findViewById(R.id.video_container);
        audioPlaceholder = findViewById(R.id.audio_placeholder);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnRewind = findViewById(R.id.btn_rewind);
        btnForward = findViewById(R.id.btn_forward);

        waveformView = findViewById(R.id.waveform_view);

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> seekRelative(-AppPrefs.getSkipStepMs(this)));
        btnForward.setOnClickListener(v -> seekRelative(AppPrefs.getSkipStepMs(this)));

        waveformView.setOnSeekListener(fraction -> {
            if (mediaPlayer == null || mediaDuration <= 0) return;
            int target = (int) (fraction * mediaDuration);
            if (trimEnabled) {
                target = Math.max((int) trimStartMs, Math.min(target, (int) trimEndMs));
            }
            // In wave-tap mode the next tap goes to the start/end picker; in
            // every other case the tap behaves like a seek.
            if (waveTapMode && waveTapTarget != WaveTapTarget.NONE) {
                onWaveTap(target);
                return;
            }
            try {
                mediaPlayer.seekTo(target);
                seekBar.setProgress(target);
                tvCurrentTime.setText(TimeFormatter.format(target));
                waveformView.setProgress((float) target / mediaDuration);
            } catch (IllegalStateException ignored) {}
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    int seekTo = progress;
                    if (trimEnabled) {
                        seekTo = Math.max((int) trimStartMs, Math.min(seekTo, (int) trimEndMs));
                    }
                    mediaPlayer.seekTo(seekTo);
                    tvCurrentTime.setText(TimeFormatter.format(seekTo));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) { userSeeking = false; }
        });

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mediaPlayer != null && isVideo) {
                    mediaPlayer.setDisplay(holder);
                }
            }
            @Override public void surfaceChanged(@NonNull SurfaceHolder h, int f, int w, int ht) {}
            @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mediaPlayer != null) {
                    mediaPlayer.setDisplay(null);
                }
            }
        });
    }

    private void openMediaFile(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}

        releasePlayer();

        currentFileUri = uri;
        currentFileName = resolveFileName(uri);
        String mimeType = getContentResolver().getType(uri);
        isVideo = mimeType != null && mimeType.startsWith("video/");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(currentFileName != null ? currentFileName : getString(R.string.app_name));

        if (isVideo) {
            videoContainer.setVisibility(View.VISIBLE);
            audioPlaceholder.setVisibility(View.GONE);
        } else {
            videoContainer.setVisibility(View.GONE);
            audioPlaceholder.setVisibility(View.VISIBLE);
            TextView tvAudioName = findViewById(R.id.tv_audio_file_name);
            if (tvAudioName != null) {
                tvAudioName.setText(currentFileName != null ? currentFileName : "Audio");
            }
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            if (isVideo && surfaceView.getHolder().getSurface().isValid()) {
                mediaPlayer.setDisplay(surfaceView.getHolder());
            }
            mediaPlayer.prepare();
            mediaDuration = mediaPlayer.getDuration();

            seekBar.setMax((int) mediaDuration);
            seekBar.setProgress(0);
            tvTotalTime.setText(TimeFormatter.format(mediaDuration));
            tvCurrentTime.setText(TimeFormatter.format(0));

            trimStartMs = 0;
            trimEndMs = mediaDuration;
            trimEnabled = false;
            tvTrimStart.setText(TimeFormatter.format(0));
            tvTrimEnd.setText(TimeFormatter.format(mediaDuration));
            syncTrimSliderFromMs();
            updateWaveformTrimOverlay();

            // Reset wave-tap markers between files.
            clearWaveTapState();

            applyPlaybackSpeed();

            mediaPlayer.setOnCompletionListener(mp -> onMediaCompleted());

            mediaPlayer.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause);

            // Extract waveform in background
            if (!isVideo) {
                waveformView.setAmplitudes(null);
                final Uri extractUri = uri;
                WaveformExtractor.extract(this, uri, new WaveformExtractor.Callback() {
                    @Override
                    public void onWaveformReady(float[] amplitudes) {
                        if (extractUri.equals(currentFileUri)) {
                            waveformView.setAmplitudes(amplitudes);
                        }
                    }
                    @Override
                    public void onError(Exception e) {
                        // Waveform extraction failed silently
                    }
                });
            }

            recordHistory(uri);
            loadSavedSubtitles(uri);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
        } else {
            if (trimEnabled) {
                int pos = mediaPlayer.getCurrentPosition();
                if (pos < trimStartMs || pos >= trimEndMs) {
                    mediaPlayer.seekTo((int) trimStartMs);
                }
            }
            mediaPlayer.start();
            applyPlaybackSpeed();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        }
    }

    private void seekRelative(int deltaMs) {
        if (mediaPlayer == null) return;
        int target = mediaPlayer.getCurrentPosition() + deltaMs;
        if (trimEnabled) {
            target = Math.max((int) trimStartMs, Math.min(target, (int) trimEndMs));
        } else {
            target = Math.max(0, Math.min(target, (int) mediaDuration));
        }
        try {
            mediaPlayer.seekTo(target);
        } catch (IllegalStateException ignored) {}
        // Refresh the seekbar / waveform / time label here instead of relying on
        // the periodic tick — when the player is paused the tick early-returns,
        // so without this the UI looked frozen after tapping rewind/forward.
        tvCurrentTime.setText(TimeFormatter.format(target));
        seekBar.setProgress(target);
        if (mediaDuration > 0) {
            waveformView.setProgress((float) target / mediaDuration);
        }
    }

    private void onTick() {
        if (mediaPlayer == null) return;
        int pos;
        try {
            pos = mediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            return;
        }

        // Enforce trim bounds
        if (trimEnabled && pos >= trimEndMs) {
            if (loopMode == LoopMode.ALL) {
                handleLoopRepeat((int) trimStartMs);
                return;
            }
            try {
                mediaPlayer.seekTo((int) trimStartMs);
                mediaPlayer.pause();
            } catch (IllegalStateException ignored) {}
            btnPlayPause.setImageResource(R.drawable.ic_play);
            return;
        }

        // Loop single line
        if (loopMode == LoopMode.SINGLE && selectedLineIndex >= 0
                && selectedLineIndex < subtitleLines.size()) {
            SubtitleLine line = subtitleLines.get(selectedLineIndex);
            if (line.hasTimestamp() && pos >= line.endMs) {
                handleLoopRepeat((int) line.startMs);
                return;
            }
        }

        // Loop range
        if (loopMode == LoopMode.RANGE && loopRangeStart >= 0 && loopRangeEnd >= 0
                && loopRangeStart < subtitleLines.size() && loopRangeEnd < subtitleLines.size()) {
            SubtitleLine endLine = subtitleLines.get(loopRangeEnd);
            if (endLine.hasTimestamp() && pos >= endLine.endMs) {
                SubtitleLine startLine = subtitleLines.get(loopRangeStart);
                if (startLine.hasTimestamp()) {
                    handleLoopRepeat((int) startLine.startMs);
                    return;
                }
            }
        }

        if (!userSeeking) {
            seekBar.setProgress(pos);
            tvCurrentTime.setText(TimeFormatter.format(pos));
            if (mediaDuration > 0) {
                waveformView.setProgress((float) pos / mediaDuration);
            }
        }

        updateActiveSubtitle(pos);
    }

    private void handleLoopRepeat(int seekToMs) {
        if (loopCount > 0) {
            loopRemaining--;
            if (loopRemaining <= 0) {
                clearLoop();
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.pause();
                    } catch (IllegalStateException ignored) {}
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                }
                Toast.makeText(this, R.string.loop_finished, Toast.LENGTH_SHORT).show();
                return;
            }
            updateLoopBadge();
        }
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(seekToMs);
            } catch (IllegalStateException ignored) {}
        }
    }

    private void onMediaCompleted() {
        if (loopMode == LoopMode.ALL) {
            int seekTo = trimEnabled ? (int) trimStartMs : 0;
            handleLoopRepeat(seekTo);
            if (mediaPlayer != null && loopMode == LoopMode.ALL) {
                try {
                    mediaPlayer.seekTo(seekTo);
                    mediaPlayer.start();
                    applyPlaybackSpeed();
                } catch (IllegalStateException ignored) {
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                }
            }
            return;
        }
        btnPlayPause.setImageResource(R.drawable.ic_play);
        seekBar.setProgress((int) mediaDuration);
        tvCurrentTime.setText(TimeFormatter.format(mediaDuration));
    }

    private void updateActiveSubtitle(int posMs) {
        if (subtitleLines.isEmpty()) return;
        int newActive = -1;
        for (int i = 0; i < subtitleLines.size(); i++) {
            if (subtitleLines.get(i).contains(posMs)) {
                newActive = i;
                break;
            }
        }
        // Only update + scroll when the active line actually changes — avoids
        // re-binding the same row every tick (which felt jittery to users).
        if (newActive == lastActiveIndex) return;
        lastActiveIndex = newActive;
        subtitleAdapter.setActiveIndex(newActive);
        if (newActive >= 0) {
            layoutManager.scrollToPositionWithOffset(newActive, 100);
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        btnPlayPause.setImageResource(R.drawable.ic_play);
    }

    @Nullable
    private String resolveFileName(Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) return c.getString(idx);
                }
            } catch (Exception ignored) {}
        }
        String path = uri.getLastPathSegment();
        return path != null ? path : uri.toString();
    }

    // ======================== TRIM ========================

    private void bindTrim() {
        trimContainer = findViewById(R.id.trim_container);
        trimRangeSlider = findViewById(R.id.trim_range_slider);
        tvTrimStart = findViewById(R.id.tv_trim_start);
        tvTrimEnd = findViewById(R.id.tv_trim_end);
        TextView btnResetTrim = findViewById(R.id.btn_reset_trim);
        TextView btnSetTrimStart = findViewById(R.id.btn_set_trim_start);
        TextView btnSetTrimEnd = findViewById(R.id.btn_set_trim_end);

        // Two thumbs on the slider give the user direct, accurate control over
        // both trim_start and trim_end without needing the Set Start / Set End
        // buttons. The buttons stay as a one-tap shortcut to set the value to
        // the current playback position.
        trimRangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser || syncingTrimSlider) return;
            List<Float> values = slider.getValues();
            if (values.size() < 2) return;
            long total = mediaDuration > 0 ? mediaDuration : 100L;
            long start = (long) (values.get(0) / 100f * total);
            long end = (long) (values.get(1) / 100f * total);
            if (end <= start + 100) end = Math.min(total, start + 100);
            trimStartMs = start;
            trimEndMs = end;
            trimEnabled = true;
            tvTrimStart.setText(TimeFormatter.format(trimStartMs));
            tvTrimEnd.setText(TimeFormatter.format(trimEndMs));
            updateWaveformTrimOverlay();
        });

        btnSetTrimStart.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            trimStartMs = mediaPlayer.getCurrentPosition();
            if (trimEndMs <= trimStartMs) {
                trimEndMs = Math.min(mediaDuration, trimStartMs + 1000);
            }
            trimEnabled = true;
            tvTrimStart.setText(TimeFormatter.format(trimStartMs));
            tvTrimEnd.setText(TimeFormatter.format(trimEndMs));
            syncTrimSliderFromMs();
            updateWaveformTrimOverlay();
            Toast.makeText(this, getString(R.string.trim_start_set, TimeFormatter.format(trimStartMs)),
                    Toast.LENGTH_SHORT).show();
        });

        btnSetTrimEnd.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            trimEndMs = mediaPlayer.getCurrentPosition();
            if (trimEndMs <= trimStartMs) {
                Toast.makeText(this, R.string.trim_end_before_start, Toast.LENGTH_SHORT).show();
                return;
            }
            trimEnabled = true;
            tvTrimEnd.setText(TimeFormatter.format(trimEndMs));
            syncTrimSliderFromMs();
            updateWaveformTrimOverlay();
            Toast.makeText(this, getString(R.string.trim_end_set, TimeFormatter.format(trimEndMs)),
                    Toast.LENGTH_SHORT).show();
        });

        btnResetTrim.setOnClickListener(v -> {
            trimEnabled = false;
            trimStartMs = 0;
            trimEndMs = mediaDuration > 0 ? mediaDuration : Long.MAX_VALUE;
            tvTrimStart.setText(TimeFormatter.format(0));
            tvTrimEnd.setText(TimeFormatter.format(mediaDuration));
            syncTrimSliderFromMs();
            updateWaveformTrimOverlay();
            Toast.makeText(this, R.string.trim_reset, Toast.LENGTH_SHORT).show();
        });
    }

    /** Pushes the current trimStartMs/trimEndMs values into the RangeSlider as
     *  percentages of the media duration. Guarded by syncingTrimSlider so the
     *  addOnChangeListener doesn't fight us. */
    private void syncTrimSliderFromMs() {
        syncingTrimSlider = true;
        try {
            if (mediaDuration <= 0) {
                trimRangeSlider.setValues(0f, 100f);
                return;
            }
            float startPct = Math.max(0f,
                    Math.min(100f, 100f * trimStartMs / (float) mediaDuration));
            float endPct = Math.max(startPct + 0.1f,
                    Math.min(100f, 100f * trimEndMs / (float) mediaDuration));
            try {
                trimRangeSlider.setValues(startPct, endPct);
            } catch (IllegalStateException ignored) {}
        } finally {
            syncingTrimSlider = false;
        }
    }

    /** Mirrors the trim region onto the audio waveform so users can see exactly
     *  which part of the file is kept. Hidden when trim is disabled. */
    private void updateWaveformTrimOverlay() {
        if (!trimEnabled || mediaDuration <= 0) {
            waveformView.setTrimRegion(-1f, -1f);
            return;
        }
        waveformView.setTrimRegion(
                (float) trimStartMs / mediaDuration,
                (float) trimEndMs / mediaDuration);
    }

    // ======================== ACTION BUTTONS ========================

    private void bindActionButtons() {
        btnSetStart = findViewById(R.id.btn_set_start);
        btnSetEnd = findViewById(R.id.btn_set_end);
        btnSpeed = findViewById(R.id.btn_speed);
        btnLoop = findViewById(R.id.btn_loop);
        btnLoopRange = findViewById(R.id.btn_loop_range);
        btnWaveMode = findViewById(R.id.btn_wave_mode);
        btnConfirmWaveLine = findViewById(R.id.btn_confirm_wave_line);

        btnSetStart.setOnClickListener(v -> onSetStart());
        btnSetEnd.setOnClickListener(v -> onSetEnd());
        btnSpeed.setOnClickListener(v -> showSpeedMenu());
        btnLoop.setOnClickListener(v -> onLoopClicked());
        btnLoopRange.setOnClickListener(v -> onLoopRangeClicked());
        btnWaveMode.setOnClickListener(v -> toggleWaveTapMode());
        btnConfirmWaveLine.setOnClickListener(v -> confirmWaveTapLine());
    }

    private void onSetStart() {
        if (mediaPlayer == null) {
            Toast.makeText(this, R.string.error_no_media, Toast.LENGTH_SHORT).show();
            return;
        }
        // Auto-pick the first line that doesn't have a timestamp yet so the user can
        // assign Start/End to imported sub lines without having to tap them first.
        if (subPool.isEmpty()
                && (selectedLineIndex < 0 || selectedLineIndex >= subtitleLines.size())) {
            int autoIdx = findFirstUntimedLine();
            if (autoIdx >= 0) {
                selectedLineIndex = autoIdx;
                subtitleAdapter.setActiveIndex(autoIdx);
                layoutManager.scrollToPositionWithOffset(autoIdx, 100);
            }
        }
        if (waveTapMode) {
            if (selectedLineIndex < 0 || selectedLineIndex >= subtitleLines.size()) {
                Toast.makeText(this, R.string.wave_mode_no_line, Toast.LENGTH_SHORT).show();
                return;
            }
            // Switch the next-tap target to START. Repeated taps on the
            // waveform will keep updating the start until the user switches
            // to END or hits Confirm.
            waveTapTarget = WaveTapTarget.START;
            btnSetStart.setSelected(true);
            btnSetEnd.setSelected(false);
            Toast.makeText(this, R.string.wave_mode_set_start, Toast.LENGTH_SHORT).show();
            return;
        }
        long startMs = mediaPlayer.getCurrentPosition();
        // Store temporarily, wait for Set End
        btnSetStart.setTag(startMs);
        btnSetStart.setText(getString(R.string.action_set_start_at, TimeFormatter.format(startMs)));
        btnSetStart.setSelected(true);
        Toast.makeText(this, getString(R.string.start_set_at, TimeFormatter.format(startMs)),
                Toast.LENGTH_SHORT).show();
    }

    private int findFirstUntimedLine() {
        for (int i = 0; i < subtitleLines.size(); i++) {
            if (!subtitleLines.get(i).hasTimestamp()) {
                return i;
            }
        }
        return -1;
    }

    private void onSetEnd() {
        if (mediaPlayer == null) {
            Toast.makeText(this, R.string.error_no_media, Toast.LENGTH_SHORT).show();
            return;
        }
        if (waveTapMode) {
            if (selectedLineIndex < 0 || selectedLineIndex >= subtitleLines.size()) {
                Toast.makeText(this, R.string.wave_mode_no_line, Toast.LENGTH_SHORT).show();
                return;
            }
            waveTapTarget = WaveTapTarget.END;
            btnSetEnd.setSelected(true);
            Toast.makeText(this, R.string.wave_mode_set_end, Toast.LENGTH_SHORT).show();
            return;
        }
        Object startTag = btnSetStart.getTag();
        if (startTag == null) {
            Toast.makeText(this, R.string.set_start_first, Toast.LENGTH_SHORT).show();
            return;
        }
        long startMs = (long) startTag;
        long endMs = mediaPlayer.getCurrentPosition();
        if (endMs <= startMs) {
            Toast.makeText(this, R.string.end_before_start, Toast.LENGTH_SHORT).show();
            return;
        }

        // If sub pool has any unused entries, show the pool picker dialog.
        // (Pool entries are now hidden, not removed, when used — so checking
        // !subPool.isEmpty() would block normal subtitle creation forever.)
        if (hasUnusedPoolEntries()) {
            showSubPoolPicker(startMs, endMs);
        } else if (selectedLineIndex >= 0 && selectedLineIndex < subtitleLines.size()) {
            // Assign to selected line
            SubtitleLine line = subtitleLines.get(selectedLineIndex);
            line.startMs = startMs;
            line.endMs = endMs;
            // Re-sort so the newly-timed line lands in chronological order.
            sortLinesByTime();
            // Find the next untimed line (if any) so the user can keep going.
            int nextUntimed = findFirstUntimedLine();
            if (nextUntimed >= 0) {
                selectedLineIndex = nextUntimed;
                subtitleAdapter.setActiveIndex(nextUntimed);
                layoutManager.scrollToPositionWithOffset(nextUntimed, 100);
            } else {
                // No more untimed lines: just keep the currently-edited line selected.
                selectedLineIndex = subtitleLines.indexOf(line);
                if (selectedLineIndex >= 0) {
                    subtitleAdapter.setActiveIndex(selectedLineIndex);
                }
            }
            autoSaveSubtitles();
        } else {
            // Create new line with input dialog
            showNewSubtitleDialog(startMs, endMs);
        }

        // Reset start button
        btnSetStart.setTag(null);
        btnSetStart.setText(R.string.action_set_start);
        btnSetStart.setSelected(false);
    }

    private void showSubPoolPicker(long startMs, long endMs) {
        // Show only unused entries
        List<SubPoolAdapter.PoolEntry> available = new ArrayList<>();
        for (SubPoolAdapter.PoolEntry e : subPool) {
            if (!e.used) available.add(e);
        }

        SubPoolAdapter poolAdapter = new SubPoolAdapter();
        poolAdapter.setEntries(available);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(poolAdapter);
        int paddingPx = (int) (getResources().getDisplayMetrics().density * 8);
        rv.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.pick_from_pool)
                .setView(rv)
                .setPositiveButton(R.string.action_confirm, (d, w) -> {
                    List<SubPoolAdapter.PoolEntry> selected = poolAdapter.getSelectedEntries();
                    if (selected.isEmpty()) {
                        Toast.makeText(this, R.string.no_line_selected, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!poolAdapter.isSelectionConsecutive()) {
                        Toast.makeText(this, R.string.select_consecutive, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Merge selected texts
                    StringBuilder merged = new StringBuilder();
                    for (SubPoolAdapter.PoolEntry entry : selected) {
                        if (merged.length() > 0) merged.append(' ');
                        merged.append(entry.text);
                    }

                    // Add to subtitle lines (and sort so it lands in chronological order)
                    SubtitleLine newLine = new SubtitleLine(startMs, endMs, merged.toString());
                    // Remember which pool entries were merged into this line so
                    // we can restore *exactly* those entries (not random
                    // substring matches) if the user deletes the line later.
                    for (SubPoolAdapter.PoolEntry entry : selected) {
                        newLine.sourcePoolIndices.add(entry.originalIndex);
                    }
                    subtitleLines.add(newLine);
                    sortLinesByTime();

                    // Mark used entries (hide, not delete)
                    for (SubPoolAdapter.PoolEntry entry : selected) {
                        for (SubPoolAdapter.PoolEntry poolEntry : subPool) {
                            if (poolEntry.originalIndex == entry.originalIndex) {
                                poolEntry.used = true;
                            }
                        }
                    }

                    autoSaveSubtitles();

                    boolean allUsed = true;
                    for (SubPoolAdapter.PoolEntry e : subPool) {
                        if (!e.used) { allUsed = false; break; }
                    }
                    if (allUsed) {
                        Toast.makeText(this, R.string.pool_completed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        dialog.show();
    }

    private void showNewSubtitleDialog(long startMs, long endMs) {
        EditText input = new EditText(this);
        input.setHint(R.string.subtitle_text_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        int paddingPx = (int) (getResources().getDisplayMetrics().density * 20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        container.addView(input);

        final boolean isManualAdd = startMs < 0 || endMs < 0;
        String title = isManualAdd
                ? getString(R.string.action_add_line)
                : getString(R.string.new_subtitle_title,
                        TimeFormatter.format(startMs), TimeFormatter.format(endMs));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton(R.string.action_add, (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    if (text.isEmpty()) return;
                    SubtitleLine line = new SubtitleLine(startMs, endMs, text);
                    subtitleLines.add(line);
                    if (isManualAdd) {
                        // Append + select the new line so the global Set Start /
                        // Set End buttons can target it without needing to long-press.
                        subtitleAdapter.setLines(new ArrayList<>(subtitleLines));
                        int newIdx = subtitleLines.size() - 1;
                        selectedLineIndex = newIdx;
                        lastActiveIndex = newIdx;
                        subtitleAdapter.setActiveIndex(newIdx);
                        layoutManager.scrollToPositionWithOffset(newIdx, 100);
                        Toast.makeText(this, R.string.added_line_use_start_end,
                                Toast.LENGTH_LONG).show();
                    } else {
                        // Has timestamps: keep the list sorted by start time.
                        sortLinesByTime();
                    }
                    autoSaveSubtitles();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Sorts {@link #subtitleLines} by start time ascending. Lines without a
     *  timestamp are kept at the end (in their original relative order, since
     *  Java's {@code List.sort} is stable). The currently-selected line keeps
     *  pointing at the same {@link SubtitleLine} instance after sorting. */
    private void sortLinesByTime() {
        SubtitleLine selected =
                (selectedLineIndex >= 0 && selectedLineIndex < subtitleLines.size())
                        ? subtitleLines.get(selectedLineIndex)
                        : null;
        Collections.sort(subtitleLines, (a, b) -> {
            long ka = a.hasTimestamp() ? a.startMs : Long.MAX_VALUE;
            long kb = b.hasTimestamp() ? b.startMs : Long.MAX_VALUE;
            return Long.compare(ka, kb);
        });
        if (selected != null) {
            selectedLineIndex = subtitleLines.indexOf(selected);
        }
        subtitleAdapter.setLines(new ArrayList<>(subtitleLines));
        if (selectedLineIndex >= 0) {
            subtitleAdapter.setActiveIndex(selectedLineIndex);
            lastActiveIndex = selectedLineIndex;
        } else {
            lastActiveIndex = -1;
        }
    }

    // ======================== SPEED ========================

    private void showSpeedMenu() {
        PopupMenu popup = new PopupMenu(this, btnSpeed);
        float[] speeds = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
        for (float s : speeds) {
            String label = s == 1.0f ? "1x" : String.format(java.util.Locale.US, "%.2f", s).replaceAll("0+$", "").replaceAll("\\.$", "") + "x";
            popup.getMenu().add(label).setOnMenuItemClickListener(item -> {
                playbackSpeed = s;
                applyPlaybackSpeed();
                btnSpeed.setText(s == 1.0f ? getString(R.string.action_speed) : label);
                btnSpeed.setSelected(s != 1.0f);
                return true;
            });
        }
        popup.show();
    }

    @SuppressLint("NewApi")
    private void applyPlaybackSpeed() {
        if (mediaPlayer == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (mediaPlayer.isPlaying()) {
                    PlaybackParams params = mediaPlayer.getPlaybackParams();
                    params.setSpeed(playbackSpeed);
                    mediaPlayer.setPlaybackParams(params);
                }
            } catch (Exception ignored) {}
        }
    }

    // ======================== LOOP ========================

    private void onLoopClicked() {
        if (loopMode == LoopMode.SINGLE) {
            clearLoop();
            Toast.makeText(this, R.string.loop_off, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedLineIndex < 0 || selectedLineIndex >= subtitleLines.size()) {
            // No line selected → loop all
            showLoopCountDialog(LoopMode.ALL, -1, -1);
            return;
        }
        SubtitleLine line = subtitleLines.get(selectedLineIndex);
        if (!line.hasTimestamp()) {
            Toast.makeText(this, R.string.line_no_timestamp, Toast.LENGTH_SHORT).show();
            return;
        }
        showLoopCountDialog(LoopMode.SINGLE, selectedLineIndex, selectedLineIndex);
    }

    private void onLoopRangeClicked() {
        if (loopMode == LoopMode.RANGE) {
            clearLoop();
            Toast.makeText(this, R.string.loop_off, Toast.LENGTH_SHORT).show();
            return;
        }
        showRangeInputDialog();
    }

    private void showRangeInputDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_loop_range, null);
        EditText etFrom = dialogView.findViewById(R.id.et_from);
        EditText etTo = dialogView.findViewById(R.id.et_to);

        new AlertDialog.Builder(this)
                .setTitle(R.string.loop_range_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_confirm, (d, w) -> {
                    try {
                        int from = Integer.parseInt(etFrom.getText().toString().trim()) - 1;
                        int to = Integer.parseInt(etTo.getText().toString().trim()) - 1;
                        if (from < 0 || to < from || to >= subtitleLines.size()) {
                            Toast.makeText(this, R.string.invalid_range, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!subtitleLines.get(from).hasTimestamp() || !subtitleLines.get(to).hasTimestamp()) {
                            Toast.makeText(this, R.string.range_no_timestamp, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showLoopCountDialog(LoopMode.RANGE, from, to);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, R.string.invalid_range, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showLoopCountDialog(LoopMode mode, int rangeStart, int rangeEnd) {
        String[] options = {
                getString(R.string.loop_infinite),
                "2x", "3x", "5x", "10x",
                getString(R.string.loop_custom)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.loop_count_title)
                .setItems(options, (d, which) -> {
                    int count;
                    switch (which) {
                        case 0: count = -1; break;
                        case 1: count = 2; break;
                        case 2: count = 3; break;
                        case 3: count = 5; break;
                        case 4: count = 10; break;
                        default:
                            showCustomLoopCountDialog(mode, rangeStart, rangeEnd);
                            return;
                    }
                    activateLoop(mode, rangeStart, rangeEnd, count);
                })
                .show();
    }

    private void showCustomLoopCountDialog(LoopMode mode, int rangeStart, int rangeEnd) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("N");

        int paddingPx = (int) (getResources().getDisplayMetrics().density * 20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.loop_custom_title)
                .setView(container)
                .setPositiveButton(R.string.action_confirm, (d, w) -> {
                    try {
                        int count = Integer.parseInt(input.getText().toString().trim());
                        if (count <= 0) count = -1;
                        activateLoop(mode, rangeStart, rangeEnd, count);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void activateLoop(LoopMode mode, int rangeStart, int rangeEnd, int count) {
        loopMode = mode;
        loopCount = count;
        loopRemaining = count;
        loopRangeStart = rangeStart;
        loopRangeEnd = rangeEnd;

        updateLoopBadge();

        if (mode == LoopMode.RANGE) {
            subtitleAdapter.setLoopRange(rangeStart, rangeEnd);
        } else {
            subtitleAdapter.clearLoopRange();
        }

        // Seek to start of loop target
        if (mediaPlayer != null) {
            if (mode == LoopMode.SINGLE && rangeStart >= 0 && rangeStart < subtitleLines.size()) {
                SubtitleLine line = subtitleLines.get(rangeStart);
                if (line.hasTimestamp()) mediaPlayer.seekTo((int) line.startMs);
            } else if (mode == LoopMode.RANGE && rangeStart >= 0 && rangeStart < subtitleLines.size()) {
                SubtitleLine line = subtitleLines.get(rangeStart);
                if (line.hasTimestamp()) mediaPlayer.seekTo((int) line.startMs);
            } else if (mode == LoopMode.ALL) {
                int seekTo = trimEnabled ? (int) trimStartMs : 0;
                mediaPlayer.seekTo(seekTo);
            }
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                applyPlaybackSpeed();
                btnPlayPause.setImageResource(R.drawable.ic_pause);
            }
        }

        String msg;
        switch (mode) {
            case SINGLE: msg = getString(R.string.loop_single_on); break;
            case RANGE: msg = getString(R.string.loop_range_on, rangeStart + 1, rangeEnd + 1); break;
            default: msg = getString(R.string.loop_all_on); break;
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void clearLoop() {
        loopMode = LoopMode.NONE;
        loopCount = -1;
        loopRemaining = -1;
        loopRangeStart = -1;
        loopRangeEnd = -1;
        subtitleAdapter.clearLoopRange();
        btnLoop.setSelected(false);
        btnLoopRange.setSelected(false);
        btnLoop.setText(R.string.action_loop);
    }

    private void updateLoopBadge() {
        if (loopMode == LoopMode.NONE) {
            btnLoop.setSelected(false);
            btnLoopRange.setSelected(false);
            btnLoop.setText(R.string.action_loop);
        } else if (loopMode == LoopMode.SINGLE) {
            btnLoop.setSelected(true);
            if (loopCount > 0) {
                btnLoop.setText(getString(R.string.loop_badge, loopRemaining));
            } else {
                btnLoop.setText(R.string.action_loop_infinite);
            }
        } else if (loopMode == LoopMode.RANGE) {
            btnLoopRange.setSelected(true);
            if (loopCount > 0) {
                btnLoopRange.setText(getString(R.string.loop_range_badge, loopRemaining));
            } else {
                btnLoopRange.setText(R.string.action_loop_range_infinite);
            }
        } else {
            btnLoop.setSelected(true);
            btnLoop.setText(R.string.action_loop_all);
        }
    }

    // ======================== SUBTITLE LIST ========================

    private void bindSubtitleList() {
        rvSubtitles = findViewById(R.id.rv_subtitles);
        subtitleAdapter = new LocalSubtitleAdapter();
        layoutManager = new LinearLayoutManager(this);
        rvSubtitles.setLayoutManager(layoutManager);
        rvSubtitles.setAdapter(subtitleAdapter);

        subtitleAdapter.setOnLineClickListener((position, line) -> {
            selectedLineIndex = position;
            lastActiveIndex = position;
            subtitleAdapter.setActiveIndex(position);
            if (line.hasTimestamp() && mediaPlayer != null) {
                mediaPlayer.seekTo((int) line.startMs);
                if (!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                    applyPlaybackSpeed();
                    btnPlayPause.setImageResource(R.drawable.ic_pause);
                }
            }
        });

        subtitleAdapter.setOnLineLongClickListener((position, line) -> {
            showLineOptionsDialog(position, line);
        });

        subtitleAdapter.setOnTimeAdjustListener(new LocalSubtitleAdapter.OnTimeAdjustListener() {
            @Override
            public void onAdjustStart(int position, SubtitleLine line, long deltaMs) {
                long maxStart = line.endMs > 0 ? line.endMs - 100 : Long.MAX_VALUE;
                long newStart = Math.max(0, Math.min(maxStart, line.startMs + deltaMs));
                line.startMs = newStart;
                subtitleAdapter.notifyItemChanged(position);
                autoSaveSubtitles();
            }

            @Override
            public void onAdjustEnd(int position, SubtitleLine line, long deltaMs) {
                long maxEnd = mediaDuration > 0 ? mediaDuration : Long.MAX_VALUE;
                long minEnd = line.startMs >= 0 ? line.startMs + 100 : 0;
                long newEnd = Math.max(minEnd, Math.min(maxEnd, line.endMs + deltaMs));
                line.endMs = newEnd;
                subtitleAdapter.notifyItemChanged(position);
                autoSaveSubtitles();
            }
        });
    }

    private void showLineOptionsDialog(int position, SubtitleLine line) {
        String[] options = {
                getString(R.string.option_copy_text),
                getString(R.string.option_edit_text),
                getString(R.string.option_edit_timestamp),
                getString(R.string.option_delete),
                getString(R.string.option_loop_this),
                getString(R.string.option_set_range_start)
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.line_options_title, position + 1))
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: copyLineText(line); break;
                        case 1: editLineText(position, line); break;
                        case 2: editLineTimestamp(position, line); break;
                        case 3: deleteLine(position); break;
                        case 4:
                            if (line.hasTimestamp()) {
                                selectedLineIndex = position;
                                showLoopCountDialog(LoopMode.SINGLE, position, position);
                            } else {
                                Toast.makeText(this, R.string.line_no_timestamp, Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case 5:
                            loopRangeStart = position;
                            Toast.makeText(this, getString(R.string.range_start_set, position + 1),
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .show();
    }

    private void copyLineText(SubtitleLine line) {
        if (line == null || line.text == null) return;
        ClipboardManager cm =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("subtitle", line.text));
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    private void editLineText(int position, SubtitleLine line) {
        EditText input = new EditText(this);
        input.setText(line.text);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        int paddingPx = (int) (getResources().getDisplayMetrics().density * 20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_text_title)
                .setView(container)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!text.isEmpty()) {
                        line.text = text;
                        subtitleAdapter.notifyItemChanged(position);
                        autoSaveSubtitles();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void editLineTimestamp(int position, SubtitleLine line) {
        // Build a small dialog with two human-readable time fields and "Hiện tại"
        // shortcut buttons that fill in the current playback position. Far
        // friendlier than typing milliseconds.
        int paddingPx = (int) (getResources().getDisplayMetrics().density * 16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(paddingPx, paddingPx, paddingPx, 0);

        EditText etStart = new EditText(this);
        etStart.setHint(R.string.start_ms_label);
        etStart.setInputType(InputType.TYPE_CLASS_TEXT);
        if (line.startMs >= 0) etStart.setText(TimeFormatter.formatPrecise(line.startMs));

        EditText etEnd = new EditText(this);
        etEnd.setHint(R.string.end_ms_label);
        etEnd.setInputType(InputType.TYPE_CLASS_TEXT);
        if (line.endMs >= 0) etEnd.setText(TimeFormatter.formatPrecise(line.endMs));

        TextView btnUseStart = new TextView(this);
        btnUseStart.setText(R.string.action_use_current);
        btnUseStart.setTextColor(getColor(R.color.brand_primary));
        btnUseStart.setPadding(paddingPx / 2, paddingPx / 4, paddingPx / 2, paddingPx / 4);
        btnUseStart.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                etStart.setText(TimeFormatter.formatPrecise(mediaPlayer.getCurrentPosition()));
            }
        });

        TextView btnUseEnd = new TextView(this);
        btnUseEnd.setText(R.string.action_use_current);
        btnUseEnd.setTextColor(getColor(R.color.brand_primary));
        btnUseEnd.setPadding(paddingPx / 2, paddingPx / 4, paddingPx / 2, paddingPx / 4);
        btnUseEnd.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                etEnd.setText(TimeFormatter.formatPrecise(mediaPlayer.getCurrentPosition()));
            }
        });

        TextView lblStart = new TextView(this);
        lblStart.setText(R.string.start_ms_label);
        lblStart.setTextColor(getColor(R.color.text_primary));
        TextView lblEnd = new TextView(this);
        lblEnd.setText(R.string.end_ms_label);
        lblEnd.setTextColor(getColor(R.color.text_primary));
        TextView hint = new TextView(this);
        hint.setText(R.string.edit_timestamp_hint);
        hint.setTextSize(11f);
        hint.setTextColor(getColor(R.color.text_secondary));

        root.addView(lblStart);
        LinearLayout startRow = new LinearLayout(this);
        startRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        startRow.addView(etStart, etParams);
        startRow.addView(btnUseStart);
        root.addView(startRow);

        root.addView(lblEnd);
        LinearLayout endRow = new LinearLayout(this);
        endRow.setOrientation(LinearLayout.HORIZONTAL);
        endRow.addView(etEnd, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        endRow.addView(btnUseEnd);
        root.addView(endRow);

        root.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_timestamp_title)
                .setView(root)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    long start = parseHumanTime(etStart.getText().toString().trim());
                    long end = parseHumanTime(etEnd.getText().toString().trim());
                    if (start < 0 || end < 0 || end <= start) {
                        Toast.makeText(this, R.string.invalid_time_format,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    line.startMs = start;
                    line.endMs = end;
                    sortLinesByTime();
                    autoSaveSubtitles();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * Parses a user-typed time string into milliseconds. Accepts:
     * raw ms ("12500"), seconds ("12.5"), "M:SS", "M:SS.mmm", "H:MM:SS",
     * "H:MM:SS.mmm". Returns -1 on parse failure.
     */
    private static long parseHumanTime(String s) {
        if (s == null) return -1;
        s = s.trim();
        if (s.isEmpty()) return -1;
        try {
            // Pure integer = milliseconds (back-compat with the old dialog).
            if (s.matches("\\d+")) {
                return Long.parseLong(s);
            }
            String[] parts = s.split(":");
            double hours = 0, minutes = 0, seconds;
            if (parts.length == 1) {
                seconds = Double.parseDouble(parts[0]);
            } else if (parts.length == 2) {
                minutes = Long.parseLong(parts[0]);
                seconds = Double.parseDouble(parts[1]);
            } else if (parts.length == 3) {
                hours = Long.parseLong(parts[0]);
                minutes = Long.parseLong(parts[1]);
                seconds = Double.parseDouble(parts[2]);
            } else {
                return -1;
            }
            double total = hours * 3600 + minutes * 60 + seconds;
            if (total < 0) return -1;
            return Math.round(total * 1000);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void deleteLine(int position) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_line_confirm)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    SubtitleLine removed = subtitleLines.remove(position);
                    // Reset selection/active tracking — the indices we cached
                    // may now point at the wrong line (or past the end).
                    if (selectedLineIndex == position) {
                        selectedLineIndex = -1;
                    } else if (selectedLineIndex > position) {
                        selectedLineIndex--;
                    }
                    lastActiveIndex = -1;
                    subtitleAdapter.setLines(new ArrayList<>(subtitleLines));
                    if (selectedLineIndex >= 0) {
                        subtitleAdapter.setActiveIndex(selectedLineIndex);
                    }
                    autoSaveSubtitles();

                    // Restore matching pool entries so user can re-pick them.
                    // Uses the explicit source-pool indices captured when the
                    // line was created from the pool, so we restore *exactly*
                    // those entries (no substring false positives).
                    restorePoolEntries(removed);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void clearAllSubtitles() {
        if (subtitleLines.isEmpty()) {
            Toast.makeText(this, R.string.no_subs_to_clear, Toast.LENGTH_SHORT).show();
            return;
        }
        final int count = subtitleLines.size();
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_all_confirm_title)
                .setMessage(getString(R.string.clear_all_confirm_message, count))
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    subtitleLines.clear();
                    selectedLineIndex = -1;
                    lastActiveIndex = -1;
                    // Reset any pool "used" flags so the user can re-assign
                    // bulk-pasted entries from scratch.
                    for (SubPoolAdapter.PoolEntry entry : subPool) {
                        entry.used = false;
                    }
                    clearLoop();
                    subtitleAdapter.setLines(new ArrayList<>(subtitleLines));
                    subtitleAdapter.setActiveIndex(-1);
                    autoSaveSubtitles();
                    Toast.makeText(this, R.string.cleared_all_subs,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Restores pool entries that were the source of a deleted subtitle line.
     *  Uses the explicit {@code sourcePoolIndices} list captured when the line
     *  was created, so we never restore unrelated entries that just happened
     *  to be substring matches. Falls back to the legacy substring check for
     *  legacy lines / lines loaded from the database that don't carry the
     *  source-index list. */
    private void restorePoolEntries(SubtitleLine deletedLine) {
        if (subPool.isEmpty() || deletedLine == null) return;
        if (!deletedLine.sourcePoolIndices.isEmpty()) {
            for (SubPoolAdapter.PoolEntry entry : subPool) {
                if (entry.used && deletedLine.sourcePoolIndices.contains(entry.originalIndex)) {
                    entry.used = false;
                }
            }
            return;
        }
        // Legacy path: no source indices recorded — fall back to text match.
        String deletedText = deletedLine.text;
        if (deletedText == null) return;
        for (SubPoolAdapter.PoolEntry entry : subPool) {
            if (entry.used && deletedText.contains(entry.text)) {
                entry.used = false;
            }
        }
    }

    private boolean hasUnusedPoolEntries() {
        if (subPool.isEmpty()) return false;
        for (SubPoolAdapter.PoolEntry entry : subPool) {
            if (!entry.used) return true;
        }
        return false;
    }

    // ======================== WAVE-TAP MODE ========================

    /** Routes a waveform tap (in ms) into the current Set-Start / Set-End
     *  picker. Each tap overwrites the previously-picked value. */
    private void onWaveTap(int posMs) {
        if (mediaDuration <= 0) return;
        if (selectedLineIndex < 0 || selectedLineIndex >= subtitleLines.size()) {
            Toast.makeText(this, R.string.wave_mode_no_line, Toast.LENGTH_SHORT).show();
            return;
        }
        if (waveTapTarget == WaveTapTarget.START) {
            waveTapStartMs = posMs;
            waveformView.setStartMarker((float) posMs / mediaDuration);
            Toast.makeText(this, getString(R.string.wave_mode_start_at,
                    TimeFormatter.format(posMs)), Toast.LENGTH_SHORT).show();
        } else if (waveTapTarget == WaveTapTarget.END) {
            waveTapEndMs = posMs;
            waveformView.setEndMarker((float) posMs / mediaDuration);
            Toast.makeText(this, getString(R.string.wave_mode_end_at,
                    TimeFormatter.format(posMs)), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleWaveTapMode() {
        waveTapMode = !waveTapMode;
        btnWaveMode.setSelected(waveTapMode);
        btnWaveMode.setText(waveTapMode
                ? R.string.action_wave_mode_on
                : R.string.action_wave_mode);
        btnConfirmWaveLine.setVisibility(waveTapMode ? View.VISIBLE : View.GONE);
        if (waveTapMode) {
            // Pre-select the first untimed line so Set Start / Set End have a
            // target without forcing the user to long-press one first.
            if (selectedLineIndex < 0 || selectedLineIndex >= subtitleLines.size()) {
                int autoIdx = findFirstUntimedLine();
                if (autoIdx >= 0) {
                    selectedLineIndex = autoIdx;
                    subtitleAdapter.setActiveIndex(autoIdx);
                    layoutManager.scrollToPositionWithOffset(autoIdx, 100);
                }
            }
            Toast.makeText(this, R.string.wave_mode_enabled, Toast.LENGTH_LONG).show();
        } else {
            clearWaveTapState();
            Toast.makeText(this, R.string.wave_mode_disabled, Toast.LENGTH_SHORT).show();
        }
    }

    /** Wipes any in-progress wave-tap selection and resets the visual state. */
    private void clearWaveTapState() {
        waveTapTarget = WaveTapTarget.NONE;
        waveTapStartMs = -1;
        waveTapEndMs = -1;
        if (waveformView != null) {
            waveformView.setStartMarker(-1f);
            waveformView.setEndMarker(-1f);
        }
        if (btnSetStart != null) {
            btnSetStart.setSelected(false);
            btnSetStart.setText(R.string.action_set_start);
            btnSetStart.setTag(null);
        }
        if (btnSetEnd != null) {
            btnSetEnd.setSelected(false);
        }
    }

    private void confirmWaveTapLine() {
        if (!waveTapMode) return;
        if (selectedLineIndex < 0 || selectedLineIndex >= subtitleLines.size()) {
            Toast.makeText(this, R.string.wave_mode_no_line, Toast.LENGTH_SHORT).show();
            return;
        }
        if (waveTapStartMs < 0) {
            Toast.makeText(this, R.string.wave_mode_need_start, Toast.LENGTH_SHORT).show();
            return;
        }
        if (waveTapEndMs < 0) {
            Toast.makeText(this, R.string.wave_mode_need_end, Toast.LENGTH_SHORT).show();
            return;
        }
        if (waveTapEndMs <= waveTapStartMs) {
            Toast.makeText(this, R.string.wave_mode_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        SubtitleLine line = subtitleLines.get(selectedLineIndex);
        line.startMs = waveTapStartMs;
        line.endMs = waveTapEndMs;
        sortLinesByTime();
        autoSaveSubtitles();

        // Reset scratch state and markers for the next line.
        waveTapTarget = WaveTapTarget.NONE;
        waveTapStartMs = -1;
        waveTapEndMs = -1;
        waveformView.setStartMarker(-1f);
        waveformView.setEndMarker(-1f);
        btnSetStart.setSelected(false);
        btnSetEnd.setSelected(false);

        int next = findFirstUntimedLine();
        if (next >= 0) {
            selectedLineIndex = next;
            subtitleAdapter.setActiveIndex(next);
            layoutManager.scrollToPositionWithOffset(next, 100);
            Toast.makeText(this, R.string.wave_mode_confirmed, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.wave_mode_all_done, Toast.LENGTH_SHORT).show();
        }
    }

    // ======================== BOTTOM ACTIONS ========================

    private void bindBottomActions() {
        // Apply system gesture / nav-bar insets to the bottom action row so
        // the buttons (Open file, Paste sub, Share, …) don't sit underneath
        // the system "swipe to multitask" gesture area at the bottom edge.
        View bottomScroll = findViewById(R.id.bottom_action_scroll);
        if (bottomScroll != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomScroll, (v, insets) -> {
                int inset = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.systemGestures()
                ).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                        v.getPaddingRight(), inset);
                return insets;
            });
        }

        findViewById(R.id.btn_add_line).setOnClickListener(v -> {
            if (mediaPlayer == null) {
                Toast.makeText(this, R.string.error_no_media, Toast.LENGTH_SHORT).show();
                return;
            }
            showNewSubtitleDialog(-1, -1);
        });

        findViewById(R.id.btn_paste_sub).setOnClickListener(v -> showPasteSubDialog());

        findViewById(R.id.btn_import_text).setOnClickListener(v ->
                importTextLauncher.launch(new String[]{"text/plain"}));

        findViewById(R.id.btn_import_srt).setOnClickListener(v ->
                importSrtLauncher.launch(new String[]{
                        "application/x-subrip",
                        "text/srt",
                        "application/octet-stream",
                        "text/plain"
                }));

        findViewById(R.id.btn_export_srt).setOnClickListener(v -> {
            if (subtitleLines.isEmpty()) {
                Toast.makeText(this, R.string.no_subtitles_to_export, Toast.LENGTH_SHORT).show();
                return;
            }
            String name = currentFileName != null
                    ? currentFileName.replaceAll("\\.[^.]+$", "") + ".srt"
                    : "subtitles.srt";
            exportSrtLauncher.launch(name);
        });

        findViewById(R.id.btn_share_srt).setOnClickListener(v -> shareSrt());

        findViewById(R.id.btn_open_file).setOnClickListener(v ->
                mediaPickerLauncher.launch(new String[]{"audio/*", "video/*"}));

        findViewById(R.id.btn_clear_all).setOnClickListener(v -> clearAllSubtitles());
    }

    // ======================== PASTE SUB (BULK) ========================

    private void showPasteSubDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.paste_sub_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(6);
        input.setGravity(android.view.Gravity.TOP);

        int paddingPx = (int) (getResources().getDisplayMetrics().density * 16);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.paste_sub_title)
                .setView(container)
                .setPositiveButton(R.string.action_confirm, (d, w) -> {
                    String raw = input.getText() != null ? input.getText().toString() : "";
                    processBulkPaste(raw);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void processBulkPaste(String raw) {
        if (raw.trim().isEmpty()) return;

        subPool.clear();
        String[] lines = raw.split("\n");
        int idx = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                subPool.add(new SubPoolAdapter.PoolEntry(idx++, trimmed));
            }
        }

        if (subPool.isEmpty()) {
            Toast.makeText(this, R.string.paste_sub_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this,
                getString(R.string.paste_sub_loaded, subPool.size()),
                Toast.LENGTH_SHORT).show();
    }

    // ======================== IMPORT / EXPORT ========================

    private void importTextFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            List<SubtitleLine> imported = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    imported.add(new SubtitleLine(-1, -1, trimmed));
                }
            }
            reader.close();
            is.close();

            if (imported.isEmpty()) {
                Toast.makeText(this, R.string.import_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            subtitleLines.clear();
            subtitleLines.addAll(imported);
            subtitleAdapter.setLines(new ArrayList<>(subtitleLines));
            autoSaveSubtitles();
            Toast.makeText(this, getString(R.string.import_success, imported.size()),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void importSrtFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            is.close();

            List<SubtitleLine> parsed = SrtParser.parse(sb.toString());
            if (parsed.isEmpty()) {
                Toast.makeText(this, R.string.import_srt_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            subtitleLines.clear();
            subtitleLines.addAll(parsed);
            subtitleAdapter.setLines(new ArrayList<>(subtitleLines));
            autoSaveSubtitles();
            Toast.makeText(this, getString(R.string.import_success, parsed.size()),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void exportSrtToUri(Uri uri) {
        try {
            String srt = SrtExporter.export(subtitleLines);
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                os.write(srt.getBytes());
                os.close();
            }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show();

            // Mark as completed in history
            if (currentFileUri != null) {
                SubToolApp.get().getDbExecutor().execute(() -> {
                    LocalMediaHistoryDao dao = SubToolApp.get().getDatabase().localMediaHistoryDao();
                    LocalMediaHistoryEntity entity = dao.findByUri(currentFileUri.toString());
                    if (entity != null) {
                        entity.hasCompletedSrt = true;
                        dao.update(entity);
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSrt() {
        if (subtitleLines.isEmpty()) {
            Toast.makeText(this, R.string.no_subtitles_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        String srt = SrtExporter.export(subtitleLines);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, srt);
        intent.putExtra(Intent.EXTRA_SUBJECT,
                currentFileName != null ? currentFileName + " - Subtitles" : "Subtitles");
        startActivity(Intent.createChooser(intent, getString(R.string.share_srt_title)));
    }

    // ======================== PERSISTENCE ========================

    private void recordHistory(Uri uri) {
        SubToolApp.get().getDbExecutor().execute(() -> {
            LocalMediaHistoryDao dao = SubToolApp.get().getDatabase().localMediaHistoryDao();
            LocalMediaHistoryEntity existing = dao.findByUri(uri.toString());
            if (existing != null) {
                existing.openedAt = System.currentTimeMillis();
                existing.durationMs = mediaDuration;
                if (currentFileName != null) existing.fileName = currentFileName;
                dao.update(existing);
            } else {
                LocalMediaHistoryEntity entity = new LocalMediaHistoryEntity();
                entity.fileUri = uri.toString();
                entity.fileName = currentFileName != null ? currentFileName : "";
                entity.mediaType = isVideo ? "video" : "audio";
                entity.durationMs = mediaDuration;
                entity.openedAt = System.currentTimeMillis();
                dao.upsert(entity);
            }
        });
    }

    private void autoSaveSubtitles() {
        if (currentFileUri == null) return;
        String uriStr = currentFileUri.toString();
        SubToolApp.get().getDbExecutor().execute(() -> {
            LocalSubtitleDao dao = SubToolApp.get().getDatabase().localSubtitleDao();
            dao.deleteByMediaUri(uriStr);
            List<LocalSubtitleEntity> entities = new ArrayList<>();
            for (int i = 0; i < subtitleLines.size(); i++) {
                SubtitleLine line = subtitleLines.get(i);
                LocalSubtitleEntity e = new LocalSubtitleEntity();
                e.mediaUri = uriStr;
                e.lineIndex = i;
                e.text = line.text;
                e.startMs = line.startMs;
                e.endMs = line.endMs;
                entities.add(e);
            }
            dao.insertAll(entities);
        });
    }

    private void loadSavedSubtitles(Uri uri) {
        SubToolApp.get().getDbExecutor().execute(() -> {
            LocalSubtitleDao dao = SubToolApp.get().getDatabase().localSubtitleDao();
            List<LocalSubtitleEntity> entities = dao.getByMediaUri(uri.toString());
            runOnUiThread(() -> {
                subtitleLines.clear();
                selectedLineIndex = -1;
                lastActiveIndex = -1;
                if (entities != null && !entities.isEmpty()) {
                    for (LocalSubtitleEntity e : entities) {
                        subtitleLines.add(new SubtitleLine(e.startMs, e.endMs, e.text));
                    }
                    sortLinesByTime();
                } else {
                    subtitleAdapter.setLines(new ArrayList<>(subtitleLines));
                }
            });
        });
    }

    // ======================== LIFECYCLE ========================

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tickRunnable);
        releasePlayer();
        super.onDestroy();
    }
}
