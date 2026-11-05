package com.laf.lostandfound.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Category implements Serializable {

    private String name;
    private boolean isSelected;

    public Category() {
    }

    public Category(String name, boolean isSelected) {
        this.name = name;
        this.isSelected = isSelected;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public static List<Category> getDefaultCategories() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("All", true));
        list.add(new Category("Phones", false));
        list.add(new Category("Wallets", false));
        list.add(new Category("Cards", false));
        list.add(new Category("Keys", false));
        list.add(new Category("Bags", false));
        list.add(new Category("Other", false));
        return list;
    }
}
