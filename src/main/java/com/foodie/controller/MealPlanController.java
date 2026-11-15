package com.foodie.controller;

import com.foodie.controller.form.MealPlanForms;
import com.foodie.model.db.MealPlan;
import com.foodie.model.MealSlot;
import com.foodie.service.MealPlanService;
import com.foodie.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Controller
@RequestMapping("/meal-plan")
@RequiredArgsConstructor
public class MealPlanController {

    private final MealPlanService mealPlanService;
    private final MealService mealService;

    @GetMapping
    public String show(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       Model model) {
        LocalDate weekStart = (week != null) ? week : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        MealPlan plan = mealPlanService.getOrCreateForWeek(weekStart);

        var shoppingList = mealPlanService.getShoppingList(plan);

        model.addAttribute("plan", plan);
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("prevWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("days", DayOfWeek.values());
        model.addAttribute("slots", MealSlot.values());
        model.addAttribute("meals", mealService.findAll());
        model.addAttribute("shoppingList", shoppingList);
        model.addAttribute("shoppingTotal", mealPlanService.getShoppingListTotalCost(shoppingList));
        model.addAttribute("dailyCalories", mealPlanService.getDailyCalories(plan));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("currentWeekStart", LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        return "meal-plan/weekly";
    }

    @PostMapping("/add")
    public String addEntry(@Valid @ModelAttribute MealPlanForms.AddEntry form,
                           RedirectAttributes redirectAttributes) {
        mealPlanService.addEntry(form.weekStart(), form.day(), form.slot(), form.mealId(), form.servings());
        redirectAttributes.addFlashAttribute("success", "Meal added to plan!");
        return backToWeek(form.weekStart());
    }

    @PostMapping("/remove")
    public String removeEntry(@Valid @ModelAttribute MealPlanForms.EntryRef form,
                              RedirectAttributes redirectAttributes) {
        mealPlanService.removeEntry(form.planId(), form.entryId());
        redirectAttributes.addFlashAttribute("success", "Entry removed.");
        return backToWeek(form.weekStart());
    }

    @PostMapping("/move")
    public String moveEntry(@Valid @ModelAttribute MealPlanForms.EntryTarget form) {
        mealPlanService.moveEntry(form.planId(), form.entryId(), form.newDay(), form.newSlot());
        return backToWeek(form.weekStart());
    }

    @PostMapping("/update-servings")
    public String updateServings(@Valid @ModelAttribute MealPlanForms.EntryServings form) {
        mealPlanService.updateEntryServings(form.planId(), form.entryId(), form.servings());
        return backToWeek(form.weekStart());
    }

    @PostMapping("/copy")
    public String copyEntry(@Valid @ModelAttribute MealPlanForms.EntryTarget form) {
        mealPlanService.copyEntry(form.planId(), form.entryId(), form.newDay(), form.newSlot());
        return backToWeek(form.weekStart());
    }

    @PostMapping("/toggle-freeze")
    public String toggleFreeze(@Valid @ModelAttribute MealPlanForms.PlanRef form,
                               RedirectAttributes redirectAttributes) {
        mealPlanService.toggleFreeze(form.planId());
        redirectAttributes.addFlashAttribute("success", "Plan status updated.");
        return backToWeek(form.weekStart());
    }

    @PostMapping("/toggle-prepared")
    public String togglePrepared(@Valid @ModelAttribute MealPlanForms.EntryRef form) {
        mealPlanService.togglePrepared(form.planId(), form.entryId());
        return backToWeek(form.weekStart());
    }

    private static String backToWeek(LocalDate weekStart) {
        return "redirect:/meal-plan?week=" + weekStart;
    }
}
