package com.joy.subtool.ui.localmedia;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.joy.subtool.R;
import com.joy.subtool.SubToolApp;
import com.joy.subtool.data.LocalMediaHistoryDao;
import com.joy.subtool.data.LocalMediaHistoryEntity;

import java.util.Collections;
import java.util.List;

public class LocalMediaHistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private TextView tvEmpty;
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_history);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvHistory = findViewById(R.id.rv_history);
        tvEmpty = findViewById(R.id.tv_empty);
        adapter = new HistoryAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);

        adapter.setOnItemClickListener((entity) -> {
            Intent intent = new Intent(this, LocalMediaActivity.class);
            intent.setData(Uri.parse(entity.fileUri));
            startActivity(intent);
            finish();
        });

        adapter.setOnItemLongClickListener((entity) -> {
            new AlertDialog.Builder(this)
                    .setTitle(entity.fileName)
                    .setItems(new String[]{getString(R.string.action_delete)}, (d, w) -> {
                        SubToolApp.get().getDbExecutor().execute(() -> {
                            SubToolApp.get().getDatabase().localMediaHistoryDao().deleteById(entity.id);
                            runOnUiThread(this::reload);
                        });
                    })
                    .show();
        });

        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        SubToolApp.get().getDbExecutor().execute(() -> {
            LocalMediaHistoryDao dao = SubToolApp.get().getDatabase().localMediaHistoryDao();
            final List<LocalMediaHistoryEntity> rows = dao.getAll();
            runOnUiThread(() -> {
                adapter.submit(rows != null ? rows : Collections.emptyList());
                boolean empty = rows == null || rows.isEmpty();
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }
}
