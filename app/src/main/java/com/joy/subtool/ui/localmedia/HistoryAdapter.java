package com.joy.subtool.ui.localmedia;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.joy.subtool.R;
import com.joy.subtool.data.LocalMediaHistoryEntity;
import com.joy.subtool.util.TimeFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onClick(LocalMediaHistoryEntity entity);
    }

    public interface OnItemLongClickListener {
        void onLongClick(LocalMediaHistoryEntity entity);
    }

    private List<LocalMediaHistoryEntity> items = new ArrayList<>();
    @Nullable private OnItemClickListener clickListener;
    @Nullable private OnItemLongClickListener longClickListener;

    public void submit(List<LocalMediaHistoryEntity> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemLongClickListener(@Nullable OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocalMediaHistoryEntity entity = items.get(position);

        holder.tvFileName.setText(entity.fileName);
        holder.tvDuration.setText(TimeFormatter.format(entity.durationMs));
        holder.tvDate.setText(formatDate(entity.openedAt));
        holder.ivType.setImageResource(
                "video".equals(entity.mediaType) ? R.drawable.ic_video : R.drawable.ic_audio);

        if (entity.hasCompletedSrt) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(R.string.status_completed);
        } else {
            holder.tvStatus.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(entity);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(entity);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date(timestamp));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivType;
        final TextView tvFileName;
        final TextView tvDuration;
        final TextView tvDate;
        final TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivType = itemView.findViewById(R.id.iv_type);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}
