package com.foodie.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BrainTestType {

    REACTION_TIME("Reaction Time", "bi-stopwatch",
            "5 rounds. The final average is recorded automatically."),
    PROCESSING_SPEED("Processing Speed", "bi-grid-3x3-gap",
            "3 levels. Average response time and accuracy are recorded automatically.");

    private final String label;
    private final String icon;
    private final String description;
}
