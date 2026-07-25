package com.foodie.model;

/**
 * Unit of measurement for products.
 */
public enum Unit {
    KG("kg"),
    LITER("L"),
    PIECE("pc");

    private final String label;

    Unit(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
