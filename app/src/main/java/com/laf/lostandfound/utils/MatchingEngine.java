package com.laf.lostandfound.utils;

import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.models.SearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MatchingEngine {

    private static final int MIN_MATCH_SCORE = 35;

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for", "with",
            "is", "it", "my", "of", "from", "by", "this", "that", "i", "lost", "found",
            "item", "near", "around", "some", "was", "were", "has", "have", "had"
    ));

    private MatchingEngine() {
    }

    /**
     * Calculates a 0-100 match score between two opposite type reports.
     */
    public static int calculateMatchScore(Item item1, Item item2) {
        if (item1 == null || item2 == null) return 0;

        int score = 0;

        String category1 = SearchUtils.normalise(item1.getCategory());
        String category2 = SearchUtils.normalise(item2.getCategory());
        if (!category1.isEmpty() && category1.equals(category2)) {
            score += 30;
        } else if (!category1.isEmpty() && (category1.contains(category2) || category2.contains(category1))) {
            score += 18;
        }

        Set<String> tokens1 = extractKeywords(textOf(item1));
        Set<String> tokens2 = extractKeywords(textOf(item2));
        if (!tokens1.isEmpty() && !tokens2.isEmpty()) {
            int common = 0;
            for (String token : tokens1) {
                if (tokens2.contains(token)) common++;
            }
            float overlap = (float) common / (float) Math.min(tokens1.size(), tokens2.size());
            score += Math.round(overlap * 25f);
        }

        String color1 = SearchUtils.normalise(item1.getPrimaryColor());
        String color2 = SearchUtils.normalise(item2.getPrimaryColor());
        if (!color1.isEmpty() && !color2.isEmpty()) {
            if (color1.equals(color2)) {
                score += 20;
            } else if (color1.contains(color2) || color2.contains(color1)) {
                score += 12;
            } else {
                int secOverlap = overlap(item1.getSecondaryColors(), item2.getSecondaryColors());
                if (secOverlap > 0) {
                    score += Math.min(10, secOverlap * 5);
                }
            }
        } else {
            int secOverlap = overlap(item1.getSecondaryColors(), item2.getSecondaryColors());
            if (secOverlap > 0) {
                score += Math.min(12, secOverlap * 6);
            }
        }

        int tagOverlap = overlap(item1.getTags(), item2.getTags());
        if (tagOverlap > 0) score += Math.min(10, tagOverlap * 4);

        String location1 = SearchUtils.normalise(item1.getLocation());
        String location2 = SearchUtils.normalise(item2.getLocation());
        if (!location1.isEmpty() && !location2.isEmpty()) {
            if (location1.equals(location2)) {
                score += 10;
            } else if (location1.contains(location2) || location2.contains(location1)) {
                score += 6;
            }
        }

        if (hasCoordinates(item1) && hasCoordinates(item2)) {
            double distanceKm = haversineDistanceKm(
                    item1.getLatitude(), item1.getLongitude(),
                    item2.getLatitude(), item2.getLongitude()
            );
            if (distanceKm <= 0.2) {
                score += 15;
            } else if (distanceKm <= 1.0) {
                score += 10;
            } else if (distanceKm <= 3.0) {
                score += 5;
            }
        }

        if (!SearchUtils.normalise(item1.getBrand()).isEmpty()
                && SearchUtils.normalise(item1.getBrand()).equals(SearchUtils.normalise(item2.getBrand()))) {
            score += 3;
        }
        if (!SearchUtils.normalise(item1.getMaterial()).isEmpty()
                && SearchUtils.normalise(item1.getMaterial()).equals(SearchUtils.normalise(item2.getMaterial()))) {
            score += 2;
        }

        return Math.min(100, Math.max(0, score));
    }

    /**
     * Finds opposite-type candidates owned by someone else, sorted by score.
     */
    public static List<SearchResult> findMatches(Item targetItem, List<Item> pool) {
        List<SearchResult> results = new ArrayList<>();
        if (targetItem == null || pool == null) return results;

        String targetType = targetItem.getType() != null ? targetItem.getType() : Constants.TYPE_LOST;
        String oppositeType = Constants.TYPE_LOST.equalsIgnoreCase(targetType)
                ? Constants.TYPE_FOUND
                : Constants.TYPE_LOST;

        for (Item candidate : pool) {
            if (candidate == null) continue;
            if (candidate.getItemId() != null && candidate.getItemId().equals(targetItem.getItemId())) continue;
            if (!Constants.isActiveStatus(candidate.getStatus())) continue;
            if (targetItem.getUserId() != null && targetItem.getUserId().equals(candidate.getUserId())) continue;
            if (!oppositeType.equalsIgnoreCase(candidate.getType())) continue;

            int score = calculateMatchScore(targetItem, candidate);
            if (score >= MIN_MATCH_SCORE) {
                results.add(new SearchResult(candidate, score));
            }
        }

        Collections.sort(results, (r1, r2) ->
                Integer.compare(r2.getMatchPercentage(), r1.getMatchPercentage()));
        return results;
    }

    private static String textOf(Item item) {
        StringBuilder builder = new StringBuilder();
        builder.append(item.getTitle() == null ? "" : item.getTitle()).append(' ');
        builder.append(item.getDescription() == null ? "" : item.getDescription()).append(' ');
        builder.append(item.getBrand() == null ? "" : item.getBrand()).append(' ');
        builder.append(item.getMaterial() == null ? "" : item.getMaterial()).append(' ');
        if (item.getFeatures() != null) {
            for (String feature : item.getFeatures()) builder.append(feature).append(' ');
        }
        if (item.getTags() != null) {
            for (String tag : item.getTags()) builder.append(tag).append(' ');
        }
        if (item.getKeywords() != null) {
            for (String keyword : item.getKeywords()) builder.append(keyword).append(' ');
        }
        return builder.toString();
    }

    private static int overlap(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return 0;
        int count = 0;
        for (String a : left) {
            for (String b : right) {
                if (a != null && b != null && a.trim().equalsIgnoreCase(b.trim())) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        String clean = SearchUtils.normalise(text);
        if (clean.isEmpty()) return keywords;
        for (String word : clean.split(" ")) {
            if (word.length() > 2 && !STOP_WORDS.contains(word.toLowerCase(Locale.ROOT))) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    private static boolean hasCoordinates(Item item) {
        return item != null && (item.getLatitude() != 0.0 || item.getLongitude() != 0.0);
    }

    private static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
