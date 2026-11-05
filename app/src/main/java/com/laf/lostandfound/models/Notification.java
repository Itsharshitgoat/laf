package com.laf.lostandfound.models;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Notification implements Serializable {

    private String id;
    private String userId;   // recipient
    private String actorId;  // signed-in user who triggered the notification
    private String title;
    private String message;
    private String itemId;
    private String relatedItemId; // the recipient's own item, used to verify match notifications
    private String type; // "match", "claim", "status_change", "system", "report"
    private boolean read;
    private long timestamp;

    public Notification() {
    }

    public Notification(String userId, String title, String message, String itemId, String type) {
        this(userId, userId, title, message, itemId, type);
    }

    public Notification(String userId, String actorId, String title, String message,
                        String itemId, String type) {
        this.userId = userId;
        this.actorId = actorId;
        this.title = title;
        this.message = message;
        this.itemId = itemId;
        this.type = type;
        this.read = false;
        this.timestamp = System.currentTimeMillis();
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("userId", safe(userId));
        map.put("actorId", safe(actorId));
        map.put("title", safe(title));
        map.put("message", safe(message));
        map.put("itemId", safe(itemId));
        map.put("relatedItemId", safe(relatedItemId));
        map.put("type", safe(type));
        map.put("read", read);
        map.put("timestamp", timestamp);
        return map;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Exclude
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getRelatedItemId() {
        return relatedItemId;
    }

    public void setRelatedItemId(String relatedItemId) {
        this.relatedItemId = relatedItemId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
