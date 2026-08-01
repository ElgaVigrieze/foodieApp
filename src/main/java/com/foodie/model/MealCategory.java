package com.foodie.model;

/**
 * Category of a meal.
 */
public enum MealCategory {
    MAIN_COURSE("Main Course"),
    SOUP("Soup"),
    SALAD("Salad"),
    SNACK("Snack"),
    DESSERT("Dessert"),
    DRINK("Drink"),
    SAUCE("Sauce"),
    SIDE("Side");

    private final String label;

    MealCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
