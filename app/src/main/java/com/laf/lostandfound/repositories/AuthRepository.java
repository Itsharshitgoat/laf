package com.laf.lostandfound.repositories;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.laf.lostandfound.models.User;
import com.laf.lostandfound.utils.Constants;

import java.util.HashMap;
import java.util.Map;

public class AuthRepository {

    private static AuthRepository instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface UserCallback {
        void onSuccess(User user);
        void onFailure(String errorMessage);
    }

    public interface AuthStateListener {
        void onSignedIn(FirebaseUser user);
        void onSignedOut();
    }

    private AuthRepository() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized AuthRepository getInstance() {
        if (instance == null) {
            instance = new AuthRepository();
        }
        return instance;
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : "";
    }

    public void login(String email, String password, @NonNull AuthCallback callback) {
        auth.signInWithEmailAndPassword(clean(email), password)
                .addOnSuccessListener(authResult -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Sign in failed")));
    }

    public void register(String email, String password, @NonNull AuthCallback callback) {
        auth.createUserWithEmailAndPassword(clean(email), password)
                .addOnSuccessListener(authResult -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Registration failed")));
    }

    public void sendPasswordResetEmail(String email, @NonNull AuthCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            callback.onFailure("Please enter an email address");
            return;
        }
        auth.sendPasswordResetEmail(clean(email))
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Password reset failed")));
    }

    public void sendEmailVerification(@NonNull AuthCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onFailure("No active user session");
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Failed to send verification email")));
    }

    public boolean isEmailVerified() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }

    public void reloadCurrentUser(@NonNull AuthCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onFailure("No active user session");
            return;
        }
        user.reload()
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Failed to refresh account")));
    }

    public void createUserProfile(@NonNull User user, @NonNull AuthCallback callback) {
        String uid = getCurrentUserId();
        if (uid.isEmpty()) {
            callback.onFailure("No active user session");
            return;
        }
        user.setUid(uid);
        long now = System.currentTimeMillis();
        if (user.getCreatedAt() <= 0) user.setCreatedAt(now);
        user.setUpdatedAt(now);
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            String email = user.getEmail();
            if (email != null && email.contains("@")) {
                user.setUsername(email.substring(0, email.indexOf('@')));
            }
        }
        if (user.getUsername() == null) user.setUsername("");

        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(user.toMap())
                .addOnSuccessListener(aVoid -> {
                    updateAuthDisplayName(user.getDisplayName());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Failed to create profile")));
    }

    public void updateUserProfile(@NonNull User user, @NonNull AuthCallback callback) {
        String uid = user.getUid() == null ? getCurrentUserId() : user.getUid();
        if (uid.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return;
        }
        user.setUid(uid);
        user.setUpdatedAt(System.currentTimeMillis());
        Map<String, Object> updates = new HashMap<>();
        // Rules require request.resource.data.uid == userId for every user write.
        updates.put("uid", uid);
        updates.put("displayName", user.getDisplayName() == null ? "" : user.getDisplayName());
        updates.put("username", user.getUsername() == null ? "" : user.getUsername());
        updates.put("phone", user.getPhone() == null ? "" : user.getPhone());
        updates.put("bio", user.getBio() == null ? "" : user.getBio());
        updates.put("photoUrl", user.getPhotoUrl() == null ? "" : user.getPhotoUrl());
        updates.put("avatarStoragePath", user.getAvatarStoragePath() == null ? "" : user.getAvatarStoragePath());
        updates.put("updatedAt", user.getUpdatedAt());

        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    updateAuthDisplayName(user.getDisplayName());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Failed to update profile")));
    }

    /** Used by the profile screen for small, focused updates (name, bio, avatar). */
    public void updateUserFields(String uid, Map<String, Object> updates, @NonNull AuthCallback callback) {
        if (uid == null || uid.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return;
        }
        Map<String, Object> safe = updates == null ? new HashMap<>() : new HashMap<>(updates);
        // Rules require request.resource.data.uid == userId for every user write.
        safe.put("uid", uid);
        safe.put("updatedAt", System.currentTimeMillis());
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(safe, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Object name = safe.get("displayName");
                    if (name instanceof String) updateAuthDisplayName((String) name);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Failed to update profile")));
    }

    public void getUserProfile(String uid, @NonNull UserCallback callback) {
        if (uid == null || uid.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return;
        }

        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            user.setUid(documentSnapshot.getId());
                            if (auth.getCurrentUser() != null && auth.getCurrentUser().isEmailVerified() && !user.isVerified()) {
                                user.setVerified(true);
                                Map<String, Object> syncMap = new HashMap<>();
                                syncMap.put("isVerified", true);
                                syncMap.put("uid", user.getUid());
                                db.collection(Constants.COLLECTION_USERS).document(user.getUid()).set(syncMap, SetOptions.merge());
                            }
                            callback.onSuccess(user);
                        } else {
                            callback.onFailure("User profile is empty");
                        }
                    } else {
                        callback.onFailure("User profile not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(friendlyError(e, "Failed to load profile")));
    }

    public ListenerRegistration listenToUserProfile(String uid, @NonNull UserCallback callback) {
        if (uid == null || uid.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return null;
        }
        return db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onFailure(friendlyError(error, "Failed to watch profile"));
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        if (user != null) {
                            user.setUid(snapshot.getId());
                            if (auth.getCurrentUser() != null && auth.getCurrentUser().isEmailVerified() && !user.isVerified()) {
                                user.setVerified(true);
                                Map<String, Object> syncMap = new HashMap<>();
                                syncMap.put("isVerified", true);
                                syncMap.put("uid", user.getUid());
                                db.collection(Constants.COLLECTION_USERS).document(user.getUid()).set(syncMap, SetOptions.merge());
                            }
                            callback.onSuccess(user);
                        }
                    }
                });
    }

    public void addAuthStateListener(@NonNull AuthStateListener listener) {
        auth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                listener.onSignedIn(user);
            } else {
                listener.onSignedOut();
            }
        });
    }

    public void signOut() {
        String uid = getCurrentUserId();
        if (!uid.isEmpty()) {
            Map<String, Object> tokenClear = new HashMap<>();
            tokenClear.put("fcmToken", "");
            tokenClear.put("uid", uid);
            db.collection(Constants.COLLECTION_USERS).document(uid).set(tokenClear, SetOptions.merge());
        }
        auth.signOut();
    }

    private void updateAuthDisplayName(String displayName) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || displayName == null || displayName.trim().isEmpty()) return;
        if (displayName.equals(user.getDisplayName())) return;
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build();
        user.updateProfile(request);
    }

    private static String clean(String email) {
        return email == null ? "" : email.trim();
    }

    public void updateFcmToken(String token) {
        String uid = getCurrentUserId();
        if (uid.isEmpty() || token == null || token.trim().isEmpty()) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("fcmToken", token);
        updates.put("uid", uid);
        updates.put("updatedAt", System.currentTimeMillis());
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge());
    }

    public void syncFcmToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(this::updateFcmToken);
        } catch (Exception ignored) {
        }
    }

    public static String friendlyError(Exception e, String fallback) {
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if (code != null) {
                switch (code) {
                    case "ERROR_INVALID_EMAIL":
                        return "That email address is not valid";
                    case "ERROR_WRONG_PASSWORD":
                    case "ERROR_INVALID_CREDENTIAL":
                        return "Email or password is incorrect";
                    case "ERROR_USER_NOT_FOUND":
                        return "No account exists for that email";
                    case "ERROR_EMAIL_ALREADY_IN_USE":
                        return "An account already exists for that email";
                    case "ERROR_WEAK_PASSWORD":
                        return "Please choose a stronger password";
                    case "ERROR_TOO_MANY_REQUESTS":
                        return "Too many attempts. Please try again later";
                    case "ERROR_NETWORK_REQUEST_FAILED":
                        return "Network error. Check your connection and try again";
                    case "ERROR_USER_DISABLED":
                        return "This account has been disabled";
                    default:
                        break;
                }
            }
        }
        String message = e != null ? e.getMessage() : null;
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}
