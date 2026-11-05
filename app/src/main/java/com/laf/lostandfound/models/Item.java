package com.laf.lostandfound.models;

import com.google.firebase.firestore.Exclude;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A lost/found report.
 *
 * <p>The model contains the full report schema used by the wizard and the
 * Firebase repositories. A handful of legacy accessors are kept so older
 * documents/adapters keep working while the data set is migrated.</p>
 */
public class Item implements Serializable {

    private String id;
    private String title;
    private String description;
    private String category;
    private String type; // "lost" or "found"

    private String imageUrl;
    private String imagePath;
    private String storageProvider;

    private String location;
    private double latitude;
    private double longitude;

    private String primaryColor;
    private List<String> secondaryColors = new ArrayList<>();
    private String material;
    private String brand;
    private List<String> features = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();
    private String notes;

    private double aiConfidence;
    private String aiModel;
    private String aiStatus; // "completed", "failed", "manual"

    private String userId;
    private String userName;
    private String userPhone; // legacy field: new reports never persist a phone number
    private String userPhotoUrl;
    private boolean userVerified;

    private String status; // "active", "claimed", "returned", "closed"
    private long incidentAt;     // when the item was lost/found
    private long reportCreatedAt; // when the report was published
    private long updatedAt;
    private long returnedAt;
    private long closedAt;

    private String searchText;
    private List<String> searchTokens = new ArrayList<>();
    private String clientRequestId;

    public Item() {
    }

    /**
     * Explicit map used for every write. Keeping this separate from Firestore's
     * automatic bean mapping prevents accidentally persisting the document id
     * or legacy-only fields.
     */
    public Map<String, Object> toMap() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("title", safe(title));
        map.put("description", safe(description));
        map.put("category", safe(category));
        map.put("type", safe(type));
        map.put("imageUrl", safe(imageUrl));
        map.put("imagePath", safe(imagePath));
        map.put("storageProvider", safe(storageProvider));
        map.put("location", safe(location));
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("primaryColor", safe(primaryColor));
        map.put("secondaryColors", listOrEmpty(secondaryColors));
        map.put("material", safe(material));
        map.put("brand", safe(brand));
        map.put("features", listOrEmpty(features));
        map.put("tags", listOrEmpty(tags));
        map.put("keywords", listOrEmpty(keywords));
        map.put("notes", safe(notes));
        map.put("aiConfidence", aiConfidence);
        map.put("aiModel", safe(aiModel));
        map.put("aiStatus", safe(aiStatus));
        map.put("userId", safe(userId));
        map.put("userName", safe(userName));
        map.put("userPhotoUrl", safe(userPhotoUrl));
        map.put("userVerified", userVerified);
        map.put("status", safe(status));
        map.put("incidentAt", incidentAt);
        map.put("reportCreatedAt", reportCreatedAt);
        map.put("updatedAt", updatedAt);
        map.put("returnedAt", returnedAt);
        map.put("closedAt", closedAt);
        map.put("searchText", safe(searchText));
        map.put("searchTokens", listOrEmpty(searchTokens));
        map.put("clientRequestId", safe(clientRequestId));
        return map;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static List<String> listOrEmpty(List<String> value) {
        return value == null ? new ArrayList<>() : value;
    }

    @Exclude
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Exclude
    public String getItemId() {
        return id;
    }

    public void setItemId(String id) {
        this.id = id;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    /** Legacy alias for {@link #getPrimaryColor()}. */
    @Exclude
    public String getColor() {
        return primaryColor;
    }

    /** Legacy alias for {@link #setPrimaryColor(String)}. */
    public void setColor(String color) {
        this.primaryColor = color;
    }

    public List<String> getSecondaryColors() {
        return secondaryColors;
    }

    public void setSecondaryColors(List<String> secondaryColors) {
        this.secondaryColors = secondaryColors;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    /**
     * Legacy comma separated feature accessor.
     */
    @Exclude
    public String getDetectedFeatures() {
        return join(features);
    }

    /**
     * Accepts a comma separated string so documents written by older clients
     * still deserialize into {@link #features}.
     */
    public void setDetectedFeatures(String detectedFeatures) {
        this.features = split(detectedFeatures);
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public double getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(double aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public String getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus) {
        this.aiStatus = aiStatus;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public void setUserPhone(String userPhone) {
        this.userPhone = userPhone;
    }

    public String getUserPhotoUrl() {
        return userPhotoUrl;
    }

    public void setUserPhotoUrl(String userPhotoUrl) {
        this.userPhotoUrl = userPhotoUrl;
    }

    public boolean isUserVerified() {
        return userVerified;
    }

    public void setUserVerified(boolean userVerified) {
        this.userVerified = userVerified;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getIncidentAt() {
        return incidentAt;
    }

    public void setIncidentAt(long incidentAt) {
        this.incidentAt = incidentAt;
    }

    public long getReportCreatedAt() {
        return reportCreatedAt;
    }

    public void setReportCreatedAt(long reportCreatedAt) {
        this.reportCreatedAt = reportCreatedAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(long returnedAt) {
        this.returnedAt = returnedAt;
    }

    public long getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(long closedAt) {
        this.closedAt = closedAt;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public List<String> getSearchTokens() {
        return searchTokens;
    }

    public void setSearchTokens(List<String> searchTokens) {
        this.searchTokens = searchTokens;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    /** Legacy alias: event time. */
    @Exclude
    public long getTimestamp() {
        return incidentAt;
    }

    /** Legacy alias: event time. */
    public void setTimestamp(long timestamp) {
        this.incidentAt = timestamp;
    }

    /** Legacy alias: report publish time. */
    @Exclude
    public long getCreatedAt() {
        return reportCreatedAt;
    }

    /** Legacy alias: report publish time. */
    public void setCreatedAt(long createdAt) {
        this.reportCreatedAt = createdAt;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(", ");
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private static List<String> split(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return result;
        for (String part : value.split(",")) {
            String clean = part == null ? "" : part.trim();
            if (!clean.isEmpty()) result.add(clean);
        }
        return result;
    }
}
