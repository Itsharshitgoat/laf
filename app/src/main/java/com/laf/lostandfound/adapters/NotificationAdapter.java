package com.laf.lostandfound.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.laf.lostandfound.R;
import com.laf.lostandfound.databinding.ItemNotificationBinding;
import com.laf.lostandfound.models.Notification;
import com.laf.lostandfound.utils.TimeUtils;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
        default void onNotificationDelete(Notification notification) {}
    }

    private final List<Notification> notificationList;
    private final OnNotificationClickListener listener;

    public NotificationAdapter(List<Notification> notificationList, OnNotificationClickListener listener) {
        this.notificationList = notificationList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNotificationBinding binding = ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new NotificationViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        holder.bind(notificationList.get(position));
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {

        private final ItemNotificationBinding binding;

        public NotificationViewHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Notification notification) {
            binding.tvNotificationTitle.setText(notification.getTitle());
            binding.tvNotificationMessage.setText(notification.getMessage());
            binding.tvNotificationTime.setText(TimeUtils.getTimeAgo(notification.getTimestamp()));

            binding.vUnreadDot.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

            if ("match".equalsIgnoreCase(notification.getType())) {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_search);
            } else if ("claim".equalsIgnoreCase(notification.getType())) {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_ticket_active);
            } else if ("returned".equalsIgnoreCase(notification.getType())) {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_returned_box);
            } else {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_notification);
            }

            binding.btnDeleteNotification.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationDelete(notification);
                }
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(notification);
                }
            });
        }
    }
}
