package com.laf.lostandfound.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.laf.lostandfound.models.ActivityItem;
import com.laf.lostandfound.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class ActivityRepository {

    private static ActivityRepository instance;
    private final FirebaseFirestore db;

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface ActivityListCallback {
        void onSuccess(List<ActivityItem> activities);
        void onFailure(String errorMessage);
    }

    private ActivityRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized ActivityRepository getInstance() {
        if (instance == null) {
            instance = new ActivityRepository();
        }
        return instance;
    }

    public void logActivity(@NonNull ActivityItem activity, @Nullable SimpleCallback callback) {
        DocumentReference docRef = db.collection(Constants.COLLECTION_ACTIVITIES).document();
        activity.setId(docRef.getId());
        if (activity.getTimestamp() <= 0) {
            activity.setTimestamp(System.currentTimeMillis());
        }
        if (activity.getActorId() == null || activity.getActorId().trim().isEmpty()) {
            activity.setActorId(activity.getUserId());
        }

        docRef.set(activity.toMap())
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(message(e, "Failed to record activity"));
                    }
                });
    }

    public ListenerRegistration listenToUserActivities(String userId,
                                                       @NonNull ActivityListCallback callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return null;
        }

        return db.collection(Constants.COLLECTION_ACTIVITIES)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(25)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Error listening to activities"));
                        return;
                    }
                    if (querySnapshot == null) return;
                    List<ActivityItem> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        ActivityItem item = doc.toObject(ActivityItem.class);
                        if (item == null) continue;
                        item.setId(doc.getId());
                        list.add(item);
                    }
                    callback.onSuccess(list);
                });
    }

    public void deleteActivity(String activityId, SimpleCallback callback) {
        if (activityId == null || activityId.isEmpty()) {
            if (callback != null) callback.onFailure("Invalid activity ID");
            return;
        }
        db.collection(Constants.COLLECTION_ACTIVITIES)
                .document(activityId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(message(e, "Failed to delete activity"));
                });
    }

    private static String message(Exception e, String fallback) {
        return e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty()
                ? e.getMessage()
                : fallback;
    }
}
