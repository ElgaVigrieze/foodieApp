package com.foodie.model;

public enum WorkoutType {
    RUNNING("Running"),
    WALKING("Walking"),
    HIIT("HIIT"),
    STRENGTH("Strength"),
    PILATES("Pilates"),
    PLYO("Plyo"),
    REHAB("Rehab");

    private final String label;

    WorkoutType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
