package com.foodie.controller;

import com.foodie.service.FoodLogService;
import com.foodie.service.MealService;
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

    @GetMapping
    public String show(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model) {
        LocalDate selectedDate = (date != null) ? date : LocalDate.now();
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("entries", foodLogService.findByDate(selectedDate));
        model.addAttribute("totals", foodLogService.getDailyTotals(selectedDate));
        model.addAttribute("meals", mealService.findAll());
        model.addAttribute("prevDate", selectedDate.minusDays(1));
        model.addAttribute("nextDate", selectedDate.plusDays(1));
        return "log/daily";
    }

    @PostMapping("/add")
    public String addEntry(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam Long mealId,
                           @RequestParam BigDecimal servingsConsumed,
                           RedirectAttributes redirectAttributes) {
        foodLogService.addEntry(date, mealId, servingsConsumed);
        redirectAttributes.addFlashAttribute("success", "Entry added!");
        return "redirect:/log?date=" + date;
    }

    @PostMapping("/{id}/delete")
    public String deleteEntry(@PathVariable Long id,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              RedirectAttributes redirectAttributes) {
        foodLogService.deleteEntry(id);
        redirectAttributes.addFlashAttribute("success", "Entry deleted.");
        return "redirect:/log?date=" + date;
    }
}
