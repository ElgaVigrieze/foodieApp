package com.foodie.controller;

import com.foodie.model.MealSlot;
import com.foodie.service.FoodLogService;
import com.foodie.service.MealService;
import com.foodie.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/log")
@RequiredArgsConstructor
public class FoodLogController {

    private final FoodLogService foodLogService;
    private final MealService mealService;
    private final ProductService productService;

    @GetMapping
    public String show(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model) {
        LocalDate selectedDate = (date != null) ? date : LocalDate.now();
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("entries", foodLogService.findByDate(selectedDate));
        model.addAttribute("totals", foodLogService.getDailyTotals(selectedDate));
        model.addAttribute("dailyCost", foodLogService.getDailyCost(selectedDate));
        model.addAttribute("caloriesSpent", foodLogService.getCaloriesSpent(selectedDate));
        model.addAttribute("meals", mealService.findAll());
        model.addAttribute("products", productService.findAll());
        model.addAttribute("slots", MealSlot.values());
        model.addAttribute("slotTotals", foodLogService.getSlotTotals(selectedDate));
        model.addAttribute("prevDate", selectedDate.minusDays(1));
        model.addAttribute("nextDate", selectedDate.plusDays(1));
        return "log/daily";
    }

    @PostMapping("/add")
    public String addEntry(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam Long mealId,
                           @RequestParam BigDecimal servingsConsumed,
                           @RequestParam(required = false) MealSlot slot,
                           RedirectAttributes ra) {
        foodLogService.addEntry(date, mealId, servingsConsumed, slot);
        ra.addFlashAttribute("success", "Entry added!");
        return "redirect:/log?date=" + date;
    }

    @PostMapping("/add-product")
    public String addProductEntry(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam Long productId,
                                  @RequestParam BigDecimal quantity,
                                  @RequestParam(required = false) MealSlot slot,
                                  RedirectAttributes ra) {
        foodLogService.addProductEntry(date, productId, quantity, slot);
        ra.addFlashAttribute("success", "Product logged!");
        return "redirect:/log?date=" + date;
    }

    @PostMapping("/update-calories-spent")
    public String updateCaloriesSpent(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestParam BigDecimal caloriesSpent) {
        foodLogService.updateCaloriesSpent(date, caloriesSpent);
        return "redirect:/log?date=" + date;
    }

    
    @PostMapping("/{id}/update-servings")
    public String updateServings(@PathVariable Long id,
                                 @RequestParam BigDecimal servings,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        foodLogService.updateServings(id, servings);
        return "redirect:/log?date=" + date;
    }

    @PostMapping("/{id}/delete")
    public String deleteEntry(@PathVariable Long id,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              RedirectAttributes ra) {
        foodLogService.deleteEntry(id);
        ra.addFlashAttribute("success", "Entry deleted.");
        return "redirect:/log?date=" + date;
    }
}