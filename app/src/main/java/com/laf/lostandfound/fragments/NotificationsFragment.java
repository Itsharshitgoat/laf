package com.laf.lostandfound.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.ListenerRegistration;
import com.laf.lostandfound.R;
import com.laf.lostandfound.activities.ItemDetailActivity;
import com.laf.lostandfound.adapters.NotificationAdapter;
import com.laf.lostandfound.databinding.FragmentNotificationsBinding;
import com.laf.lostandfound.models.Notification;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.repositories.NotificationRepository;
import com.laf.lostandfound.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment
        implements NotificationAdapter.OnNotificationClickListener {

    private FragmentNotificationsBinding binding;
    private NotificationRepository notificationRepository;
    private AuthRepository authRepository;
    private NotificationAdapter adapter;
    private final List<Notification> notificationList = new ArrayList<>();
    private static final String DEFAULT_SUBTITLE =
            "Stay updated on potential matches & claims";

    private ListenerRegistration listenerRegistration;
    private boolean listenerStarted;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        notificationRepository = NotificationRepository.getInstance();
        authRepository = AuthRepository.getInstance();

        setupRecyclerView();
        setupActions();
        loadNotifications();
    }

    private void setupRecyclerView() {
        adapter = new NotificationAdapter(notificationList, this);
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvNotifications.setAdapter(adapter);
    }

    private void setupActions() {
        binding.swipeRefresh.setColorSchemeColors(requireContext().getColor(R.color.primary));
        binding.swipeRefresh.setOnRefreshListener(this::loadNotifications);
        binding.btnMarkAllRead.setOnClickListener(v -> markAllRead());
        binding.btnRetryNotifications.setOnClickListener(v -> loadNotifications());
    }

    private void loadNotifications() {
        if (binding == null) return;

        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) {
            binding.swipeRefresh.setRefreshing(false);
            binding.progressBar.setVisibility(View.GONE);
            binding.llNotificationsError.setVisibility(View.GONE);
            showEmptyState("Sign in to see your notifications.");
            return;
        }

        if (!listenerStarted) {
            binding.progressBar.setVisibility(notificationList.isEmpty() ? View.VISIBLE : View.GONE);
        }
        binding.llNotificationsError.setVisibility(View.GONE);

        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
        listenerStarted = true;

        listenerRegistration = notificationRepository.listenToUserNotifications(uid,
                new NotificationRepository.NotificationListCallback() {
                    @Override
                    public void onSuccess(List<Notification> notifications) {
                        if (binding == null) return;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                        binding.llNotificationsError.setVisibility(View.GONE);

                        notificationList.clear();
                        if (notifications != null) notificationList.addAll(notifications);
                        adapter.notifyDataSetChanged();
                        updateState();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null) return;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);

                        if (notificationList.isEmpty()) {
                            binding.rvNotifications.setVisibility(View.GONE);
                            binding.llEmptyNotifications.setVisibility(View.GONE);
                            binding.llNotificationsError.setVisibility(View.VISIBLE);
                            binding.tvNotificationsError.setText(errorMessage);
                            binding.btnMarkAllRead.setVisibility(View.GONE);
                        } else {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void updateState() {
        if (binding == null) return;

        boolean empty = notificationList.isEmpty();
        binding.rvNotifications.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.llEmptyNotifications.setVisibility(empty ? View.VISIBLE : View.GONE);

        int unread = 0;
        for (Notification notification : notificationList) {
            if (!notification.isRead()) unread++;
        }
        binding.btnMarkAllRead.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        if (unread > 0) {
            binding.tvNotificationsSubtitle.setText(unread + (unread == 1
                    ? " unread notification" : " unread notifications"));
        } else {
            binding.tvNotificationsSubtitle.setText(DEFAULT_SUBTITLE);
        }
    }

    private void showEmptyState(String message) {
        if (binding == null) return;
        binding.rvNotifications.setVisibility(View.GONE);
        binding.llNotificationsError.setVisibility(View.GONE);
        binding.llEmptyNotifications.setVisibility(View.VISIBLE);
        binding.btnMarkAllRead.setVisibility(View.GONE);
        binding.tvNotificationsSubtitle.setText(message);
    }

    private void markAllRead() {
        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) return;

        binding.btnMarkAllRead.setEnabled(false);
        notificationRepository.markAllAsRead(uid, new NotificationRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                if (binding == null) return;
                binding.btnMarkAllRead.setEnabled(true);
                for (Notification notification : notificationList) notification.setRead(true);
                adapter.notifyDataSetChanged();
                updateState();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null) return;
                binding.btnMarkAllRead.setEnabled(true);
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onNotificationClick(Notification notification) {
        if (notification == null) return;

        if (!notification.isRead() && notification.getId() != null) {
            notification.setRead(true);
            adapter.notifyDataSetChanged();
            updateState();
            notificationRepository.markAsRead(notification.getId(), null);
        }

        if (notification.getItemId() != null && !notification.getItemId().isEmpty()) {
            Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
            intent.putExtra(Constants.EXTRA_ITEM_ID, notification.getItemId());
            startActivity(intent);
        }
    }

    @Override
    public void onNotificationDelete(Notification notification) {
        if (notification == null || notification.getId() == null) return;
        notificationRepository.deleteNotification(notification.getId(), new NotificationRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                notificationList.remove(notification);
                adapter.notifyDataSetChanged();
                updateState();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (getContext() != null) {
                    Toast.makeText(requireContext(), "Failed to delete: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        listenerStarted = false;
        binding = null;
    }
}
