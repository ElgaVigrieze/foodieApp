package com.foodie.repository;

import com.foodie.model.db.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every product belongs to one household and is visible only to its members. */
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByHouseholdIdOrderByNameAsc(Long householdId);

    Optional<Product> findFirstByBarcodeAndHouseholdId(String barcode, Long householdId);
}
