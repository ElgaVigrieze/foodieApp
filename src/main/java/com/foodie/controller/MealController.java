package com.foodie.controller;

import com.foodie.model.MealCategory;
import com.foodie.service.MealService;
import com.foodie.service.ProductService;
import com.foodie.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/meals")
@RequiredArgsConstructor
public class MealController {

    private final MealService mealService;
    private final ProductService productService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("favorites", mealService.findFavorites());
        model.addAttribute("mealsByCategory", mealService.findAllGroupedByCategory());
        model.addAttribute("categories", MealCategory.values());
        model.addAttribute("isOwner", currentUserService.isOwner());
        return "meals/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("categories", MealCategory.values());
        model.addAttribute("products", productService.findAll());
        return "meals/form";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("meal", mealService.findById(id));
        model.addAttribute("categories", MealCategory.values());
        model.addAttribute("products", productService.findAll());
        return "meals/form";
    }

    @GetMapping("/{id}/copy")
    public String showCopyForm(@PathVariable Long id, Model model) {
        var original = mealService.findById(id);
        model.addAttribute("meal", original);
        model.addAttribute("isCopy", true);
        model.addAttribute("categories", MealCategory.values());
        model.addAttribute("products", productService.findAll());
        return "meals/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("meal", mealService.findById(id));
        return "meals/detail";
    }

    @PostMapping("/save")
    public String save(@RequestParam String name,
                       @RequestParam MealCategory category,
                       @RequestParam int servings,
                       @RequestParam(required = false) String recipe,
                       @RequestParam(required = false) String recipeUrl,
                       @RequestParam(required = false) Long id,
                       @RequestParam(name = "productIds", required = false) List<Long> productIds,
                       @RequestParam(name = "quantities", required = false) List<BigDecimal> quantities,
                       RedirectAttributes redirectAttributes) {
        if (productIds == null || quantities == null || productIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please add at least one ingredient.");
            return id == null ? "redirect:/meals/new" : "redirect:/meals/" + id + "/edit";
        }

        if (id == null) {
            mealService.createMeal(name, category, servings, recipe, recipeUrl, productIds, quantities);
        } else {
            mealService.updateMeal(id, name, category, servings, recipe, recipeUrl, productIds, quantities);
        }

        redirectAttributes.addFlashAttribute("success", "Meal saved successfully!");
        return "redirect:/meals";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        mealService.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Meal deleted.");
        return "redirect:/meals";
    }

    @PostMapping("/{id}/toggle-favorite")
    public String toggleFavorite(@PathVariable Long id) {
        mealService.toggleFavorite(id);
        return "redirect:/meals";
    }
}

