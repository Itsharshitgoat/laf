package com.laf.lostandfound;

import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.models.SearchResult;
import com.laf.lostandfound.utils.MatchingEngine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MatchingEngineTest {

    @Test
    public void testMatchingScoreHighForSameCategoryAndColor() {
        Item lost = new Item();
        lost.setId("lost_1");
        lost.setType("lost");
        lost.setTitle("Blue Dell Laptop");
        lost.setCategory("Electronics");
        lost.setPrimaryColor("Blue");
        lost.setLocation("Library 2nd Floor");
        lost.setLatitude(12.9716);
        lost.setLongitude(77.5946);

        Item found = new Item();
        found.setId("found_1");
        found.setType("found");
        found.setTitle("Dell Laptop Blue");
        found.setCategory("Electronics");
        found.setPrimaryColor("Blue");
        found.setLocation("Library");
        found.setLatitude(12.9718);
        found.setLongitude(77.5947);

        int score = MatchingEngine.calculateMatchScore(lost, found);
        // Category (30) + Keywords overlap (overlap*25) + Color (20) + Location (6-10) + Coordinates (15) > 70
        assertTrue("Match score should be high for matching items: " + score, score >= 70);
    }

    @Test
    public void testMatchingScoreLowForDissimilarItems() {
        Item lost = new Item();
        lost.setType("lost");
        lost.setTitle("Black Leather Wallet");
        lost.setCategory("Wallets");
        lost.setPrimaryColor("Black");

        Item found = new Item();
        found.setType("found");
        found.setTitle("Gold Ring");
        found.setCategory("Jewelry");
        found.setPrimaryColor("Gold");

        int score = MatchingEngine.calculateMatchScore(lost, found);
        assertTrue("Dissimilar items should have low score: " + score, score < 20);
    }

    @Test
    public void testSecondaryColorsMatching() {
        Item lost = new Item();
        lost.setType("lost");
        lost.setTitle("Striped Backpack");
        lost.setCategory("Bags");
        lost.setPrimaryColor("Blue");
        lost.setSecondaryColors(Arrays.asList("Red", "White"));

        Item found = new Item();
        found.setType("found");
        found.setTitle("Blue and Red Backpack");
        found.setCategory("Bags");
        found.setPrimaryColor("Blue");
        found.setSecondaryColors(Arrays.asList("Red"));

        int score = MatchingEngine.calculateMatchScore(lost, found);
        assertTrue("Score with matching secondary color should be significant", score >= 50);
    }

    @Test
    public void testFindMatchesFiltersOppositeTypeAndExcludesSameOwner() {
        Item target = new Item();
        target.setId("item_target");
        target.setUserId("user_a");
        target.setType("lost");
        target.setTitle("Apple AirPods Pro");
        target.setCategory("Electronics");
        target.setPrimaryColor("White");

        Item candidate1 = new Item();
        candidate1.setId("item_c1");
        candidate1.setUserId("user_b");
        candidate1.setType("found");
        candidate1.setStatus("active");
        candidate1.setTitle("AirPods Pro in white case");
        candidate1.setCategory("Electronics");
        candidate1.setPrimaryColor("White");

        Item candidateSameOwner = new Item();
        candidateSameOwner.setId("item_c2");
        candidateSameOwner.setUserId("user_a"); // Same user!
        candidateSameOwner.setType("found");
        candidateSameOwner.setStatus("active");
        candidateSameOwner.setTitle("AirPods Pro");

        Item candidateSameType = new Item();
        candidateSameType.setId("item_c3");
        candidateSameType.setUserId("user_c");
        candidateSameType.setType("lost"); // Same type (lost), should be ignored!
        candidateSameType.setStatus("active");
        candidateSameType.setTitle("AirPods Pro");

        List<Item> pool = new ArrayList<>(Arrays.asList(candidate1, candidateSameOwner, candidateSameType));
        List<SearchResult> matches = MatchingEngine.findMatches(target, pool);

        assertEquals(1, matches.size());
        assertEquals("item_c1", matches.get(0).getItem().getId());
    }
}
