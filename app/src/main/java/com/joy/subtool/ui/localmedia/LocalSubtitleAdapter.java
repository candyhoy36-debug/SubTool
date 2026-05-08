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

    public interface OnTimeAdjustListener {
        void onAdjustStart(int position, SubtitleLine line, long deltaMs);
        void onAdjustEnd(int position, SubtitleLine line, long deltaMs);
    }

    private static final long TIME_ADJUST_STEP_MS = 500L;

    private List<SubtitleLine> lines = new ArrayList<>();
    private int activeIndex = -1;
    private int loopStartIndex = -1;
    private int loopEndIndex = -1;
    @Nullable
    private OnLineClickListener clickListener;
    @Nullable
    private OnLineLongClickListener longClickListener;
    @Nullable
    private OnTimeAdjustListener timeAdjustListener;

    public void setLines(List<SubtitleLine> newLines) {
        this.lines = newLines != null ? newLines : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<SubtitleLine> getLines() {
        return lines;
    }

    public void setActiveIndex(int index) {
        int old = activeIndex;
        if (old == index) return;
        activeIndex = index;
        if (old >= 0 && old < lines.size()) notifyItemChanged(old);
        if (index >= 0 && index < lines.size()) notifyItemChanged(index);
    }

    public int getActiveIndex() {
        return activeIndex;
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

    public void setOnTimeAdjustListener(@Nullable OnTimeAdjustListener listener) {
        this.timeAdjustListener = listener;
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
            holder.timeAdjustContainer.setVisibility(View.VISIBLE);
        } else {
            holder.tvTime.setText("--:-- - --:--");
            holder.tvTime.setVisibility(View.VISIBLE);
            holder.timeAdjustContainer.setVisibility(View.GONE);
        }

        holder.btnStartMinus.setOnClickListener(v -> {
            if (timeAdjustListener != null) {
                timeAdjustListener.onAdjustStart(position, line, -TIME_ADJUST_STEP_MS);
            }
        });
        holder.btnStartPlus.setOnClickListener(v -> {
            if (timeAdjustListener != null) {
                timeAdjustListener.onAdjustStart(position, line, TIME_ADJUST_STEP_MS);
            }
        });
        holder.btnEndMinus.setOnClickListener(v -> {
            if (timeAdjustListener != null) {
                timeAdjustListener.onAdjustEnd(position, line, -TIME_ADJUST_STEP_MS);
            }
        });
        holder.btnEndPlus.setOnClickListener(v -> {
            if (timeAdjustListener != null) {
                timeAdjustListener.onAdjustEnd(position, line, TIME_ADJUST_STEP_MS);
            }
        });

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
        final View timeAdjustContainer;
        final TextView btnStartMinus;
        final TextView btnStartPlus;
        final TextView btnEndMinus;
        final TextView btnEndPlus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tv_index);
            tvText = itemView.findViewById(R.id.tv_text);
            tvTime = itemView.findViewById(R.id.tv_time);
            timeAdjustContainer = itemView.findViewById(R.id.time_adjust_container);
            btnStartMinus = itemView.findViewById(R.id.btn_start_minus);
            btnStartPlus = itemView.findViewById(R.id.btn_start_plus);
            btnEndMinus = itemView.findViewById(R.id.btn_end_minus);
            btnEndPlus = itemView.findViewById(R.id.btn_end_plus);
        }
    }
}
