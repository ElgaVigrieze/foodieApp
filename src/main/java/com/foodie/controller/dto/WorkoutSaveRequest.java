package com.foodie.controller.dto;

import com.foodie.model.WorkoutType;
import com.foodie.util.WorkoutJsonBuilder;

/**
 * Request DTO for saving a workout.
 * Encapsulates all form parameters and handles JSON building based on workout type.
 */
public record WorkoutSaveRequest(
        // Common fields
        Long id,
        String name,
        String type,
        String notes,
        String youtubeUrl,
        Integer totalMinutes,
        Integer estimatedKcal,

        // RUNNING/WALKING fields
        String runningTempo,
        Double runningDistanceKm,
        Integer runningEstimatedKcal,

        // HIIT fields
        String hiitType,
        Integer hiitWarmupSecs,
        Integer hiitCooldownSecs,
        Integer hiitNumberOfSets,
        Integer hiitHighIntensitySecs,
        Integer hiitRestSecs,

        // STRENGTH fields
        String[] strengthExercise,
        String[] strengthReps,
        String[] strengthNumSets,
        String[] strengthWeightKg,
        String[] strengthPrepSecs,
        String[] strengthWorkSecs,

        // PLYO fields
        String[] plyoJumpType,
        String[] plyoReps,

        // PILATES/REHAB fields
        String[] genericDescription,
        String[] genericReps
) {
    /**
     * Get the workout type as an enum.
     */
    public WorkoutType getWorkoutType() {
        return WorkoutType.valueOf(type);
    }

    /**
     * Build the setsJson based on the workout type.
     */
    public String buildSetsJson() {
        return switch (getWorkoutType()) {
            case RUNNING, WALKING -> WorkoutJsonBuilder.buildRunningJson(
                    runningTempo, runningDistanceKm, runningEstimatedKcal);
            case HIIT -> WorkoutJsonBuilder.buildHiitJson(
                    hiitType, hiitWarmupSecs, hiitCooldownSecs,
                    hiitNumberOfSets, hiitHighIntensitySecs, hiitRestSecs);
            case STRENGTH -> WorkoutJsonBuilder.buildStrengthJson(
                    nullSafe(strengthExercise), nullSafe(strengthReps),
                    nullSafe(strengthNumSets), nullSafe(strengthWeightKg),
                    nullSafe(strengthPrepSecs), nullSafe(strengthWorkSecs));
            case PLYO -> WorkoutJsonBuilder.buildPlyoJson(
                    nullSafe(plyoJumpType), nullSafe(plyoReps));
            case PILATES, REHAB -> WorkoutJsonBuilder.buildGenericJson(
                    nullSafe(genericDescription), nullSafe(genericReps));
        };
    }

    private String[] nullSafe(String[] arr) {
        return arr != null ? arr : new String[0];
    }
}
