package com.foodie.controller;

import com.foodie.model.MealPlan;
import com.foodie.model.MealSlot;
import com.foodie.service.MealPlanService;
import com.foodie.service.MealService;
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
        return "meal-plan/weekly";
    }

    @PostMapping("/add")
    public String addEntry(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                           @RequestParam DayOfWeek day,
                           @RequestParam MealSlot slot,
                           @RequestParam Long mealId,
                           @RequestParam(defaultValue = "1") Integer servings,
                           RedirectAttributes redirectAttributes) {
        mealPlanService.addEntry(weekStart, day, slot, mealId, servings);
        redirectAttributes.addFlashAttribute("success", "Meal added to plan!");
        return "redirect:/meal-plan?week=" + weekStart;
    }

    @PostMapping("/remove")
    public String removeEntry(@RequestParam Long planId,
                              @RequestParam Long entryId,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                              RedirectAttributes redirectAttributes) {
        mealPlanService.removeEntry(planId, entryId);
        redirectAttributes.addFlashAttribute("success", "Entry removed.");
        return "redirect:/meal-plan?week=" + weekStart;
    }

    @PostMapping("/move")
    public String moveEntry(@RequestParam Long planId,
                            @RequestParam Long entryId,
                            @RequestParam DayOfWeek newDay,
                            @RequestParam MealSlot newSlot,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        mealPlanService.moveEntry(planId, entryId, newDay, newSlot);
        return "redirect:/meal-plan?week=" + weekStart;
    }

    @PostMapping("/update-servings")
    public String updateServings(@RequestParam Long planId,
                                 @RequestParam Long entryId,
                                 @RequestParam Integer servings,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        mealPlanService.updateEntryServings(planId, entryId, servings);
        return "redirect:/meal-plan?week=" + weekStart;
    }

    @PostMapping("/copy")
    public String copyEntry(@RequestParam Long planId,
                            @RequestParam Long entryId,
                            @RequestParam DayOfWeek newDay,
                            @RequestParam MealSlot newSlot,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        mealPlanService.copyEntry(planId, entryId, newDay, newSlot);
        return "redirect:/meal-plan?week=" + weekStart;
    }

    @PostMapping("/toggle-freeze")
    public String toggleFreeze(@RequestParam Long planId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                               RedirectAttributes redirectAttributes) {
        mealPlanService.toggleFreeze(planId);
        redirectAttributes.addFlashAttribute("success", "Plan status updated.");
        return "redirect:/meal-plan?week=" + weekStart;
    }

    @PostMapping("/toggle-prepared")
    public String togglePrepared(@RequestParam Long planId,
                                 @RequestParam Long entryId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        mealPlanService.togglePrepared(planId, entryId);
        return "redirect:/meal-plan?week=" + weekStart;
    }
}
