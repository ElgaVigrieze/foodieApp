package com.foodie.repository;

import com.foodie.model.ShoppingListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {
    List<ShoppingListItem> findByWeekStartOrderByProductNameAsc(LocalDate weekStart);
    void deleteByWeekStart(LocalDate weekStart);
}
