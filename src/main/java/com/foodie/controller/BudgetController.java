package com.foodie.controller;

import com.foodie.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Controller
@RequestMapping("/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public String show(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       Model model) {
        LocalDate weekStart = (week != null) ? week : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        model.addAttribute("weeklyReport", budgetService.getWeeklyReport(weekStart));
        model.addAttribute("overallReport", budgetService.getOverallReport());
        model.addAttribute("allWeeks", budgetService.getAllWeeks());
        model.addAttribute("selectedWeek", weekStart);
        return "budget/view";
    }
}
