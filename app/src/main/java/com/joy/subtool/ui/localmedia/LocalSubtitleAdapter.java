package com.joy.subtool.ui.localmedia;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.joy.subtool.R;
import com.joy.subtool.model.SubtitleLine;
import com.joy.subtool.util.TimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class LocalSubtitleAdapter extends RecyclerView.Adapter<LocalSubtitleAdapter.ViewHolder> {

    public interface OnLineClickListener {
        void onClick(int position, SubtitleLine line);
    }

    public interface OnLineLongClickListener {
        void onLongClick(int position, SubtitleLine line);
    }

    private List<SubtitleLine> lines = new ArrayList<>();
    private int activeIndex = -1;
    private int loopStartIndex = -1;
    private int loopEndIndex = -1;
    @Nullable
    private OnLineClickListener clickListener;
    @Nullable
    private OnLineLongClickListener longClickListener;

    public void setLines(List<SubtitleLine> newLines) {
        this.lines = newLines != null ? newLines : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<SubtitleLine> getLines() {
        return lines;
    }

    public void setActiveIndex(int index) {
        int old = activeIndex;
        activeIndex = index;
        if (old >= 0 && old < lines.size()) notifyItemChanged(old);
        if (index >= 0 && index < lines.size()) notifyItemChanged(index);
    }

    public void setLoopRange(int start, int end) {
        int oldStart = loopStartIndex;
        int oldEnd = loopEndIndex;
        loopStartIndex = start;
        loopEndIndex = end;
        if (oldStart >= 0 && oldEnd >= 0) {
            for (int i = oldStart; i <= oldEnd && i < lines.size(); i++) {
                notifyItemChanged(i);
            }
        }
        if (start >= 0 && end >= 0) {
            for (int i = start; i <= end && i < lines.size(); i++) {
                notifyItemChanged(i);
            }
        }
    }

    public void clearLoopRange() {
        setLoopRange(-1, -1);
    }

    public void setOnLineClickListener(@Nullable OnLineClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnLineLongClickListener(@Nullable OnLineLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_local_subtitle, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SubtitleLine line = lines.get(position);

        holder.tvIndex.setText(String.valueOf(position + 1));
        holder.tvText.setText(line.text);

        if (line.hasTimestamp()) {
            holder.tvTime.setText(
                    TimeFormatter.format(line.startMs) + " - " + TimeFormatter.format(line.endMs));
            holder.tvTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvTime.setText("--:-- - --:--");
            holder.tvTime.setVisibility(View.VISIBLE);
        }

        boolean isActive = position == activeIndex;
        boolean isInLoopRange = loopStartIndex >= 0 && loopEndIndex >= 0
                && position >= loopStartIndex && position <= loopEndIndex;

        if (isActive) {
            holder.itemView.setBackgroundColor(0x1F1976D2);
        } else if (isInLoopRange) {
            holder.itemView.setBackgroundColor(0x1F26A69A);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(position, line);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(position, line);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIndex;
        final TextView tvText;
        final TextView tvTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tv_index);
            tvText = itemView.findViewById(R.id.tv_text);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}
