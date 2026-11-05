package com.laf.lostandfound;

import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.utils.SearchUtils;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchUtilsTest {

    @Test
    public void testNormalise() {
        assertEquals("leather wallet", SearchUtils.normalise("  Leather, Wallet!  "));
        assertEquals("macbook pro 14", SearchUtils.normalise("MacBook Pro 14\""));
        assertEquals("", SearchUtils.normalise(null));
        assertEquals("", SearchUtils.normalise("   "));
    }

    @Test
    public void testTermsExtraction() {
        List<String> terms = SearchUtils.terms("Black leather wallet");
        assertEquals(3, terms.size());
        assertTrue(terms.contains("black"));
        assertTrue(terms.contains("leather"));
        assertTrue(terms.contains("wallet"));
    }

    @Test
    public void testBuildSearchTokens() {
        Item item = new Item();
        item.setTitle("Black Leather Wallet");
        item.setCategory("Wallets");
        item.setPrimaryColor("Black");
        item.setSecondaryColors(Arrays.asList("Brown"));
        item.setBrand("Fossil");
        item.setLocation("Central Library");

        List<String> tokens = SearchUtils.buildSearchTokens(item);
        assertTrue(tokens.contains("wallet"));
        assertTrue(tokens.contains("wall"));
        assertTrue(tokens.contains("leather"));
        assertTrue(tokens.contains("leat"));
        assertTrue(tokens.contains("black"));
        assertTrue(tokens.contains("fossil"));
        assertTrue(tokens.contains("brown"));
        assertTrue(tokens.contains("library"));
    }

    @Test
    public void testMatchesTextWithTokens() {
        Item item = new Item();
        item.setTitle("Black Leather Wallet");
        item.setCategory("Wallets");
        item.setPrimaryColor("Black");
        item.setSearchTokens(SearchUtils.buildSearchTokens(item));

        // Substring and multi-word searches must all pass
        assertTrue(SearchUtils.matchesText(item, "wallet"));
        assertTrue(SearchUtils.matchesText(item, "leather"));
        assertTrue(SearchUtils.matchesText(item, "black"));
        assertTrue(SearchUtils.matchesText(item, "leather wallet"));
        assertTrue(SearchUtils.matchesText(item, "black wallet"));

        // Unrelated query must not match
        assertFalse(SearchUtils.matchesText(item, "umbrella"));
    }
}
