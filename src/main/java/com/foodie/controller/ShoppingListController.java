package com.foodie.controller;

import com.foodie.model.MealPlan;
import com.foodie.model.ShoppingListItem;
import com.foodie.service.CurrentUserService;
import com.foodie.service.MealPlanService;
import com.foodie.service.ProductService;
import com.foodie.service.ShoppingListService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
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
    public String updateQuantity(@RequestParam Long itemId,
                                 @RequestParam BigDecimal quantity,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                                 RedirectAttributes redirectAttributes) {
        shoppingListService.updateQuantity(itemId, quantity);
        return "redirect:/shopping-list?week=" + weekStart;
    }

    @PostMapping("/toggle-checked")
    public String toggleChecked(@RequestParam Long itemId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        shoppingListService.toggleChecked(itemId);
        return "redirect:/shopping-list?week=" + weekStart;
    }

    @PostMapping("/add-item")
    public String addItem(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                          @RequestParam Long productId,
                          @RequestParam BigDecimal quantity,
                          RedirectAttributes redirectAttributes) {
        shoppingListService.addItem(weekStart, productService.findById(productId), quantity);
        redirectAttributes.addFlashAttribute("success", "Item added!");
        return "redirect:/shopping-list?week=" + weekStart;
    }

    @PostMapping("/update-comment")
    public String updateComment(@RequestParam Long itemId,
                                @RequestParam(required = false) String comment,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        shoppingListService.updateComment(itemId, comment);
        return "redirect:/shopping-list?week=" + weekStart;
    }

    @PostMapping("/remove-item")
    public String removeItem(@RequestParam Long itemId,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
                             RedirectAttributes redirectAttributes) {
        shoppingListService.removeItem(itemId);
        redirectAttributes.addFlashAttribute("success", "Item removed.");
        return "redirect:/shopping-list?week=" + weekStart;
    }
}
