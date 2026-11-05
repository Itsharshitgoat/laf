package com.laf.lostandfound.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.laf.lostandfound.R;
import com.laf.lostandfound.databinding.ItemActivityBinding;
import com.laf.lostandfound.models.ActivityItem;
import com.laf.lostandfound.utils.Constants;
import com.laf.lostandfound.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder> {

    public interface OnActivityClickListener {
        void onActivityClick(ActivityItem activity);
    }

    private List<ActivityItem> activities;
    private final OnActivityClickListener listener;

    public ActivityAdapter(List<ActivityItem> activities, OnActivityClickListener listener) {
        this.activities = activities != null ? activities : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ActivityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemActivityBinding binding = ItemActivityBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ActivityViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ActivityViewHolder holder, int position) {
        holder.bind(activities.get(position));
    }

    @Override
    public int getItemCount() {
        return activities.size();
    }

    public void updateItems(List<ActivityItem> newActivities) {
        this.activities = newActivities != null ? newActivities : new ArrayList<>();
        notifyDataSetChanged();
    }

    class ActivityViewHolder extends RecyclerView.ViewHolder {

        private final ItemActivityBinding binding;

        ActivityViewHolder(@NonNull ItemActivityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ActivityItem activity) {
            if (activity == null) return;

            binding.tvActivityTitle.setText(activity.getTitle() == null
                    ? "Activity" : activity.getTitle());
            binding.tvActivityTime.setText(TimeUtils.getTimeAgo(activity.getTimestamp()));

            String description = activity.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                binding.tvActivityDescription.setText(description);
                binding.tvActivityDescription.setVisibility(View.VISIBLE);
            } else {
                binding.tvActivityDescription.setVisibility(View.GONE);
            }

            binding.ivActivityIcon.setImageResource(iconFor(activity.getActionType()));
            binding.ivActivityIcon.setColorFilter(binding.getRoot().getContext().getColor(
                    Constants.ACTION_REPORTED_LOST.equalsIgnoreCase(activity.getActionType())
                            ? R.color.lost_red
                            : R.color.primary));

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onActivityClick(activity);
            });
        }

        private int iconFor(String actionType) {
            if (actionType == null) return R.drawable.ic_notification;
            switch (actionType) {
                case Constants.ACTION_REPORTED_LOST:
                case Constants.ACTION_REPORTED_FOUND:
                    return R.drawable.ic_flag;
                case Constants.ACTION_MATCHED:
                    return R.drawable.ic_search;
                case Constants.ACTION_CLAIM_APPROVED:
                case Constants.ACTION_RETURNED:
                    return R.drawable.ic_returned_box;
                case Constants.ACTION_CLOSED:
                    return R.drawable.ic_lock;
                default:
                    return R.drawable.ic_notification;
            }
        }
    }
}
