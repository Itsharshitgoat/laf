package com.laf.lostandfound.models;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Persisted suggestion linking one of the signed-in user's reports to an
 * opposite-type item (lost -> found or found -> lost).
 */
public class MatchRecord implements Serializable {

    private String id;
    private String itemId;
    private String matchedItemId;
    private String ownerId;
    private String matchedOwnerId;
    private String itemTitle;
    private String matchedItemTitle;
    private int score;
    private String status; // suggested / dismissed
    private long createdAt;

    public MatchRecord() {
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("itemId", safe(itemId));
        map.put("matchedItemId", safe(matchedItemId));
        map.put("ownerId", safe(ownerId));
        map.put("matchedOwnerId", safe(matchedOwnerId));
        map.put("itemTitle", safe(itemTitle));
        map.put("matchedItemTitle", safe(matchedItemTitle));
        map.put("score", score);
        map.put("status", safe(status));
        map.put("createdAt", createdAt);
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

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getMatchedItemId() {
        return matchedItemId;
    }

    public void setMatchedItemId(String matchedItemId) {
        this.matchedItemId = matchedItemId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getMatchedOwnerId() {
        return matchedOwnerId;
    }

    public void setMatchedOwnerId(String matchedOwnerId) {
        this.matchedOwnerId = matchedOwnerId;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public void setItemTitle(String itemTitle) {
        this.itemTitle = itemTitle;
    }

    public String getMatchedItemTitle() {
        return matchedItemTitle;
    }

    public void setMatchedItemTitle(String matchedItemTitle) {
        this.matchedItemTitle = matchedItemTitle;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
