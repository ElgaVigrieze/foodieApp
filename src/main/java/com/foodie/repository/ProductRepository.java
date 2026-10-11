package com.foodie.repository;

import com.foodie.model.db.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByNameAsc();
    List<Product> findByHouseholdIdOrHouseholdIdIsNullOrderByNameAsc(Long householdId);
    java.util.Optional<Product> findByBarcode(String barcode);
    java.util.Optional<Product> findByBarcodeAndHouseholdId(String barcode, Long householdId);
}