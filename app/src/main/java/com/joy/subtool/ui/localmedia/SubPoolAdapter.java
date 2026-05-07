package com.joy.subtool.ui.localmedia;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.joy.subtool.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for the "sub pool" dialog — shows all remaining (unassigned)
 * subtitle lines from the bulk-paste text. The user can select one or
 * multiple consecutive lines to assign to the current start/end time.
 */
public class SubPoolAdapter extends RecyclerView.Adapter<SubPoolAdapter.ViewHolder> {

    /** One entry in the pool — carries the original index so we can remove it later. */
    public static class PoolEntry {
        public final int originalIndex;
        public final String text;

        public PoolEntry(int originalIndex, String text) {
            this.originalIndex = originalIndex;
            this.text = text;
        }
    }

    private List<PoolEntry> entries = new ArrayList<>();
    private final Set<Integer> selectedPositions = new LinkedHashSet<>();

    public void setEntries(List<PoolEntry> newEntries) {
        this.entries = newEntries != null ? new ArrayList<>(newEntries) : new ArrayList<>();
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public List<PoolEntry> getEntries() {
        return entries;
    }

    public Set<Integer> getSelectedPositions() {
        return selectedPositions;
    }

    /** Returns the selected entries in order. */
    public List<PoolEntry> getSelectedEntries() {
        List<PoolEntry> result = new ArrayList<>();
        List<Integer> sorted = new ArrayList<>(selectedPositions);
        java.util.Collections.sort(sorted);
        for (int pos : sorted) {
            if (pos >= 0 && pos < entries.size()) {
                result.add(entries.get(pos));
            }
        }
        return result;
    }

    /** Check if the selected positions are consecutive. */
    public boolean isSelectionConsecutive() {
        if (selectedPositions.size() <= 1) return true;
        List<Integer> sorted = new ArrayList<>(selectedPositions);
        java.util.Collections.sort(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) - sorted.get(i - 1) != 1) return false;
        }
        return true;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pool_entry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PoolEntry entry = entries.get(position);
        holder.tvIndex.setText(String.valueOf(entry.originalIndex + 1));
        holder.tvText.setText(entry.text);

        boolean selected = selectedPositions.contains(position);
        holder.checkBox.setChecked(selected);
        holder.itemView.setBackgroundColor(selected ? 0x1F1976D2 : Color.TRANSPARENT);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (selectedPositions.contains(pos)) {
                selectedPositions.remove(pos);
            } else {
                selectedPositions.add(pos);
            }
            notifyItemChanged(pos);
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvIndex;
        final TextView tvText;
        final CheckBox checkBox;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.tv_pool_index);
            tvText = itemView.findViewById(R.id.tv_pool_text);
            checkBox = itemView.findViewById(R.id.cb_pool_select);
        }
    }
}
