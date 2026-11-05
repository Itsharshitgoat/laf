package com.laf.lostandfound.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of the report wizard state. It is stored in
 * SharedPreferences so a user does not lose a selected photo / AI result when
 * the process is killed.
 */
public class ReportDraft implements Serializable {

    private int step = 1;
    private String type = "lost";
    private String imageUriString;
    private String imageUrl;
    private String imagePath;
    private String clientRequestId;

    private String title;
    private String category;
    private String primaryColor;
    private List<String> secondaryColors = new ArrayList<>();
    private String material;
    private String brand;
    private String description;
    private List<String> features = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();
    private double aiConfidence;
    private String aiModel;
    private String aiStatus;

    private String location;
    private double latitude;
    private double longitude;
    private long incidentAt;
    private boolean aiFailed;

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageUriString() {
        return imageUriString;
    }

    public void setImageUriString(String imageUriString) {
        this.imageUriString = imageUriString;
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

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
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

    public long getIncidentAt() {
        return incidentAt;
    }

    public void setIncidentAt(long incidentAt) {
        this.incidentAt = incidentAt;
    }

    public boolean isAiFailed() {
        return aiFailed;
    }

    public void setAiFailed(boolean aiFailed) {
        this.aiFailed = aiFailed;
    }
}
