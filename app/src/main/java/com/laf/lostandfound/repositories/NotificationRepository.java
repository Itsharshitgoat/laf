package com.laf.lostandfound.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.laf.lostandfound.models.Notification;
import com.laf.lostandfound.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    private static NotificationRepository instance;
    private final FirebaseFirestore db;

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface NotificationListCallback {
        void onSuccess(List<Notification> notifications);
        void onFailure(String errorMessage);
    }

    public interface CountCallback {
        void onCount(int count);
        void onFailure(String errorMessage);
    }

    private NotificationRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized NotificationRepository getInstance() {
        if (instance == null) {
            instance = new NotificationRepository();
        }
        return instance;
    }

    public void createNotification(@NonNull Notification notification, @Nullable SimpleCallback callback) {
        DocumentReference docRef = db.collection(Constants.COLLECTION_NOTIFICATIONS).document();
        notification.setId(docRef.getId());
        if (notification.getTimestamp() <= 0) {
            notification.setTimestamp(System.currentTimeMillis());
        }
        if (notification.getActorId() == null || notification.getActorId().trim().isEmpty()) {
            String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : notification.getUserId();
            notification.setActorId(uid);
        }

        docRef.set(notification.toMap())
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(message(e, "Failed to create notification"));
                    }
                });
    }

    public ListenerRegistration listenToUserNotifications(String userId,
                                                         @NonNull NotificationListCallback callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return null;
        }

        return db.collection(Constants.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Error listening to notifications"));
                        return;
                    }
                    if (querySnapshot == null) return;
                    List<Notification> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Notification notification = doc.toObject(Notification.class);
                        if (notification == null) continue;
                        notification.setId(doc.getId());
                        list.add(notification);
                    }
                    callback.onSuccess(list);
                });
    }

    public ListenerRegistration listenToUnreadCount(String userId, @NonNull CountCallback callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return null;
        }
        return db.collection(Constants.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Failed to count notifications"));
                        return;
                    }
                    callback.onCount(snapshots == null ? 0 : snapshots.size());
                });
    }

    public void markAsRead(String notificationId, SimpleCallback callback) {
        if (notificationId == null || notificationId.isEmpty()) {
            if (callback != null) callback.onFailure("Invalid notification ID");
            return;
        }

        db.collection(Constants.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(message(e, "Failed to update notification"));
                });
    }

    public void markAllAsRead(String userId, SimpleCallback callback) {
        if (userId == null || userId.isEmpty()) {
            if (callback != null) callback.onFailure("Invalid user ID");
            return;
        }

        db.collection(Constants.COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", false)
                .limit(200)
                .get()
                .addOnSuccessListener(snapshots -> {
                    WriteBatch batch = db.batch();
                    int count = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        batch.update(doc.getReference(), "read", true);
                        count++;
                    }
                    if (count == 0) {
                        if (callback != null) callback.onSuccess();
                        return;
                    }
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) {
                                    callback.onFailure(message(e, "Failed to mark notifications read"));
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(message(e, "Failed to load notifications"));
                });
    }

    public void deleteNotification(String notificationId, SimpleCallback callback) {
        if (notificationId == null || notificationId.isEmpty()) {
            if (callback != null) callback.onFailure("Invalid notification ID");
            return;
        }
        db.collection(Constants.COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(message(e, "Failed to delete notification"));
                });
    }

    private static String message(Exception e, String fallback) {
        return e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty()
                ? e.getMessage()
                : fallback;
    }
}
