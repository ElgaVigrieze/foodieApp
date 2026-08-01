package com.foodie.controller;

import com.foodie.model.*;
import com.foodie.service.CurrentUserService;
import com.foodie.service.FitnessService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Controller
@RequestMapping("/fitness")
@RequiredArgsConstructor
public class FitnessController {

    private final FitnessService fitnessService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public String index() {
        return "redirect:/fitness/plan";
    }

    // ── Weekly Training Plan ─────────────────────────────────────────────────

    @GetMapping("/plan")
    public String plan(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       Model model) {
        LocalDate weekStart = fitnessService.weekStart(week != null ? week : LocalDate.now());
        Map<LocalDate, List<TrainingPlanEntry>> weeklyPlan = fitnessService.getWeeklyPlan(weekStart);

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("prevWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("weeklyPlan", weeklyPlan);
        model.addAttribute("days", DayOfWeek.values());
        model.addAttribute("allWorkouts", fitnessService.findAll());
        model.addAttribute("workoutTypes", com.foodie.model.WorkoutType.values());
        model.addAttribute("today", LocalDate.now());
        return "fitness/training-plan/weekly";
    }

    @PostMapping("/plan/assign")
    public String assignPlan(@RequestParam Long workoutId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        fitnessService.assignWorkout(workoutId, date);
        return "redirect:/fitness/plan?week=" + weekStart;
    }

    @PostMapping("/plan/remove/{entryId}")
    public String removePlanEntry(@PathVariable Long entryId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        fitnessService.removeEntry(entryId);
        return "redirect:/fitness/plan?week=" + weekStart;
    }

    @PostMapping("/plan/toggle/{entryId}")
    public String toggleEntry(@PathVariable Long entryId,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        fitnessService.toggleEntryCompleted(entryId);
        return "redirect:/fitness/plan?week=" + weekStart;
    }

    // ── Workout Library ──────────────────────────────────────────────────────

    @GetMapping("/workouts")
    public String list(Model model) {
        model.addAttribute("workouts", fitnessService.findAll());
        model.addAttribute("workoutTypes", WorkoutType.values());
        return "fitness/list";
    }

    @GetMapping("/workouts/new")
    public String newForm(@RequestParam(defaultValue = "STRENGTH") String type, Model model) {
        model.addAttribute("workout", Workout.builder().type(WorkoutType.valueOf(type)).build());
        model.addAttribute("workoutTypes", WorkoutType.values());
        model.addAttribute("isEdit", false);
        model.addAttribute("parsedSets", Map.of());
        return "fitness/form";
    }

    @GetMapping("/workouts/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Workout workout = fitnessService.findById(id);
        model.addAttribute("workout", workout);
        model.addAttribute("workoutTypes", WorkoutType.values());
        model.addAttribute("isEdit", true);
        model.addAttribute("parsedSets", fitnessService.parseSetsJson(workout.getSetsJson()));
        return "fitness/form";
    }

    @GetMapping("/workouts/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Workout workout = fitnessService.findById(id);
        model.addAttribute("workout", workout);
        model.addAttribute("parsedSets", fitnessService.parseSetsJson(workout.getSetsJson()));
        return "fitness/detail";
    }

    @PostMapping("/workouts/save")
    public String save(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam String type,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String youtubeUrl,
            @RequestParam(required = false) Integer totalMinutes,
            @RequestParam(required = false) Integer estimatedKcal,
            @RequestParam(required = false) Integer actualKcal,
            // RUNNING
            @RequestParam(required = false) String runningTempo,
            @RequestParam(required = false) Double runningDistanceKm,
            @RequestParam(required = false) Integer runningEstimatedKcal,
            // HIIT
            @RequestParam(required = false) String hiitType,
            @RequestParam(required = false) Integer hiitWarmupSecs,
            @RequestParam(required = false) Integer hiitCooldownSecs,
            @RequestParam(required = false) Integer hiitNumberOfSets,
            @RequestParam(required = false) Integer hiitHighIntensitySecs,
            @RequestParam(required = false) Integer hiitRestSecs,
            // STRENGTH
            @RequestParam(required = false) String[] strengthExercise,
            @RequestParam(required = false) String[] strengthReps,
            @RequestParam(required = false) String[] strengthNumSets,
            @RequestParam(required = false) String[] strengthWeightKg,
            // PLYO
            @RequestParam(required = false) String[] plyoJumpType,
            @RequestParam(required = false) String[] plyoReps,
            // PILATES / REHAB
            @RequestParam(required = false) String[] genericDescription,
            @RequestParam(required = false) String[] genericReps,
            RedirectAttributes ra) {

        WorkoutType workoutType = WorkoutType.valueOf(type);
        String setsJson = switch (workoutType) {
            case RUNNING, WALKING -> fitnessService.buildRunningJson(runningTempo, runningDistanceKm, runningEstimatedKcal);
            case HIIT -> fitnessService.buildHiitJson(hiitType, hiitWarmupSecs, hiitCooldownSecs,
                    hiitNumberOfSets, hiitHighIntensitySecs, hiitRestSecs);
            case STRENGTH -> fitnessService.buildStrengthJson(
                    strengthExercise != null ? strengthExercise : new String[0],
                    strengthReps != null ? strengthReps : new String[0],
                    strengthNumSets != null ? strengthNumSets : new String[0],
                    strengthWeightKg != null ? strengthWeightKg : new String[0]);
            case PLYO -> fitnessService.buildPlyoJson(
                    plyoJumpType != null ? plyoJumpType : new String[0],
                    plyoReps != null ? plyoReps : new String[0]);
            case PILATES, REHAB -> fitnessService.buildGenericJson(
                    genericDescription != null ? genericDescription : new String[0],
                    genericReps != null ? genericReps : new String[0]);
        };

        Workout workout;
        if (id != null) {
            workout = fitnessService.findById(id);
            workout.setName(name);
            workout.setNotes(notes);
            workout.setYoutubeUrl(youtubeUrl);
            workout.setTotalMinutes(totalMinutes);
            workout.setEstimatedKcal(estimatedKcal);
            workout.setActualKcal(actualKcal);
            workout.setSetsJson(setsJson);
        } else {
            workout = Workout.builder()
                    .name(name).type(workoutType)
                    .notes(notes).youtubeUrl(youtubeUrl)
                    .totalMinutes(totalMinutes).setsJson(setsJson)
                    .owner(currentUserService.getCurrentUser())
                    .build();
        }

        Workout saved = fitnessService.save(workout);
        ra.addFlashAttribute("success", "Workout saved!");
        return "redirect:/fitness/workouts/" + saved.getId();
    }

    @PostMapping("/workouts/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        fitnessService.delete(id);
        ra.addFlashAttribute("success", "Workout deleted.");
        return "redirect:/fitness/workouts";
    }
}
