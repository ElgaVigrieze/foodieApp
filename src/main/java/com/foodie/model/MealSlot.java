package com.foodie.model;

/**
 * Time slot for a meal in the meal plan.
 */
public enum MealSlot {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    private final String label;

    MealSlot(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
