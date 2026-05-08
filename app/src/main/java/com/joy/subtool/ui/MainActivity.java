package com.joy.subtool.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.joy.subtool.R;
import com.joy.subtool.ui.localmedia.LocalMediaActivity;
import com.joy.subtool.ui.localmedia.LocalMediaHistoryActivity;

/**
 * Launcher screen with three shortcuts: pick a media file, open the history of
 * previously-loaded files, or open the app settings. Picking a file forwards to
 * {@link LocalMediaActivity} with the chosen URI as the intent data, matching
 * the same flow used by the history list.
 */
public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String[]> mediaPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    openMedia(uri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
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

        findViewById(R.id.btn_main_pick_file).setOnClickListener(v ->
                mediaPicker.launch(new String[]{"audio/*", "video/*"}));
        findViewById(R.id.btn_main_history).setOnClickListener(v ->
                startActivity(new Intent(this, LocalMediaHistoryActivity.class)));
        findViewById(R.id.btn_main_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void openMedia(Uri uri) {
        Intent intent = new Intent(this, LocalMediaActivity.class);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivity(intent);
    }
}
