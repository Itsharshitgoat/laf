package com.laf.lostandfound.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Moderation report submitted by a user for an abusive, spammy, or fraudulent listing.
 */
public class ReportAbuse {

    private String id;
    private String reporterId;
    private String targetType; // "item", "user", "claim"
    private String targetId;
    private String reason;
    private String details;
    private long createdAt;
    private String status; // "pending", "reviewed", "dismissed"

    public ReportAbuse() {
        this.createdAt = System.currentTimeMillis();
        this.status = "pending";
    }

    public ReportAbuse(String reporterId, String targetType, String targetId,
                       String reason, String details) {
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.details = details;
        this.createdAt = System.currentTimeMillis();
        this.status = "pending";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("reporterId", reporterId == null ? "" : reporterId);
        map.put("targetType", targetType == null ? "item" : targetType);
        map.put("targetId", targetId == null ? "" : targetId);
        map.put("reason", reason == null ? "" : reason);
        map.put("details", details == null ? "" : details);
        map.put("createdAt", createdAt);
        map.put("status", status == null ? "pending" : status);
        return map;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
