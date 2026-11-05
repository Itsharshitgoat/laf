package com.laf.lostandfound.utils;

public final class Constants {

    private Constants() {
    }

    // Firestore Collections
    public static final String COLLECTION_ITEMS = "items";
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_NOTIFICATIONS = "notifications";
    public static final String COLLECTION_ACTIVITIES = "activities";
    public static final String COLLECTION_CLAIMS = "claims";
    public static final String COLLECTION_MATCHES = "matches";

    // Firebase Storage folders (user-scoped paths are required by storage.rules)
    public static final String STORAGE_ITEMS = "items";
    public static final String STORAGE_PROFILE_IMAGES = "profile_images";

    // Item Types
    public static final String TYPE_LOST = "lost";
    public static final String TYPE_FOUND = "found";
    public static final String TYPE_ALL = "all";

    // Item Statuses
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CLAIMED = "claimed";
    public static final String STATUS_RETURNED = "returned";
    public static final String STATUS_CLOSED = "closed";
    public static final String STATUS_RESOLVED = "resolved"; // legacy value kept for old documents

    // Claim Statuses
    public static final String CLAIM_PENDING = "pending";
    public static final String CLAIM_APPROVED = "approved";
    public static final String CLAIM_REJECTED = "rejected";

    // Match Statuses
    public static final String MATCH_SUGGESTED = "suggested";
    public static final String MATCH_DISMISSED = "dismissed";

    // Categories
    public static final String CATEGORY_ALL = "All";
    public static final String CATEGORY_PHONES = "Phones";
    public static final String CATEGORY_WALLETS = "Wallets";
    public static final String CATEGORY_CARDS = "Cards";
    public static final String CATEGORY_KEYS = "Keys";
    public static final String CATEGORY_BAGS = "Bags";
    public static final String CATEGORY_OTHER = "Other";

    public static final String[] CATEGORIES = new String[]{
            CATEGORY_PHONES,
            CATEGORY_WALLETS,
            CATEGORY_CARDS,
            CATEGORY_KEYS,
            CATEGORY_BAGS,
            CATEGORY_OTHER
    };

    // AI Server Function (Supabase Edge Function). The Ollama key lives in the
    // function's environment variables and is never shipped in the APK.
    public static final String AI_ANALYSIS_URL =
            "https://kwydfsqcuuznybjsxm.supabase.co/functions/v1/analyze-item";
    public static final String AI_MODEL = "gemma3:27b";
    public static final String AI_CLIENT_SECRET = "laf-campus-vision-token-v1";

    // Upload limits / validation
    public static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L; // 10 MB
    public static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L; // 5 MB
    public static final int MAX_IMAGE_DIMENSION = 1600;
    public static final int MAX_AVATAR_DIMENSION = 720;
    public static final int JPEG_QUALITY = 82;
    public static final int SEARCH_PAGE_SIZE = 20;
    public static final int AI_REQUEST_TIMEOUT_MS = 60_000;
    public static final String STORAGE_PROVIDER_FIREBASE = "firebase";

    // Activity action types
    public static final String ACTION_REPORTED_LOST = "reported_lost";
    public static final String ACTION_REPORTED_FOUND = "reported_found";
    public static final String ACTION_MATCHED = "matched";
    public static final String ACTION_CLAIM_APPROVED = "claim_approved";
    public static final String ACTION_RETURNED = "returned";
    public static final String ACTION_CLOSED = "closed";

    // Report workflow
    public static final int WIZARD_STEPS = 7;
    public static final int MATCH_NOTIFICATION_THRESHOLD = 45;

    // Intent Extras
    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_ITEM = "extra_item";
    public static final String EXTRA_SEARCH_QUERY = "extra_search_query";
    public static final String EXTRA_REPORT_TYPE = "extra_report_type";
    public static final String EXTRA_IMAGE_URI = "extra_image_uri";

    public static boolean isActiveStatus(String status) {
        return status == null || status.trim().isEmpty()
                || STATUS_ACTIVE.equalsIgnoreCase(status.trim());
    }

    public static boolean isReturnedStatus(String status) {
        if (status == null) return false;
        String s = status.trim();
        return STATUS_RETURNED.equalsIgnoreCase(s)
                || STATUS_RESOLVED.equalsIgnoreCase(s);
    }

    public static boolean isClosedStatus(String status) {
        return status != null && STATUS_CLOSED.equalsIgnoreCase(status.trim());
    }

    public static boolean isValidStatus(String status) {
        if (status == null) return false;
        String s = status.trim();
        return STATUS_ACTIVE.equalsIgnoreCase(s)
                || STATUS_CLAIMED.equalsIgnoreCase(s)
                || STATUS_RETURNED.equalsIgnoreCase(s)
                || STATUS_CLOSED.equalsIgnoreCase(s)
                || STATUS_RESOLVED.equalsIgnoreCase(s);
    }

    public static String typeLabel(String type) {
        return TYPE_FOUND.equalsIgnoreCase(type) ? "Found" : "Lost";
    }

    public static String normaliseCategory(String value) {
        if (value == null) return CATEGORY_OTHER;
        String trimmed = value.trim();
        for (String category : CATEGORIES) {
            if (category.equalsIgnoreCase(trimmed)) {
                return category;
            }
        }
        return CATEGORY_OTHER;
    }
}
