package com.foodie.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Utility class for building workout setsJson for different workout types.
 */
@Slf4j
public final class WorkoutJsonBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TIMED_EXERCISES = Set.of("HANG", "FARMERS_CARRY", "PLANK");

    private WorkoutJsonBuilder() {
        // Utility class - prevent instantiation
    }

    /**
     * Build JSON for RUNNING/WALKING workouts.
     */
    public static String buildRunningJson(String tempo, Double distanceKm, Integer estimatedKcal) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tempo", tempo != null ? tempo : "");
            map.put("distanceKm", distanceKm != null ? distanceKm : 0.0);
            map.put("estimatedKcal", estimatedKcal != null ? estimatedKcal : 0);
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to build running JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Build JSON for HIIT workouts.
     */
    public static String buildHiitJson(String hiitType, Integer warmupSecs, Integer cooldownSecs,
                                       Integer numberOfSets, Integer highIntensitySecs, Integer restSecs) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("hiitType", hiitType != null ? hiitType : "RUNNING");
            map.put("warmupSecs", warmupSecs != null ? warmupSecs : 0);
            map.put("cooldownSecs", cooldownSecs != null ? cooldownSecs : 0);
            map.put("numberOfSets", numberOfSets != null ? numberOfSets : 1);
            map.put("sets", List.of(Map.of(
                    "highIntensitySecs", highIntensitySecs != null ? highIntensitySecs : 0,
                    "restSecs", restSecs != null ? restSecs : 0)));
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to build HIIT JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Build JSON for STRENGTH workouts.
     */
    public static String buildStrengthJson(String[] exercises, String[] reps, String[] numSets,
                                           String[] weightKg, String[] prepSecs, String[] workSecs) {
        try {
            List<Map<String, Object>> sets = new ArrayList<>();
            if (exercises == null) exercises = new String[0];

            for (int i = 0; i < exercises.length; i++) {
                String ex = exercises[i];
                if (ex == null || ex.isBlank()) continue;

                int r = parseIntSafe(reps, i, 0);
                int ns = parseIntSafe(numSets, i, 3);
                double kg = parseDoubleSafe(weightKg, i, 0.0);
                int prep = parseIntSafe(prepSecs, i, 10);
                int work = parseIntSafe(workSecs, i, 30);

                Map<String, Object> setMap = new LinkedHashMap<>();
                setMap.put("exercise", ex);
                setMap.put("reps", r);
                setMap.put("numSets", ns);
                if (kg > 0) setMap.put("weightKg", kg);
                if (TIMED_EXERCISES.contains(ex)) {
                    setMap.put("prepSecs", prep);
                    setMap.put("workSecs", work);
                }
                sets.add(setMap);
            }
            return OBJECT_MAPPER.writeValueAsString(Map.of("sets", sets));
        } catch (Exception e) {
            log.warn("Failed to build strength JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Build JSON for PLYO workouts.
     */
    public static String buildPlyoJson(String[] jumpTypes, String[] reps) {
        try {
            List<Map<String, Object>> sets = new ArrayList<>();
            if (jumpTypes == null) jumpTypes = new String[0];

            for (int i = 0; i < jumpTypes.length; i++) {
                String jt = jumpTypes[i];
                if (jt == null || jt.isBlank()) continue;

                int r = parseIntSafe(reps, i, 0);
                sets.add(Map.of("jumpType", jt, "reps", r));
            }
            return OBJECT_MAPPER.writeValueAsString(Map.of("sets", sets));
        } catch (Exception e) {
            log.warn("Failed to build plyo JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Build JSON for PILATES/REHAB workouts (generic format).
     */
    public static String buildGenericJson(String[] descriptions, String[] reps) {
        try {
            List<Map<String, Object>> sets = new ArrayList<>();
            if (descriptions == null) descriptions = new String[0];

            for (int i = 0; i < descriptions.length; i++) {
                String desc = descriptions[i];
                if (desc == null || desc.isBlank()) continue;

                int r = parseIntSafe(reps, i, 0);
                sets.add(Map.of("description", desc, "reps", r));
            }
            return OBJECT_MAPPER.writeValueAsString(Map.of("sets", sets));
        } catch (Exception e) {
            log.warn("Failed to build generic JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Parse setsJson back to a Map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseSetsJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse setsJson: {}", e.getMessage());
            return Map.of();
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private static int parseIntSafe(String[] arr, int index, int defaultValue) {
        if (arr == null || arr.length <= index || arr[index] == null || arr[index].isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(arr[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseDoubleSafe(String[] arr, int index, double defaultValue) {
        if (arr == null || arr.length <= index || arr[index] == null || arr[index].isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(arr[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
