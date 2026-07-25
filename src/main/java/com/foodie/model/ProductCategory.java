package com.foodie.model;

/**
 * Category of a product (ingredient).
 */
public enum ProductCategory {
    FRUIT_BERRIES("Fruit & Berries"),
    VEGETABLES("Vegetables"),
    NUTS_SEEDS("Nuts & Seeds"),
    GRAINS("Grains"),
    MEAT("Meat"),
    FISH_SEA_PRODUCTS("Fish & Sea Products"),
    DAIRY("Dairy"),
    CONDIMENTS("Condiments"),
    SNACKS("Snacks");

    private final String label;

    ProductCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
