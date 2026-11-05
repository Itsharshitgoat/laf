package com.laf.lostandfound.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.laf.lostandfound.databinding.ItemMatchBinding;
import com.laf.lostandfound.models.MatchRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared list of possible lost/found matches. Used by the owner's item detail
 * screen and by the "Possible matches" section of the profile.
 */
public class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.MatchViewHolder> {

    public interface MatchActionListener {
        void onOpen(MatchRecord match);

        void onDismiss(MatchRecord match);
    }

    private final List<MatchRecord> matches = new ArrayList<>();
    private final MatchActionListener listener;

    public MatchAdapter(MatchActionListener listener) {
        this.listener = listener;
    }

    public void updateMatches(List<MatchRecord> newMatches) {
        matches.clear();
        if (newMatches != null) matches.addAll(newMatches);
        notifyDataSetChanged();
    }

    public void remove(MatchRecord match) {
        int index = matches.indexOf(match);
        if (index >= 0) {
            matches.remove(index);
            notifyItemRemoved(index);
        }
    }

    public int size() {
        return matches.size();
    }

    @NonNull
    @Override
    public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMatchBinding binding = ItemMatchBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MatchViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
        holder.bind(matches.get(position));
    }

    @Override
    public int getItemCount() {
        return matches.size();
    }

    class MatchViewHolder extends RecyclerView.ViewHolder {

        private final ItemMatchBinding binding;

        MatchViewHolder(@NonNull ItemMatchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MatchRecord match) {
            if (match == null) return;

            String title = match.getMatchedItemTitle();
            binding.tvMatchTitle.setText(title == null || title.trim().isEmpty()
                    ? "Possible match" : title.trim());
            binding.tvMatchScore.setText(Math.max(0, Math.min(100, match.getScore())) + "%");

            String ownTitle = match.getItemTitle();
            binding.tvMatchMeta.setText(ownTitle == null || ownTitle.trim().isEmpty()
                    ? "Suggested opposite-type report"
                    : "May match your report: " + ownTitle.trim());

            binding.btnOpenMatch.setOnClickListener(v -> {
                if (listener != null) listener.onOpen(match);
            });
            binding.btnDismissMatch.setOnClickListener(v -> {
                if (listener != null) listener.onDismiss(match);
            });
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onOpen(match);
            });
        }
    }
}
