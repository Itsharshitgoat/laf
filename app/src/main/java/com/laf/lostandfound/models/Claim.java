package com.laf.lostandfound.models;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A contact request ("this is my item") created by a visitor.
 *
 * <p>Contact details are only stored on the claim document. The item document
 * never exposes a phone number, and Firestore rules restrict claim reads to
 * the claimant and the item owner.</p>
 */
public class Claim implements Serializable {

    private String id;
    private String itemId;
    private String itemTitle;
    private String ownerId;
    private String claimantId;
    private String claimantName;
    private String claimantContact;
    private String message;
    private String status; // pending / approved / rejected
    private String ownerContact; // written by the owner on approval
    private long createdAt;
    private long updatedAt;
    private long resolvedAt;

    public Claim() {
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("itemId", safe(itemId));
        map.put("itemTitle", safe(itemTitle));
        map.put("ownerId", safe(ownerId));
        map.put("claimantId", safe(claimantId));
        map.put("claimantName", safe(claimantName));
        map.put("claimantContact", safe(claimantContact));
        map.put("message", safe(message));
        map.put("status", safe(status));
        map.put("ownerContact", safe(ownerContact));
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        map.put("resolvedAt", resolvedAt);
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

    public String getItemTitle() {
        return itemTitle;
    }

    public void setItemTitle(String itemTitle) {
        this.itemTitle = itemTitle;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getClaimantId() {
        return claimantId;
    }

    public void setClaimantId(String claimantId) {
        this.claimantId = claimantId;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public void setClaimantName(String claimantName) {
        this.claimantName = claimantName;
    }

    public String getClaimantContact() {
        return claimantContact;
    }

    public void setClaimantContact(String claimantContact) {
        this.claimantContact = claimantContact;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOwnerContact() {
        return ownerContact;
    }

    public void setOwnerContact(String ownerContact) {
        this.ownerContact = ownerContact;
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

    public long getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(long resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
