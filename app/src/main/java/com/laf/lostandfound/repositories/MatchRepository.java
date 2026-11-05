package com.laf.lostandfound.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.laf.lostandfound.models.MatchRecord;
import com.laf.lostandfound.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the automatic lost/found suggestions produced after a report is
 * published. Document ids are deterministic so retries never duplicate a
 * suggestion.
 */
public class MatchRepository {

    private static MatchRepository instance;
    private final FirebaseFirestore db;

    public interface MatchCallback {
        void onSuccess(MatchRecord match);
        void onFailure(String errorMessage);
    }

    public interface MatchListCallback {
        void onSuccess(List<MatchRecord> matches);
        void onFailure(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    private MatchRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized MatchRepository getInstance() {
        if (instance == null) {
            instance = new MatchRepository();
        }
        return instance;
    }

    private DocumentReference matchRef(String itemId, String matchedItemId) {
        return db.collection(Constants.COLLECTION_MATCHES)
                .document(safe(itemId) + "_" + safe(matchedItemId));
    }

    public void saveMatch(@NonNull MatchRecord record, @Nullable MatchCallback callback) {
        if (isBlank(record.getItemId()) || isBlank(record.getMatchedItemId())) {
            if (callback != null) callback.onFailure("Missing match details");
            return;
        }
        if (record.getCreatedAt() <= 0) record.setCreatedAt(System.currentTimeMillis());
        if (isBlank(record.getStatus())) record.setStatus(Constants.MATCH_SUGGESTED);
        record.setId(safe(record.getItemId()) + "_" + safe(record.getMatchedItemId()));

        DocumentReference reference =
                matchRef(record.getItemId(), record.getMatchedItemId());
        // Document ids are deterministic, so retries can hit an existing doc.
        // Updating it would be rejected by firestore.rules; read first and treat
        // the existing suggestion as success instead.
        reference.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        MatchRecord existing = toMatch(snapshot);
                        if (callback != null) callback.onSuccess(existing != null ? existing : record);
                        return;
                    }
                    reference.set(record.toMap())
                            .addOnSuccessListener(aVoid -> {
                                if (callback != null) callback.onSuccess(record);
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) {
                                    callback.onFailure(message(e, "Failed to save match"));
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(message(e, "Failed to save match"));
                });
    }

    public ListenerRegistration listenToUserMatches(String userId,
                                                    @NonNull MatchListCallback callback) {
        if (isBlank(userId)) {
            callback.onFailure("Invalid user id");
            return null;
        }
        return db.collection(Constants.COLLECTION_MATCHES)
                .whereEqualTo("ownerId", userId)
                .whereEqualTo("status", Constants.MATCH_SUGGESTED)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Failed to load matches"));
                        return;
                    }
                    List<MatchRecord> matches = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            MatchRecord match = toMatch(doc);
                            if (match != null) matches.add(match);
                        }
                    }
                    callback.onSuccess(matches);
                });
    }

    public ListenerRegistration listenToItemMatches(String itemId,
                                                    @NonNull MatchListCallback callback) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        return listenToItemMatches(itemId, uid != null ? uid : "", callback);
    }

    public ListenerRegistration listenToItemMatches(String itemId, String ownerId,
                                                    @NonNull MatchListCallback callback) {
        if (isBlank(itemId) || isBlank(ownerId)) {
            callback.onFailure("Invalid match query");
            return null;
        }
        return db.collection(Constants.COLLECTION_MATCHES)
                .whereEqualTo("itemId", itemId)
                .whereEqualTo("ownerId", ownerId)
                .whereEqualTo("status", Constants.MATCH_SUGGESTED)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Failed to load matches"));
                        return;
                    }
                    List<MatchRecord> matches = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            MatchRecord match = toMatch(doc);
                            if (match != null) matches.add(match);
                        }
                    }
                    callback.onSuccess(matches);
                });
    }

    public void dismissMatch(String matchId, SimpleCallback callback) {
        if (isBlank(matchId)) {
            if (callback != null) callback.onFailure("Invalid match id");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Constants.MATCH_DISMISSED);
        db.collection(Constants.COLLECTION_MATCHES).document(matchId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(message(e, "Failed to dismiss match"));
                });
    }

    private static MatchRecord toMatch(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        MatchRecord record = doc.toObject(MatchRecord.class);
        if (record == null) return null;
        record.setId(doc.getId());
        if (isBlank(record.getStatus())) record.setStatus(Constants.MATCH_SUGGESTED);
        return record;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String message(Exception e, String fallback) {
        return e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty()
                ? e.getMessage()
                : fallback;
    }
}
