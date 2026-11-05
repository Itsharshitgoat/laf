package com.laf.lostandfound.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.laf.lostandfound.models.ReportDraft;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the report wizard draft. SharedPreferences is enough for a small
 * serializable draft and survives process death without adding a dependency.
 */
public final class DraftManager {

    private static final String PREFS = "laf_report_draft";
    private static final String KEY_DRAFT = "draft_json";

    private DraftManager() {
    }

    public static void save(Context context, ReportDraft draft) {
        if (context == null || draft == null) return;
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_DRAFT, toJson(draft).toString()).apply();
        } catch (Exception ignored) {
            // Draft persistence is best effort.
        }
    }

    public static ReportDraft load(Context context) {
        if (context == null) return null;
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_DRAFT, null);
            if (raw == null || raw.isEmpty()) return null;
            return fromJson(new JSONObject(raw));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasDraft(Context context) {
        return load(context) != null;
    }

    public static void clear(Context context) {
        if (context == null) return;
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_DRAFT).apply();
    }

    private static JSONObject toJson(ReportDraft draft) throws Exception {
        JSONObject json = new JSONObject();
        json.put("step", draft.getStep());
        json.put("type", draft.getType());
        json.put("imageUri", draft.getImageUriString());
        json.put("imageUrl", draft.getImageUrl());
        json.put("imagePath", draft.getImagePath());
        json.put("clientRequestId", draft.getClientRequestId());
        json.put("title", draft.getTitle());
        json.put("category", draft.getCategory());
        json.put("primaryColor", draft.getPrimaryColor());
        json.put("secondaryColors", listToJson(draft.getSecondaryColors()));
        json.put("material", draft.getMaterial());
        json.put("brand", draft.getBrand());
        json.put("description", draft.getDescription());
        json.put("features", listToJson(draft.getFeatures()));
        json.put("tags", listToJson(draft.getTags()));
        json.put("keywords", listToJson(draft.getKeywords()));
        json.put("aiConfidence", draft.getAiConfidence());
        json.put("aiModel", draft.getAiModel());
        json.put("aiStatus", draft.getAiStatus());
        json.put("location", draft.getLocation());
        json.put("incidentAt", draft.getIncidentAt());
        json.put("aiFailed", draft.isAiFailed());
        return json;
    }

    private static ReportDraft fromJson(JSONObject json) {
        ReportDraft draft = new ReportDraft();
        draft.setStep(json.optInt("step", 1));
        draft.setType(json.optString("type", "lost"));
        draft.setImageUriString(json.optString("imageUri", ""));
        draft.setImageUrl(json.optString("imageUrl", ""));
        draft.setImagePath(json.optString("imagePath", ""));
        draft.setClientRequestId(json.optString("clientRequestId", ""));
        draft.setTitle(json.optString("title", ""));
        draft.setCategory(json.optString("category", ""));
        draft.setPrimaryColor(json.optString("primaryColor", ""));
        draft.setSecondaryColors(jsonToList(json.optJSONArray("secondaryColors")));
        draft.setMaterial(json.optString("material", ""));
        draft.setBrand(json.optString("brand", ""));
        draft.setDescription(json.optString("description", ""));
        draft.setFeatures(jsonToList(json.optJSONArray("features")));
        draft.setTags(jsonToList(json.optJSONArray("tags")));
        draft.setKeywords(jsonToList(json.optJSONArray("keywords")));
        draft.setAiConfidence(json.optDouble("aiConfidence", 0));
        draft.setAiModel(json.optString("aiModel", ""));
        draft.setAiStatus(json.optString("aiStatus", ""));
        draft.setLocation(json.optString("location", ""));
        draft.setIncidentAt(json.optLong("incidentAt", 0));
        draft.setAiFailed(json.optBoolean("aiFailed", false));
        return draft;
    }

    private static JSONArray listToJson(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) array.put(value);
            }
        }
        return array;
    }

    private static List<String> jsonToList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.trim().isEmpty()) result.add(value);
            }
        }
        return result;
    }
}
