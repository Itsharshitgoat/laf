package com.laf.lostandfound.repositories;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.laf.lostandfound.App;
import com.laf.lostandfound.utils.Constants;
import com.laf.lostandfound.utils.ImageValidationUtils;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Firebase Storage uploads. Objects live under the signed-in user's UID so the
 * storage rules can enforce owner-only writes.
 */
public class StorageRepository {

    private static final String TAG = "StorageRepository";
    private static StorageRepository instance;

    private final FirebaseStorage storage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ProgressListener {
        void onProgress(int percent);
    }

    public interface UploadCallback {
        void onSuccess(UploadResult result);
        void onFailure(String errorMessage);
    }

    public interface DeleteCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface UrlCallback {
        void onSuccess(String url);
        void onFailure(String errorMessage);
    }

    public static class UploadResult {
        private final String downloadUrl;
        private final String storagePath;
        private final long sizeBytes;

        public UploadResult(String downloadUrl, String storagePath, long sizeBytes) {
            this.downloadUrl = downloadUrl;
            this.storagePath = storagePath;
            this.sizeBytes = sizeBytes;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }
    }

    private StorageRepository() {
        this.storage = FirebaseStorage.getInstance();
    }

    public static synchronized StorageRepository getInstance() {
        if (instance == null) {
            instance = new StorageRepository();
        }
        return instance;
    }

    public void uploadItemImage(Uri imageUri, UploadCallback callback) {
        uploadItemImage(imageUri, null, callback);
    }

    public void uploadItemImage(Uri imageUri, ProgressListener progressListener, UploadCallback callback) {
        compressAndUpload(imageUri, Constants.STORAGE_ITEMS, Constants.MAX_IMAGE_BYTES,
                Constants.MAX_IMAGE_DIMENSION, Constants.JPEG_QUALITY, progressListener, callback);
    }

    public void uploadAvatar(Uri imageUri, UploadCallback callback) {
        uploadAvatar(imageUri, null, callback);
    }

    public void uploadAvatar(Uri imageUri, ProgressListener progressListener, UploadCallback callback) {
        compressAndUpload(imageUri, Constants.STORAGE_PROFILE_IMAGES, Constants.MAX_AVATAR_BYTES,
                Constants.MAX_AVATAR_DIMENSION, Constants.JPEG_QUALITY, progressListener, callback);
    }

    private void compressAndUpload(Uri imageUri, String folder, long maxBytes,
                                   int maxDimension, int quality,
                                   ProgressListener progressListener,
                                   UploadCallback callback) {
        if (callback == null) return;
        FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onFailure("Please sign in before uploading images");
            return;
        }
        if (imageUri == null) {
            callback.onFailure("Invalid image URI");
            return;
        }

        String uid = user.getUid();
        Context context = App.getInstance();

        executor.execute(() -> {
            try {
                ImageValidationUtils.Result validation =
                        ImageValidationUtils.validate(context, imageUri, maxBytes);
                if (!validation.isValid()) {
                    postFailure(callback, validation.getError());
                    return;
                }

                byte[] bytes = ImageValidationUtils.compress(context, imageUri, maxDimension, quality);
                if (bytes == null || bytes.length == 0) {
                    postFailure(callback, "Unable to read the selected image");
                    return;
                }

                String path = folder + "/" + uid + "/" + UUID.randomUUID() + ".jpg";
                StorageReference reference = storage.getReference().child(path);
                // storage.rules only accepts image/* content, and putBytes() does
                // not infer a MIME type on its own.
                StorageMetadata metadata = new StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build();
                UploadTask task = reference.putBytes(bytes, metadata);
                attachUploadListeners(task, reference, path, bytes.length, progressListener, callback);
            } catch (Exception e) {
                Log.e(TAG, "Upload preparation failed", e);
                postFailure(callback, "Upload failed: " + safeMessage(e));
            }
        });
    }

    private void attachUploadListeners(UploadTask task, StorageReference reference, String path,
                                       long sizeBytes, ProgressListener progressListener,
                                       UploadCallback callback) {
        task.addOnProgressListener(snapshot -> {
            if (progressListener == null || snapshot == null) return;
            long total = snapshot.getTotalByteCount();
            if (total <= 0) return;
            int percent = (int) Math.max(0, Math.min(100,
                    (snapshot.getBytesTransferred() * 100L) / total));
            mainHandler.post(() -> progressListener.onProgress(percent));
        }).addOnSuccessListener(snapshot ->
                reference.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            if (uri == null) {
                                postFailure(callback, "Upload finished but no download URL was returned");
                                return;
                            }
                            UploadResult result = new UploadResult(uri.toString(), path, sizeBytes);
                            mainHandler.post(() -> callback.onSuccess(result));
                        })
                        .addOnFailureListener(e ->
                                postFailure(callback, "Unable to resolve image URL: " + safeMessage(e)))
        ).addOnFailureListener(e -> {
            Log.e(TAG, "Firebase Storage upload failed", e);
            postFailure(callback, "Upload failed: " + safeMessage(e));
        });
    }

    public void deleteByPath(String storagePath, DeleteCallback callback) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            if (callback != null) callback.onFailure("Missing storage path");
            return;
        }
        storage.getReference().child(storagePath.trim()).delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(safeMessage(e));
                });
    }

    /**
     * Best-effort cleanup for reports that were created before imagePath was
     * persisted.
     */
    public void deleteByDownloadUrl(String downloadUrl, DeleteCallback callback) {
        String path = pathFromDownloadUrl(downloadUrl);
        if (path == null) {
            if (callback != null) callback.onFailure("Unable to resolve storage path");
            return;
        }
        deleteByPath(path, callback);
    }

    public void resolveDownloadUrl(String storagePath, UrlCallback callback) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            callback.onFailure("Missing storage path");
            return;
        }
        storage.getReference().child(storagePath.trim()).getDownloadUrl()
                .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                .addOnFailureListener(e -> callback.onFailure(safeMessage(e)));
    }

    private static String pathFromDownloadUrl(String downloadUrl) {
        if (downloadUrl == null) return null;
        int marker = downloadUrl.indexOf("/o/");
        if (marker < 0) return null;
        String encoded = downloadUrl.substring(marker + 3);
        int query = encoded.indexOf('?');
        if (query >= 0) encoded = encoded.substring(0, query);
        return Uri.decode(encoded);
    }

    private void postFailure(UploadCallback callback, String message) {
        mainHandler.post(() -> callback.onFailure(message));
    }

    private static String safeMessage(Exception e) {
        return e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty()
                ? e.getMessage()
                : "Please check your connection and try again";
    }
}
