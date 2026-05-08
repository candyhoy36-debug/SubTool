package com.joy.subtool.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.joy.subtool.R;
import com.joy.subtool.util.AppPrefs;

import java.util.Locale;

/**
 * Settings screen. Currently exposes a single configurable value: the rewind /
 * forward step (in milliseconds) used by {@link
 * com.joy.subtool.ui.localmedia.LocalMediaActivity}.
 */
public class SettingsActivity extends AppCompatActivity {

    /** Preset values offered in the picker dialog (in milliseconds). */
    private static final int[] STEP_PRESETS_MS = {
            500, 1000, 2000, 3000, 5000, 10_000, 15_000, 30_000
    };

    private TextView tvSkipStepSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSkipStepSummary = findViewById(R.id.tv_skip_step_summary);
        View row = findViewById(R.id.row_skip_step);
        row.setOnClickListener(v -> showSkipStepDialog());

        refreshSummary();
    }

    private void refreshSummary() {
        int ms = AppPrefs.getSkipStepMs(this);
        tvSkipStepSummary.setText(getString(R.string.settings_skip_step_summary, formatStep(ms)));
    }

    private static String formatStep(int ms) {
        if (ms % 1000 == 0) {
            return String.format(Locale.US, "%ds", ms / 1000);
        }
        return String.format(Locale.US, "%.1fs", ms / 1000.0);
    }

    private void showSkipStepDialog() {
        String[] labels = new String[STEP_PRESETS_MS.length + 1];
        for (int i = 0; i < STEP_PRESETS_MS.length; i++) {
            labels[i] = formatStep(STEP_PRESETS_MS[i]);
        }
        labels[STEP_PRESETS_MS.length] = getString(R.string.settings_skip_step_custom);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_skip_step_dialog_title)
                .setItems(labels, (d, which) -> {
                    if (which < STEP_PRESETS_MS.length) {
                        AppPrefs.setSkipStepMs(this, STEP_PRESETS_MS[which]);
                        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
                        refreshSummary();
                    } else {
                        showCustomStepDialog();
                    }
                })
                .show();
    }

    private void showCustomStepDialog() {
        EditText input = new EditText(this);
        input.setHint("1.5");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.1f",
                AppPrefs.getSkipStepMs(this) / 1000.0));

        int paddingPx = (int) (getResources().getDisplayMetrics().density * 20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_skip_step_custom_title)
                .setView(container)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String raw = input.getText() != null
                            ? input.getText().toString().trim() : "";
                    try {
                        double seconds = Double.parseDouble(raw);
                        int ms = (int) Math.round(seconds * 1000);
                        if (ms < AppPrefs.MIN_SKIP_STEP_MS
                                || ms > AppPrefs.MAX_SKIP_STEP_MS) {
                            Toast.makeText(this, R.string.settings_invalid_value,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        AppPrefs.setSkipStepMs(this, ms);
                        Toast.makeText(this, R.string.settings_saved,
                                Toast.LENGTH_SHORT).show();
                        refreshSummary();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, R.string.settings_invalid_value,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
