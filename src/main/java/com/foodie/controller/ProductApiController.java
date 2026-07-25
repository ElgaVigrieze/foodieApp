package com.foodie.controller;

import com.foodie.model.Product;
import com.foodie.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductApiController {

    private final ProductService productService;

    @GetMapping
    public List<Product> all() {
        return productService.findAll();
    }
}
