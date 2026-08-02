package com.foodie.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.model.AppUser;
import com.foodie.model.TrainingPlanEntry;
import com.foodie.model.Workout;
import com.foodie.repository.TrainingPlanEntryRepository;
import com.foodie.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FitnessService {

    private final WorkoutRepository workoutRepository;
    private final TrainingPlanEntryRepository trainingPlanEntryRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    // ── Workout library ──────────────────────────────────────────────────────

    public List<Workout> findAll() {
        AppUser user = currentUserService.getCurrentUser();
        if (user == null) return List.of();
        return workoutRepository.findByOwnerIdOrderByNameAsc(user.getId());
    }

    public Workout findById(Long id) {
        return workoutRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Workout not found: " + id));
    }

    @Transactional
    public Workout save(Workout workout) {
        AppUser user = currentUserService.getCurrentUser();
        if (workout.getOwner() == null) workout.setOwner(user);
        return workoutRepository.save(workout);
    }

    @Transactional
    public void delete(Long id) {
        trainingPlanEntryRepository.deleteByWorkoutId(id);
        workoutRepository.deleteById(id);
    }

    // ── Training plan ────────────────────────────────────────────────────────

    /** Returns the Monday of the given week. */
    public LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Map of date (Mon–Sun) → entries for the week. */
    public Map<LocalDate, List<TrainingPlanEntry>> getWeeklyPlan(LocalDate weekStart) {
        AppUser user = currentUserService.getCurrentUser();
        Map<LocalDate, List<TrainingPlanEntry>> result = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            result.put(weekStart.plusDays(i), new ArrayList<>());
        }
        if (user == null) return result;

        LocalDate weekEnd = weekStart.plusDays(6);
        List<TrainingPlanEntry> entries = trainingPlanEntryRepository
                .findByOwnerIdAndDateBetweenOrderByDate(user.getId(), weekStart, weekEnd);
        for (TrainingPlanEntry e : entries) {
            result.get(e.getDate()).add(e);
        }
        return result;
    }

    @Transactional
    public TrainingPlanEntry assignWorkout(Long workoutId, LocalDate date) {
        AppUser user = currentUserService.getCurrentUser();
        Workout workout = findById(workoutId);
        TrainingPlanEntry entry = TrainingPlanEntry.builder()
                .date(date)
                .workout(workout)
                .owner(user)
                .build();
        return trainingPlanEntryRepository.save(entry);
    }

    @Transactional
    public void removeEntry(Long entryId) {
        trainingPlanEntryRepository.deleteById(entryId);
    }

    @Transactional
    public void toggleEntryCompleted(Long entryId) {
        TrainingPlanEntry entry = trainingPlanEntryRepository.findById(entryId)
                .orElseThrow(() -> new NoSuchElementException("Entry not found: " + entryId));
        entry.setCompleted(!entry.isCompleted());
        trainingPlanEntryRepository.save(entry);
    }

    // ── setsJson builders ────────────────────────────────────────────────────

    public String buildRunningJson(String tempo, Double distanceKm, Integer estimatedKcal) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tempo", tempo != null ? tempo : "");
            map.put("distanceKm", distanceKm != null ? distanceKm : 0.0);
            map.put("estimatedKcal", estimatedKcal != null ? estimatedKcal : 0);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) { return "{}"; }
    }

    public String buildHiitJson(String hiitType, Integer warmupSecs, Integer cooldownSecs,
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
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) { return "{}"; }
    }

    public String buildStrengthJson(String[] exercises, String[] reps, String[] numSets, String[] weightKg,
                                     String[] prepSecs, String[] workSecs) {
        try {
            List<Map<String, Object>> sets = new ArrayList<>();
            for (int i = 0; i < exercises.length; i++) {
                String ex = exercises[i];
                if (ex == null || ex.isBlank()) continue;
                int r = (reps != null && reps.length > i && reps[i] != null && !reps[i].isBlank())
                        ? Integer.parseInt(reps[i]) : 0;
                int ns = (numSets != null && numSets.length > i && numSets[i] != null && !numSets[i].isBlank())
                        ? Integer.parseInt(numSets[i]) : 3;
                double kg = (weightKg != null && weightKg.length > i && weightKg[i] != null && !weightKg[i].isBlank())
                        ? Double.parseDouble(weightKg[i]) : 0.0;
                int prep = (prepSecs != null && prepSecs.length > i && prepSecs[i] != null && !prepSecs[i].isBlank())
                        ? Integer.parseInt(prepSecs[i]) : 10;
                int work = (workSecs != null && workSecs.length > i && workSecs[i] != null && !workSecs[i].isBlank())
                        ? Integer.parseInt(workSecs[i]) : 30;
                Map<String, Object> setMap = new java.util.LinkedHashMap<>();
                setMap.put("exercise", ex);
                setMap.put("reps", r);
                setMap.put("numSets", ns);
                if (kg > 0) setMap.put("weightKg", kg);
                var timedExercises = java.util.Set.of("HANG", "FARMERS_CARRY", "PLANK");
                if (timedExercises.contains(ex)) {
                    setMap.put("prepSecs", prep);
                    setMap.put("workSecs", work);
                }
                sets.add(setMap);
            }
            return objectMapper.writeValueAsString(Map.of("sets", sets));
        } catch (Exception e) { return "{}"; }
    }

    public String buildPlyoJson(String[] jumpTypes, String[] reps) {
        try {
            List<Map<String, Object>> sets = new ArrayList<>();
            for (int i = 0; i < jumpTypes.length; i++) {
                String jt = jumpTypes[i];
                if (jt == null || jt.isBlank()) continue;
                int r = (reps != null && reps.length > i && reps[i] != null && !reps[i].isBlank())
                        ? Integer.parseInt(reps[i]) : 0;
                sets.add(Map.of("jumpType", jt, "reps", r));
            }
            return objectMapper.writeValueAsString(Map.of("sets", sets));
        } catch (Exception e) { return "{}"; }
    }

    public String buildGenericJson(String[] descriptions, String[] reps) {
        try {
            List<Map<String, Object>> sets = new ArrayList<>();
            for (int i = 0; i < descriptions.length; i++) {
                String desc = descriptions[i];
                if (desc == null || desc.isBlank()) continue;
                int r = (reps != null && reps.length > i && reps[i] != null && !reps[i].isBlank())
                        ? Integer.parseInt(reps[i]) : 0;
                sets.add(Map.of("description", desc, "reps", r));
            }
            return objectMapper.writeValueAsString(Map.of("sets", sets));
        } catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseSetsJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }
}
