package com.foodie.controller;

import com.foodie.model.db.MealPlan;
import com.foodie.model.db.ShoppingListItem;
import com.foodie.service.CurrentUserService;
import com.foodie.service.MealPlanService;
import com.foodie.service.ProductService;
import com.foodie.service.ShoppingListService;
import com.foodie.controller.form.ShoppingListForms;
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
import java.util.List;

@Controller
@RequestMapping("/shopping-list")
@RequiredArgsConstructor
public class ShoppingListController {

    private final ShoppingListService shoppingListService;
    private final MealPlanService mealPlanService;
    private final ProductService productService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public String show(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       Model model) {
        LocalDate weekStart = (week != null) ? week : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        MealPlan plan = mealPlanService.getOrCreateForWeek(weekStart);
        List<ShoppingListItem> items = shoppingListService.getOrGenerate(weekStart, plan);

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("prevWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("items", items);
        model.addAttribute("totalCost", shoppingListService.getTotalCost(items));
        model.addAttribute("products", productService.findAll());
        model.addAttribute("currentWeekStart", LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        model.addAttribute("isOwner", currentUserService.isOwner());
        return "shopping-list/view";
    }

    @PostMapping("/regenerate")
    public String regenerate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                             RedirectAttributes redirectAttributes) {
        MealPlan plan = mealPlanService.getOrCreateForWeek(weekStart);
        shoppingListService.regenerate(weekStart, plan);
        redirectAttributes.addFlashAttribute("success", "Shopping list regenerated from meal plan!");
        return "redirect:/shopping-list?week=" + weekStart;
    }

    @PostMapping("/update-quantity")
    public String updateQuantity(@Valid @ModelAttribute ShoppingListForms.ItemQuantity form) {
        shoppingListService.updateQuantity(form.itemId(), form.quantity());
        return backToWeek(form.weekStart());
    }

    @PostMapping("/toggle-checked")
    public String toggleChecked(@Valid @ModelAttribute ShoppingListForms.ItemRef form) {
        shoppingListService.toggleChecked(form.itemId());
        return backToWeek(form.weekStart());
    }

    @PostMapping("/add-item")
    public String addItem(@Valid @ModelAttribute ShoppingListForms.AddItem form,
                          RedirectAttributes redirectAttributes) {
        shoppingListService.addItem(form.weekStart(), productService.findById(form.productId()), form.quantity());
        redirectAttributes.addFlashAttribute("success", "Item added!");
        return backToWeek(form.weekStart());
    }

    @PostMapping("/update-comment")
    public String updateComment(@Valid @ModelAttribute ShoppingListForms.ItemComment form) {
        shoppingListService.updateComment(form.itemId(), form.comment());
        return backToWeek(form.weekStart());
    }

    @PostMapping("/remove-item")
    public String removeItem(@Valid @ModelAttribute ShoppingListForms.ItemRef form,
                             RedirectAttributes redirectAttributes) {
        shoppingListService.removeItem(form.itemId());
        redirectAttributes.addFlashAttribute("success", "Item removed.");
        return backToWeek(form.weekStart());
    }

    private static String backToWeek(LocalDate weekStart) {
        return "redirect:/shopping-list?week=" + weekStart;
    }
}
