package com.laf.lostandfound.models;

import java.io.Serializable;

public class SearchResult implements Serializable {

    private Item item;
    private int matchPercentage;

    public SearchResult() {
    }

    public SearchResult(Item item, int matchPercentage) {
        this.item = item;
        this.matchPercentage = matchPercentage;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public int getMatchPercentage() {
        return matchPercentage;
    }

    public void setMatchPercentage(int matchPercentage) {
        this.matchPercentage = matchPercentage;
    }
}
