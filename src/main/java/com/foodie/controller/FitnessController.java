package com.foodie.controller;

import com.foodie.controller.dto.WorkoutSaveRequest;
import com.foodie.model.*;
import com.foodie.model.db.TrainingPlanEntry;
import com.foodie.model.db.Workout;
import com.foodie.service.CurrentUserService;
import com.foodie.service.FitnessService;
import com.foodie.util.CalendarUtils;
import com.foodie.util.WorkoutJsonBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
        LocalDate weekStart = CalendarUtils.weekStart(week != null ? week : LocalDate.now());
        Map<LocalDate, List<TrainingPlanEntry>> weeklyPlan = fitnessService.getWeeklyPlan(weekStart);

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("prevWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("weeklyPlan", weeklyPlan);
        model.addAttribute("days", DayOfWeek.values());
        model.addAttribute("allWorkouts", fitnessService.findAll());
        model.addAttribute("workoutTypes", WorkoutType.values());
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
        model.addAttribute("parsedSets", WorkoutJsonBuilder.parseSetsJson(workout.getSetsJson()));
        return "fitness/form";
    }

    @GetMapping("/workouts/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Workout workout = fitnessService.findById(id);
        model.addAttribute("workout", workout);
        model.addAttribute("parsedSets", WorkoutJsonBuilder.parseSetsJson(workout.getSetsJson()));
        return "fitness/detail";
    }

    @PostMapping("/workouts/save")
    public String save(@ModelAttribute WorkoutSaveRequest request, RedirectAttributes ra) {
        String setsJson = request.buildSetsJson();

        Workout workout;
        if (request.id() != null) {
            workout = fitnessService.findById(request.id());
            workout.setName(request.name());
            workout.setNotes(request.notes());
            workout.setYoutubeUrl(request.youtubeUrl());
            workout.setTotalMinutes(request.totalMinutes());
            workout.setEstimatedKcal(request.estimatedKcal());
            workout.setSetsJson(setsJson);
        } else {
            workout = Workout.builder()
                    .name(request.name())
                    .type(request.getWorkoutType())
                    .notes(request.notes())
                    .youtubeUrl(request.youtubeUrl())
                    .totalMinutes(request.totalMinutes())
                    .setsJson(setsJson)
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