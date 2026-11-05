package com.laf.lostandfound.utils;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Calls the Supabase Edge Function which talks to the vision model. The model
 * API key lives in the function environment and is never bundled in the APK.
 */
public final class ImageAnalyzer {

    private static final String TAG = "ImageAnalyzer";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageAnalyzer() {
    }

    public interface AnalysisCallback {
        void onSuccess(AnalysisResult result);
        void onFailure(String errorMessage);
    }

    public static class AnalysisResult implements Serializable {
        private String itemType;
        private String primaryColor;
        private int colorHex;
        private List<String> detectedFeatures = new ArrayList<>();
        private String suggestedCategory;
        private List<String> secondaryColors = new ArrayList<>();
        private String material;
        private String brand;
        private List<String> tags = new ArrayList<>();
        private List<String> keywords = new ArrayList<>();
        private double confidence;
        private String model;

        public AnalysisResult() {
        }

        public String getItemType() {
            return itemType;
        }

        public void setItemType(String itemType) {
            this.itemType = itemType;
        }

        public String getPrimaryColor() {
            return primaryColor;
        }

        public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
        }

        public int getColorHex() {
            return colorHex;
        }

        public void setColorHex(int colorHex) {
            this.colorHex = colorHex;
        }

        public String getDetectedFeatures() {
            return join(detectedFeatures);
        }

        public List<String> getFeatureList() {
            return detectedFeatures;
        }

        public void setDetectedFeatures(List<String> detectedFeatures) {
            this.detectedFeatures = detectedFeatures == null ? new ArrayList<>() : detectedFeatures;
        }

        public String getSuggestedCategory() {
            return suggestedCategory;
        }

        public void setSuggestedCategory(String suggestedCategory) {
            this.suggestedCategory = suggestedCategory;
        }

        public List<String> getSecondaryColors() {
            return secondaryColors;
        }

        public void setSecondaryColors(List<String> secondaryColors) {
            this.secondaryColors = secondaryColors == null ? new ArrayList<>() : secondaryColors;
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

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags == null ? new ArrayList<>() : tags;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords == null ? new ArrayList<>() : keywords;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
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
    }

    /**
     * Reads, compresses and analyzes the supplied image. All failures are
     * reported to {@link AnalysisCallback#onFailure(String)}. No client-side
     * fake analysis result is produced.
     */
    public static void analyzeItemAsync(Context context, Uri imageUri, AnalysisCallback callback) {
        if (callback == null) return;
        if (context == null || imageUri == null) {
            callback.onFailure("Please select an image to analyze");
            return;
        }

        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                byte[] jpeg = ImageValidationUtils.compress(
                        appContext, imageUri, Constants.MAX_IMAGE_DIMENSION, Constants.JPEG_QUALITY);
                if (jpeg == null || jpeg.length == 0) {
                    postFailure(callback, "Unable to read the selected image");
                    return;
                }

                String base64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);
                JSONObject body = new JSONObject();
                body.put("image", base64);
                body.put("mimeType", "image/jpeg");
                body.put("model", Constants.AI_MODEL);

                JSONObject response = postJson(Constants.AI_ANALYSIS_URL, body, Constants.AI_REQUEST_TIMEOUT_MS);
                AnalysisResult result = parseResponse(response);
                if (result == null) {
                    postFailure(callback, "The AI service returned an unexpected response");
                    return;
                }

                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "AI analysis failed", e);
                postFailure(callback, "AI analysis unavailable. You can enter details manually.");
            }
        });
    }

    private static void postFailure(AnalysisCallback callback, String message) {
        mainHandler.post(() -> callback.onFailure(message));
    }

    private static JSONObject postJson(String endpoint, JSONObject body, int timeoutMs) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + Constants.AI_CLIENT_SECRET);
            connection.setRequestProperty("apikey", Constants.AI_CLIENT_SECRET);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String payload = readStream(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("AI service HTTP " + code + ": " + payload);
            }
            if (payload == null || payload.trim().isEmpty()) {
                throw new IllegalStateException("Empty AI response");
            }
            return new JSONObject(payload);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static AnalysisResult parseResponse(JSONObject response) {
        if (response == null) return null;
        JSONObject data = response.optJSONObject("data");
        if (data == null) data = response.optJSONObject("result");
        if (data == null) data = response;

        String itemType = firstNonEmpty(
                data.optString("itemType", ""),
                data.optString("item_type", ""),
                data.optString("title", ""));
        String primaryColor = firstNonEmpty(
                data.optString("primaryColor", ""),
                data.optString("primary_color", ""),
                data.optString("color", ""));
        if (itemType.isEmpty()) return null;

        AnalysisResult result = new AnalysisResult();
        result.setItemType(itemType);
        result.setPrimaryColor(primaryColor.isEmpty() ? "Unknown" : primaryColor);
        result.setColorHex(parseColorNameToHex(result.getPrimaryColor()));
        result.setSuggestedCategory(Constants.normaliseCategory(firstNonEmpty(
                data.optString("suggestedCategory", ""),
                data.optString("suggested_category", ""),
                data.optString("category", ""))));
        result.setMaterial(firstNonEmpty(data.optString("material", ""), data.optString("Material", "")));
        result.setBrand(firstNonEmpty(data.optString("brand", ""), data.optString("Brand", "")));

        List<String> features = strings(data.opt("detectedFeatures"));
        if (features.isEmpty()) features = strings(data.opt("detected_features"));
        if (features.isEmpty()) features = strings(data.opt("features"));
        result.setDetectedFeatures(features);

        result.setSecondaryColors(strings(data.opt("secondaryColors")));
        List<String> tags = strings(data.opt("tags"));
        if (tags.isEmpty()) tags = new ArrayList<>(features);
        result.setTags(tags);
        result.setKeywords(strings(data.opt("keywords")));

        double confidence = data.optDouble("confidence", 0);
        if (confidence > 0 && confidence <= 1) confidence *= 100;
        result.setConfidence(Math.max(0, Math.min(100, confidence)));
        result.setModel(firstNonEmpty(data.optString("model", ""), Constants.AI_MODEL));
        return result;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i, "");
                if (!item.trim().isEmpty()) result.add(item.trim());
            }
        } else if (value instanceof String) {
            String raw = ((String) value).trim();
            if (!raw.isEmpty()) {
                for (String part : raw.split(",")) {
                    if (!part.trim().isEmpty()) result.add(part.trim());
                }
            }
        }
        return result;
    }

    private static int parseColorNameToHex(String colorName) {
        if (colorName == null) return Color.parseColor("#6B6B6B");
        String c = colorName.toLowerCase();
        if (c.contains("black")) return Color.parseColor("#1B1B1B");
        if (c.contains("white")) return Color.parseColor("#E0E0E0");
        if (c.contains("blue")) return Color.parseColor("#1B2A4A");
        if (c.contains("brown")) return Color.parseColor("#6F4E37");
        if (c.contains("red")) return Color.parseColor("#BC4749");
        if (c.contains("green")) return Color.parseColor("#2D6A4F");
        if (c.contains("silver") || c.contains("gray") || c.contains("grey")) return Color.parseColor("#9E9E9E");
        if (c.contains("gold") || c.contains("yellow") || c.contains("brass")) return Color.parseColor("#D4A373");
        return Color.parseColor("#6B6B6B");
    }
}
