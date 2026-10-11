package com.foodie.service;

import com.foodie.model.db.AppUser;
import com.foodie.model.db.TrainingPlanEntry;
import com.foodie.model.db.Workout;
import com.foodie.repository.TrainingPlanEntryRepository;
import com.foodie.repository.WorkoutRepository;
import com.foodie.util.CalendarUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FitnessService {

    private final WorkoutRepository workoutRepository;
    private final TrainingPlanEntryRepository trainingPlanEntryRepository;
    private final CurrentUserService currentUserService;

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

    /**
     * Returns the Monday of the given week.
     * @deprecated Use {@link CalendarUtils#weekStart(LocalDate)} instead
     */
    @Deprecated
    public LocalDate weekStart(LocalDate date) {
        return CalendarUtils.weekStart(date);
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
    public void assignWorkout(Long workoutId, LocalDate date) {
        AppUser user = currentUserService.getCurrentUser();
        Workout workout = findById(workoutId);
        TrainingPlanEntry entry = TrainingPlanEntry.builder()
                .date(date)
                .workout(workout)
                .owner(user)
                .build();
        trainingPlanEntryRepository.save(entry);
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
}