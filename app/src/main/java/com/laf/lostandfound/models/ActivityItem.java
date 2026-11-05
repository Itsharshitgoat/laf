package com.laf.lostandfound.models;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ActivityItem implements Serializable {

    private String id;
    private String userId;
    private String actorId;
    private String actionType; // "reported_lost", "reported_found", "matched", "returned", "closed"
    private String title;
    private String description;
    private String itemId;
    private long timestamp;

    public ActivityItem() {
    }

    public ActivityItem(String userId, String actionType, String title, String description, String itemId) {
        this.userId = userId;
        this.actorId = userId;
        this.actionType = actionType;
        this.title = title;
        this.description = description;
        this.itemId = itemId;
        this.timestamp = System.currentTimeMillis();
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("userId", safe(userId));
        map.put("actorId", safe(actorId));
        map.put("actionType", safe(actionType));
        map.put("title", safe(title));
        map.put("description", safe(description));
        map.put("itemId", safe(itemId));
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

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
