package com.laf.lostandfound.models;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class User implements Serializable {

    private String uid;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String bio;
    private String photoUrl;
    private String avatarStoragePath;
    private boolean isVerified;
    private long createdAt;
    private long updatedAt;

    public User() {
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("uid", safe(uid));
        map.put("username", safe(username));
        map.put("displayName", safe(displayName));
        map.put("email", safe(email));
        map.put("phone", safe(phone));
        map.put("bio", safe(bio));
        map.put("photoUrl", safe(photoUrl));
        map.put("avatarStoragePath", safe(avatarStoragePath));
        map.put("isVerified", isVerified);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Exclude
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    /** Legacy alias for {@link #getUid()}. */
    @Exclude
    public String getUserId() {
        return uid;
    }

    /** Legacy alias for {@link #setUid(String)}. */
    public void setUserId(String userId) {
        this.uid = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Legacy alias for {@link #getDisplayName()}. */
    @Exclude
    public String getName() {
        return displayName;
    }

    /** Legacy alias for {@link #setDisplayName(String)}. */
    public void setName(String name) {
        this.displayName = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getAvatarStoragePath() {
        return avatarStoragePath;
    }

    public void setAvatarStoragePath(String avatarStoragePath) {
        this.avatarStoragePath = avatarStoragePath;
    }

    /**
     * Firestore would otherwise derive the field name "verified" from this JavaBean
     * accessor pair, which does not match the "isVerified" field written by {@link #toMap()}.
     */
    @PropertyName("isVerified")
    public boolean isVerified() {
        return isVerified;
    }

    @PropertyName("isVerified")
    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
