package com.laf.lostandfound.utils;

import com.laf.lostandfound.models.Item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Helpers for the normalized {@code searchTokens} array and the human readable
 * {@code searchText} field.
 *
 * <p>Firestore cannot run a substring/prefix match across a concatenated
 * string, so every report stores a de-duplicated array of words and their
 * prefixes. A query then uses {@code array-contains-any} with the user's
 * terms, which is a real index lookup and works for any word in the title or
 * description rather than only the first word.</p>
 */
public final class SearchUtils {

    /** Firestore allows at most 30 disjunctions per query. */
    public static final int MAX_QUERY_TERMS = 10;

    private static final int MAX_SEARCH_TEXT_LENGTH = 1200;
    private static final int MAX_SEARCH_TOKENS = 500;
    private static final int MAX_PREFIX_LENGTH = 8;

    private SearchUtils() {
    }

    public static String normalise(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean lastSpace = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
                lastSpace = false;
            } else if (!lastSpace) {
                builder.append(' ');
                lastSpace = true;
            }
        }
        return builder.toString().trim();
    }

    /**
     * Builds the {@code searchTokens} index: every normalised word plus a small
     * set of prefixes so type-ahead queries ("wall", "walle", "wallet") all hit
     * the same document. The array is de-duplicated and bounded so a long
     * description cannot bloat the document.
     */
    public static List<String> buildSearchTokens(Item item) {
        if (item == null) return new ArrayList<>();
        Set<String> coreWords = new LinkedHashSet<>();
        addWords(coreWords, item.getTitle());
        addWords(coreWords, item.getCategory());
        addWords(coreWords, item.getType());
        addWords(coreWords, item.getPrimaryColor());
        addWords(coreWords, join(item.getSecondaryColors()));
        addWords(coreWords, item.getMaterial());
        addWords(coreWords, item.getBrand());
        addWords(coreWords, join(item.getTags()));
        addWords(coreWords, join(item.getKeywords()));
        addWords(coreWords, join(item.getFeatures()));
        addWords(coreWords, item.getLocation());

        Set<String> descWords = new LinkedHashSet<>();
        addWords(descWords, item.getDescription());

        List<String> tokens = new ArrayList<>();
        // Priority 1: Core metadata words
        for (String word : coreWords) {
            if (tokens.size() >= MAX_SEARCH_TOKENS) break;
            if (!tokens.contains(word)) tokens.add(word);
        }
        // Priority 2: Core prefixes (for instant type-ahead on title/category/brand/color)
        for (String word : coreWords) {
            if (tokens.size() >= MAX_SEARCH_TOKENS) break;
            int maxPrefix = Math.min(word.length() - 1, MAX_PREFIX_LENGTH);
            for (int length = 2; length <= maxPrefix; length++) {
                if (tokens.size() >= MAX_SEARCH_TOKENS) break;
                String prefix = word.substring(0, length);
                if (!tokens.contains(prefix)) tokens.add(prefix);
            }
        }
        // Priority 3: Description words
        for (String word : descWords) {
            if (tokens.size() >= MAX_SEARCH_TOKENS) break;
            if (!tokens.contains(word)) tokens.add(word);
        }
        return tokens;
    }

    /**
     * Human readable haystack retained for older cached documents and for
     * debugging. The authoritative index is {@link #buildSearchTokens(Item)}.
     */
    public static String buildSearchText(Item item) {
        if (item == null) return "";
        Set<String> parts = new LinkedHashSet<>();
        add(parts, item.getTitle());
        add(parts, item.getCategory());
        add(parts, item.getType());
        add(parts, item.getPrimaryColor());
        addAll(parts, item.getSecondaryColors());
        add(parts, item.getMaterial());
        add(parts, item.getBrand());
        addAll(parts, item.getTags());
        addAll(parts, item.getKeywords());
        addAll(parts, item.getFeatures());
        add(parts, item.getLocation());
        add(parts, item.getDescription());

        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(part);
            if (builder.length() >= MAX_SEARCH_TEXT_LENGTH) break;
        }
        String result = builder.toString().trim();
        return result.length() <= MAX_SEARCH_TEXT_LENGTH
                ? result
                : result.substring(0, MAX_SEARCH_TEXT_LENGTH);
    }

    private static void add(Set<String> parts, String value) {
        if (value == null) return;
        String clean = normalise(value);
        if (!clean.isEmpty()) parts.add(clean);
    }

    private static void addAll(Set<String> parts, List<String> values) {
        if (values == null) return;
        for (String value : values) add(parts, value);
    }

    private static void addWords(Set<String> words, String value) {
        String clean = normalise(value);
        if (clean.isEmpty()) return;
        for (String word : clean.split(" ")) {
            if (word.length() >= 2) words.add(word);
        }
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(value.trim());
        }
        return builder.toString();
    }

    /**
     * Query terms used with {@code array-contains-any}. Terms are capped at
     * Firestore's disjunction limit and de-duplicated.
     */
    public static List<String> terms(String query) {
        Set<String> result = new LinkedHashSet<>();
        String clean = normalise(query);
        if (clean.isEmpty()) return new ArrayList<>();
        for (String term : clean.split(" ")) {
            if (term.length() >= 2) result.add(term);
            if (result.size() >= MAX_QUERY_TERMS) break;
        }
        return new ArrayList<>(result);
    }

    public static boolean matchesText(Item item, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        if (item == null) return false;

        List<String> tokens = item.getSearchTokens();
        if (tokens != null && !tokens.isEmpty()) {
            for (String term : terms(query)) {
                if (!tokens.contains(term)) return false;
            }
            return true;
        }

        String haystack = item.getSearchText();
        if (haystack == null || haystack.trim().isEmpty()) {
            haystack = buildSearchText(item);
        }
        for (String term : terms(query)) {
            if (!haystack.contains(term)) return false;
        }
        return true;
    }

    public static String normalizeCategory(String value) {
        return Constants.normaliseCategory(value);
    }
}
