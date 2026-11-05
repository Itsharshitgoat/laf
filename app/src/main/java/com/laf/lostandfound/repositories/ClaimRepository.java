package com.laf.lostandfound.repositories;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.laf.lostandfound.models.Claim;
import com.laf.lostandfound.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contact requests. A claim uses a deterministic id ({@code itemId_claimantId})
 * so a claimant cannot create duplicate requests by tapping repeatedly.
 */
public class ClaimRepository {

    private static ClaimRepository instance;
    private final FirebaseFirestore db;

    public interface ClaimCallback {
        void onSuccess(Claim claim);
        void onFailure(String errorMessage);
    }

    public interface ClaimListCallback {
        void onSuccess(List<Claim> claims);
        void onFailure(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    private ClaimRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized ClaimRepository getInstance() {
        if (instance == null) {
            instance = new ClaimRepository();
        }
        return instance;
    }

    public static String claimId(String itemId, String claimantId) {
        return safe(itemId) + "_" + safe(claimantId);
    }

    private DocumentReference claimRef(String itemId, String claimantId) {
        return db.collection(Constants.COLLECTION_CLAIMS).document(claimId(itemId, claimantId));
    }

    /**
     * Creates a contact request. If the deterministic document already exists
     * (e.g. the user tapped twice or retried after a timeout) the existing
     * request is returned instead of failing.
     */
    public void createClaim(@NonNull Claim claim, @NonNull ClaimCallback callback) {
        if (isBlank(claim.getItemId()) || isBlank(claim.getClaimantId())) {
            callback.onFailure("Missing claim details");
            return;
        }
        if (claim.getClaimantId().equals(claim.getOwnerId())) {
            callback.onFailure("You cannot claim your own report");
            return;
        }

        long now = System.currentTimeMillis();
        if (claim.getCreatedAt() <= 0) claim.setCreatedAt(now);
        claim.setUpdatedAt(now);
        if (isBlank(claim.getStatus())) claim.setStatus(Constants.CLAIM_PENDING);
        claim.setId(claimId(claim.getItemId(), claim.getClaimantId()));

        DocumentReference reference = claimRef(claim.getItemId(), claim.getClaimantId());
        reference.set(claim.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess(claim))
                .addOnFailureListener(e -> reference.get()
                        .addOnSuccessListener(snapshot -> {
                            Claim existing = toClaim(snapshot);
                            if (existing != null) {
                                callback.onSuccess(existing);
                            } else {
                                callback.onFailure(message(e, "Failed to send contact request"));
                            }
                        })
                        .addOnFailureListener(readError ->
                                callback.onFailure(message(e, "Failed to send contact request"))));
    }

    public void getClaim(String itemId, String claimantId, @NonNull ClaimCallback callback) {
        if (isBlank(itemId) || isBlank(claimantId)) {
            callback.onFailure("Missing claim details");
            return;
        }
        claimRef(itemId, claimantId).get()
                .addOnSuccessListener(snapshot -> {
                    Claim claim = toClaim(snapshot);
                    if (claim != null) {
                        callback.onSuccess(claim);
                    } else {
                        callback.onFailure("No request found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to load request")));
    }

    public ListenerRegistration listenToClaim(String itemId, String claimantId,
                                              @NonNull ClaimCallback callback) {
        if (isBlank(itemId) || isBlank(claimantId)) {
            callback.onFailure("Missing claim details");
            return null;
        }
        return claimRef(itemId, claimantId).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                callback.onFailure(message(error, "Failed to watch request"));
                return;
            }
            Claim claim = toClaim(snapshot);
            if (claim != null) {
                callback.onSuccess(claim);
            } else {
                callback.onFailure("No request found");
            }
        });
    }

    /** Owner-side list of requests for one of their reports. */
    public ListenerRegistration listenToItemClaims(String itemId, String ownerId,
                                                   @NonNull ClaimListCallback callback) {
        if (isBlank(itemId) || isBlank(ownerId)) {
            callback.onFailure("Missing claim details");
            return null;
        }
        return db.collection(Constants.COLLECTION_CLAIMS)
                .whereEqualTo("itemId", itemId)
                .whereEqualTo("ownerId", ownerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Failed to load contact requests"));
                        return;
                    }
                    List<Claim> claims = new ArrayList<>();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Claim claim = toClaim(doc);
                            if (claim != null) claims.add(claim);
                        }
                    }
                    callback.onSuccess(claims);
                });
    }

    public void updateClaimStatus(String claimId, String status, String ownerContact,
                                  @NonNull ClaimCallback callback) {
        if (isBlank(claimId) || !isValidClaimStatus(status)) {
            callback.onFailure("Invalid request");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("ownerContact", ownerContact == null ? "" : ownerContact.trim());
        updates.put("updatedAt", System.currentTimeMillis());
        if (Constants.CLAIM_APPROVED.equalsIgnoreCase(status)
                || Constants.CLAIM_REJECTED.equalsIgnoreCase(status)) {
            updates.put("resolvedAt", System.currentTimeMillis());
        }

        DocumentReference reference = db.collection(Constants.COLLECTION_CLAIMS).document(claimId);
        reference.update(updates)
                .addOnSuccessListener(aVoid -> reference.get()
                        .addOnSuccessListener(snapshot -> {
                            Claim claim = toClaim(snapshot);
                            if (claim != null) callback.onSuccess(claim);
                            else callback.onFailure("Request updated but could not be loaded");
                        })
                        .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to load request"))))
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to update request")));
    }

    public void deleteClaim(String claimId, SimpleCallback callback) {
        if (isBlank(claimId)) {
            if (callback != null) callback.onFailure("Invalid request id");
            return;
        }
        db.collection(Constants.COLLECTION_CLAIMS).document(claimId).delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(message(e, "Failed to delete request"));
                });
    }

    private static boolean isValidClaimStatus(String status) {
        return Constants.CLAIM_PENDING.equalsIgnoreCase(status)
                || Constants.CLAIM_APPROVED.equalsIgnoreCase(status)
                || Constants.CLAIM_REJECTED.equalsIgnoreCase(status);
    }

    private static Claim toClaim(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Claim claim = doc.toObject(Claim.class);
        if (claim == null) return null;
        claim.setId(doc.getId());
        if (isBlank(claim.getStatus())) claim.setStatus(Constants.CLAIM_PENDING);
        return claim;
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
