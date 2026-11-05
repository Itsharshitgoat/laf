package com.laf.lostandfound.repositories;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.laf.lostandfound.models.ReportAbuse;

import java.util.UUID;

/**
 * Handles abuse, fraud, and inappropriate content reports submitted by users.
 */
public class ReportRepository {

    private static final String COLLECTION_REPORTS = "reports";
    private static ReportRepository instance;
    private final FirebaseFirestore db;

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    private ReportRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized ReportRepository getInstance() {
        if (instance == null) {
            instance = new ReportRepository();
        }
        return instance;
    }

    public void submitReport(@NonNull ReportAbuse report, @Nullable SimpleCallback callback) {
        if (report.getReporterId() == null || report.getReporterId().trim().isEmpty()) {
            if (callback != null) callback.onFailure("User must be signed in to submit a report");
            return;
        }

        String reportId = UUID.randomUUID().toString();
        report.setId(reportId);

        DocumentReference docRef = db.collection(COLLECTION_REPORTS).document(reportId);
        docRef.set(report.toMap())
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) {
                        callback.onFailure(e != null && e.getMessage() != null
                                ? e.getMessage()
                                : "Failed to submit moderation report");
                    }
                });
    }
}
