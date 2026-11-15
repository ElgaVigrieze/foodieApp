package com.foodie.controller;

import com.foodie.model.MealCategory;
import com.foodie.service.MealService;
import com.foodie.service.ProductService;
import com.foodie.service.CurrentUserService;
import com.foodie.controller.form.MealForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
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
        model.addAttribute("archivedMeals", mealService.findArchived());
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
    public String save(@Valid @ModelAttribute MealForm form, RedirectAttributes redirectAttributes) {
        if (!form.hasIngredients()) {
            redirectAttributes.addFlashAttribute("error", "Please add at least one ingredient.");
            return form.id() == null ? "redirect:/meals/new" : "redirect:/meals/" + form.id() + "/edit";
        }

        if (form.id() == null) {
            mealService.createMeal(form.name(), form.category(), form.servings(), form.recipe(), form.recipeUrl(),
                    form.productIds(), form.quantities());
        } else {
            mealService.updateMeal(form.id(), form.name(), form.category(), form.servings(), form.recipe(),
                    form.recipeUrl(), form.productIds(), form.quantities());
        }

        redirectAttributes.addFlashAttribute("success", "Meal saved successfully!");
        return "redirect:/meals";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            mealService.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Meal deleted.");
        } catch (DataIntegrityViolationException e) {
            log.warn("Meal {} could not be deleted: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Cannot delete this meal — it is still referenced by a food log or meal plan. " +
                    "Archive it instead to hide it from lists.");
        }
        return "redirect:/meals";
    }

    @PostMapping("/{id}/toggle-favorite")
    public String toggleFavorite(@PathVariable Long id) {
        mealService.toggleFavorite(id);
        return "redirect:/meals";
    }

    @PostMapping("/{id}/toggle-archive")
    public String toggleArchive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        mealService.toggleArchive(id);
        redirectAttributes.addFlashAttribute("success", "Meal archive status updated.");
        return "redirect:/meals";
    }
}

