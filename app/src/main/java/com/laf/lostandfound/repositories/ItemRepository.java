package com.laf.lostandfound.repositories;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.utils.Constants;
import com.laf.lostandfound.utils.SearchUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ItemRepository {

    private static ItemRepository instance;
    private final FirebaseFirestore db;

    public interface ItemCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface ItemDataCallback {
        void onSuccess(Item item);
        void onFailure(String errorMessage);
    }

    public interface ItemListCallback {
        void onSuccess(List<Item> items);
        void onFailure(String errorMessage);
    }

    public interface SearchPageCallback {
        void onSuccess(List<Item> items, DocumentSnapshot lastVisible, boolean hasMore);
        void onFailure(String errorMessage);
    }

    public static class SearchFilter {
        public String query = "";
        public String type = Constants.TYPE_ALL;
        public String category = Constants.CATEGORY_ALL;
        public boolean activeOnly = true;

        public SearchFilter copy() {
            SearchFilter copy = new SearchFilter();
            copy.query = query;
            copy.type = type;
            copy.category = category;
            copy.activeOnly = activeOnly;
            return copy;
        }
    }

    private ItemRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static synchronized ItemRepository getInstance() {
        if (instance == null) {
            instance = new ItemRepository();
        }
        return instance;
    }

    /**
     * Publishes a report. The client generated request id doubles as the
     * Firestore document id, making retries idempotent.
     */
    public void addItem(@NonNull Item item, @NonNull ItemCallback callback) {
        String requestId = item.getClientRequestId();
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
            item.setClientRequestId(requestId);
        }
        item.setId(requestId);

        long now = System.currentTimeMillis();
        if (item.getReportCreatedAt() <= 0) item.setReportCreatedAt(now);
        if (item.getIncidentAt() <= 0) item.setIncidentAt(item.getReportCreatedAt());
        item.setUpdatedAt(now);
        if (item.getStatus() == null || item.getStatus().trim().isEmpty()) {
            item.setStatus(Constants.STATUS_ACTIVE);
        }
        if (item.getSearchText() == null || item.getSearchText().trim().isEmpty()) {
            item.setSearchText(SearchUtils.buildSearchText(item));
        }
        item.setSearchTokens(SearchUtils.buildSearchTokens(item));

        db.collection(Constants.COLLECTION_ITEMS)
                .document(requestId)
                .set(item.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to publish report")));
    }

    public void updateItem(@NonNull Item item, @NonNull ItemCallback callback) {
        if (item.getId() == null || item.getId().trim().isEmpty()) {
            callback.onFailure("Invalid item ID");
            return;
        }
        item.setUpdatedAt(System.currentTimeMillis());
        item.setSearchText(SearchUtils.buildSearchText(item));
        item.setSearchTokens(SearchUtils.buildSearchTokens(item));
        db.collection(Constants.COLLECTION_ITEMS)
                .document(item.getId())
                .set(item.toMap())
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to update report")));
    }

    public void getItemById(String itemId, @NonNull ItemDataCallback callback) {
        if (itemId == null || itemId.isEmpty()) {
            callback.onFailure("Invalid item ID");
            return;
        }

        db.collection(Constants.COLLECTION_ITEMS)
                .document(itemId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        Item item = fromDocument(documentSnapshot);
                        if (item != null) {
                            callback.onSuccess(item);
                        } else {
                            callback.onFailure("Item is empty");
                        }
                    } else {
                        callback.onFailure("Item not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to load item")));
    }

    public ListenerRegistration getItemRealtime(String itemId, @NonNull ItemDataCallback callback) {
        if (itemId == null || itemId.isEmpty()) {
            callback.onFailure("Invalid item ID");
            return null;
        }
        return db.collection(Constants.COLLECTION_ITEMS)
                .document(itemId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Failed to watch item"));
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        Item item = fromDocument(snapshot);
                        if (item != null) callback.onSuccess(item);
                    } else {
                        callback.onFailure("This report is no longer available");
                    }
                });
    }

    public void getRecentItems(@NonNull ItemListCallback callback) {
        db.collection(Constants.COLLECTION_ITEMS)
                .whereEqualTo("status", Constants.STATUS_ACTIVE)
                .orderBy("reportCreatedAt", Query.Direction.DESCENDING)
                .limit(40)
                .get()
                .addOnSuccessListener(snapshots -> callback.onSuccess(toList(snapshots)))
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to load items")));
    }

    public ListenerRegistration listenToRecentItems(@NonNull ItemListCallback callback) {
        return db.collection(Constants.COLLECTION_ITEMS)
                .whereEqualTo("status", Constants.STATUS_ACTIVE)
                .orderBy("reportCreatedAt", Query.Direction.DESCENDING)
                .limit(40)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Error listening to the feed"));
                        return;
                    }
                    if (snapshots != null) callback.onSuccess(toList(snapshots));
                });
    }

    public void getItemsByCategory(String category, @NonNull ItemListCallback callback) {
        if (category == null || category.equalsIgnoreCase(Constants.CATEGORY_ALL)) {
            getRecentItems(callback);
            return;
        }

        db.collection(Constants.COLLECTION_ITEMS)
                .whereEqualTo("status", Constants.STATUS_ACTIVE)
                .whereEqualTo("category", category)
                .orderBy("reportCreatedAt", Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .addOnSuccessListener(snapshots -> callback.onSuccess(toList(snapshots)))
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to load items")));
    }

    public void getItemsByUser(String userId, @NonNull ItemListCallback callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return;
        }

        db.collection(Constants.COLLECTION_ITEMS)
                .whereEqualTo("userId", userId)
                .orderBy("reportCreatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshots -> callback.onSuccess(toList(snapshots)))
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to load your reports")));
    }

    public ListenerRegistration listenToUserItems(String userId, @NonNull ItemListCallback callback) {
        if (userId == null || userId.isEmpty()) {
            callback.onFailure("Invalid user ID");
            return null;
        }
        return db.collection(Constants.COLLECTION_ITEMS)
                .whereEqualTo("userId", userId)
                .orderBy("reportCreatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(message(error, "Failed to watch your reports"));
                        return;
                    }
                    if (snapshots != null) callback.onSuccess(toList(snapshots));
                });
    }

    public void getActiveItemsByType(String type, @NonNull ItemListCallback callback) {
        db.collection(Constants.COLLECTION_ITEMS)
                .whereEqualTo("status", Constants.STATUS_ACTIVE)
                .whereEqualTo("type", type)
                .orderBy("reportCreatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshots -> callback.onSuccess(toList(snapshots)))
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to load matching items")));
    }

    /**
     * Server-side search using the {@code searchTokens} array index. Every word
     * (and short prefix) of the report is stored in that array, so a query for
     * any word in a multi-word title is an indexed {@code array-contains-any}
     * lookup rather than the old, broken prefix range over one concatenated
     * string.
     */
    public void searchItems(@NonNull SearchFilter filter, DocumentSnapshot startAfter,
                            @NonNull SearchPageCallback callback) {
        String queryText = filter.query == null ? "" : filter.query.trim();
        List<String> terms = SearchUtils.terms(queryText);
        boolean hasText = !terms.isEmpty();

        Query query = db.collection(Constants.COLLECTION_ITEMS);
        if (filter.activeOnly) {
            query = query.whereEqualTo("status", Constants.STATUS_ACTIVE);
        }
        if (filter.type != null && !Constants.TYPE_ALL.equalsIgnoreCase(filter.type)) {
            query = query.whereEqualTo("type", filter.type);
        }
        if (filter.category != null && !Constants.CATEGORY_ALL.equalsIgnoreCase(filter.category)) {
            query = query.whereEqualTo("category", filter.category);
        }

        if (hasText) {
            query = query.whereArrayContainsAny("searchTokens", terms);
        }
        query = query.orderBy("reportCreatedAt", Query.Direction.DESCENDING);

        query = query.limit(Constants.SEARCH_PAGE_SIZE + 1);
        if (startAfter != null) query = query.startAfter(startAfter);

        query.get()
                .addOnSuccessListener(snapshots -> {
                    // Keep the raw page so the cursor always advances, even when
                    // some documents are filtered out by the multi-term matcher.
                    List<QueryDocumentSnapshot> documents = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        documents.add(doc);
                    }

                    boolean hasMore = documents.size() > Constants.SEARCH_PAGE_SIZE;
                    int pageCount = Math.min(documents.size(), Constants.SEARCH_PAGE_SIZE);

                    List<Item> items = new ArrayList<>();
                    for (int i = 0; i < pageCount; i++) {
                        Item item = fromDocument(documents.get(i));
                        if (item != null && SearchUtils.matchesText(item, queryText)) {
                            items.add(item);
                        }
                    }

                    DocumentSnapshot lastVisible = pageCount > 0
                            ? documents.get(pageCount - 1)
                            : null;
                    callback.onSuccess(items, lastVisible, hasMore);
                })
                .addOnFailureListener(e -> callback.onFailure(message(e, "Search failed")));
    }

    public void searchItems(String query, @NonNull ItemListCallback callback) {
        SearchFilter filter = new SearchFilter();
        filter.query = query == null ? "" : query;
        searchItems(filter, null, new SearchPageCallback() {
            @Override
            public void onSuccess(List<Item> items, DocumentSnapshot lastVisible, boolean hasMore) {
                callback.onSuccess(items);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    public void updateItemStatus(String itemId, String status, @NonNull ItemCallback callback) {
        if (itemId == null || itemId.isEmpty()) {
            callback.onFailure("Invalid item ID");
            return;
        }
        if (!Constants.isValidStatus(status)) {
            callback.onFailure("Invalid status");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("updatedAt", System.currentTimeMillis());
        if (Constants.isReturnedStatus(status)) {
            updates.put("returnedAt", System.currentTimeMillis());
        }
        if (Constants.isClosedStatus(status)) {
            updates.put("closedAt", System.currentTimeMillis());
        }

        db.collection(Constants.COLLECTION_ITEMS)
                .document(itemId)
                .update(updates)
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to update report status")));
    }

    public void markItemAsReturned(String itemId, @NonNull ItemCallback callback) {
        updateItemStatus(itemId, Constants.STATUS_RETURNED, callback);
    }

    public void closeReport(String itemId, @NonNull ItemCallback callback) {
        updateItemStatus(itemId, Constants.STATUS_CLOSED, callback);
    }

    public void deleteItem(String itemId, @NonNull ItemCallback callback) {
        if (itemId == null || itemId.isEmpty()) {
            callback.onFailure("Invalid item ID");
            return;
        }

        db.collection(Constants.COLLECTION_ITEMS)
                .document(itemId)
                .delete()
                .addOnSuccessListener(aVoid -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(message(e, "Failed to delete report")));
    }

    public DocumentReference newDocumentReference() {
        return db.collection(Constants.COLLECTION_ITEMS).document();
    }

    private static List<Item> toList(Iterable<QueryDocumentSnapshot> snapshots) {
        List<Item> list = new ArrayList<>();
        if (snapshots == null) return list;
        for (QueryDocumentSnapshot doc : snapshots) {
            Item item = fromDocument(doc);
            if (item != null) list.add(item);
        }
        return list;
    }

    private static Item fromDocument(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Item item = doc.toObject(Item.class);
        if (item == null) return null;
        item.setId(doc.getId());

        if (item.getReportCreatedAt() <= 0) {
            Long legacyCreated = doc.getLong("createdAt");
            if (legacyCreated != null) item.setReportCreatedAt(legacyCreated);
        }
        if (item.getIncidentAt() <= 0) {
            Long legacyTimestamp = doc.getLong("timestamp");
            if (legacyTimestamp != null) item.setIncidentAt(legacyTimestamp);
        }
        if (item.getPrimaryColor() == null || item.getPrimaryColor().trim().isEmpty()) {
            item.setPrimaryColor(doc.getString("color"));
        }
        if (item.getFeatures() == null || item.getFeatures().isEmpty()) {
            item.setDetectedFeatures(doc.getString("detectedFeatures"));
        }
        if (item.getStatus() == null || item.getStatus().trim().isEmpty()) {
            item.setStatus(Constants.STATUS_ACTIVE);
        }
        if (item.getSearchText() == null || item.getSearchText().trim().isEmpty()) {
            item.setSearchText(SearchUtils.buildSearchText(item));
        }
        if (item.getSearchTokens() == null || item.getSearchTokens().isEmpty()) {
            item.setSearchTokens(SearchUtils.buildSearchTokens(item));
        }
        return item;
    }

    private static String message(Exception e, String fallback) {
        return e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty()
                ? e.getMessage()
                : fallback;
    }
}
