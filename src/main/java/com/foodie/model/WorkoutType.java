package com.foodie.model;

import lombok.Getter;

@Getter
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

}