package com.foodie.controller;

import com.foodie.controller.form.ProductForm;
import com.foodie.model.db.Product;
import com.foodie.model.ProductCategory;
import com.foodie.model.Unit;
import com.foodie.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("productsByCategory", productService.findAllGroupedByCategory());
        return "products/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("units", Unit.values());
        model.addAttribute("categories", ProductCategory.values());
        return "products/form";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("product", productService.findById(id));
        model.addAttribute("units", Unit.values());
        model.addAttribute("categories", ProductCategory.values());
        return "products/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("product") ProductForm form,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("units", Unit.values());
            model.addAttribute("categories", ProductCategory.values());
            return "products/form";
        }
        // Editing loads the existing product through the service, which verifies household access.
        Product product = form.id() == null ? new Product() : productService.findById(form.id());
        apply(form, product);
        productService.save(product);
        redirectAttributes.addFlashAttribute("success", "Product saved successfully!");
        return "redirect:/products";
    }

    private static void apply(ProductForm form, Product product) {
        product.setName(form.name());
        product.setCategory(form.category());
        product.setUnit(form.unit());
        product.setPricePerUnit(form.pricePerUnit());
        product.setCalories(form.calories());
        product.setProtein(form.protein());
        product.setCarbs(form.carbs());
        product.setFat(form.fat());
        product.setFiber(form.fiber());
        product.setSugar(form.sugar());
        product.setBarcode(form.barcode());
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productService.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Product deleted.");
        return "redirect:/products";
    }
}