package com.foodie.service;

import com.foodie.controller.dto.VoiceParseResult;
import com.foodie.model.db.Meal;
import com.foodie.model.MealSlot;
import com.foodie.model.db.Product;
import com.foodie.model.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link VoiceLogService}.
 *
 * Tests the voice input parsing logic including:
 * - Meal slot detection (breakfast, lunch, dinner, snack)
 * - Quantity extraction from natural language
 * - Fuzzy matching of meals and products by name
 *
 * These tests ensure the voice logging feature continues to work correctly
 * as the codebase evolves.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoiceLogServiceTest {

    @Mock
    private MealService mealService;

    @Mock
    private ProductService productService;

    @Mock
    private FoodLogService foodLogService;

    @InjectMocks
    private VoiceLogService voiceLogService;

    private List<Meal> sampleMeals;
    private List<Product> sampleProducts;

    @BeforeEach
    void setUp() {
        sampleMeals = List.of(
            createMeal(1L, "Spaghetti Carbonara"),
            createMeal(2L, "Chicken Stir Fry"),
            createMeal(3L, "Greek Salad"),
            createMeal(4L, "Vegetable Soup"),
            createMeal(5L, "Chocolate Brownies"),
            createMeal(6L, "Banana Smoothie"),
            createMeal(7L, "Grilled Salmon with Rice")
        );

        sampleProducts = List.of(
            createProduct(1L, "Chicken Breast"),
            createProduct(2L, "Olive Oil"),
            createProduct(3L, "Brown Rice"),
            createProduct(4L, "Greek Yogurt"),
            createProduct(5L, "Banana"),
            createProduct(6L, "Eggs"),
            createProduct(7L, "Whole Milk")
        );
    }

    private Meal createMeal(Long id, String name) {
        Meal meal = new Meal();
        meal.setId(id);
        meal.setName(name);
        return meal;
    }

    private Product createProduct(Long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setUnit(Unit.KG);
        return product;
    }

    private VoiceParseResult parse(List<Meal> meals, List<Product> products, String text) {
        when(mealService.findAll()).thenReturn(meals);
        when(productService.findAll()).thenReturn(products);
        return voiceLogService.parse(text);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Nested
    @DisplayName("Meal Slot Detection")
    class MealSlotDetection {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
            "2 servings of banana smoothie for breakfast | BREAKFAST",
            "banana smoothie this morning               | BREAKFAST",
            "chicken stir fry for lunch                 | LUNCH",
            "greek salad at midday                      | LUNCH",
            "spaghetti carbonara for dinner             | DINNER",
            "grilled salmon this evening                | DINNER",
            "chocolate brownies as a snack              | SNACK",
            "greek salad                                |"          // no meal time -> null slot
        })
        void detectsSlot(String text, MealSlot expected) {
            assertEquals(expected, parse(sampleMeals, List.of(), text).slot());
        }
    }

    @Nested
    @DisplayName("Meal matching and servings")
    class MealMatching {

        @ParameterizedTest(name = "\"{0}\" -> {1} x{3}")
        @CsvSource(delimiter = '|', value = {
            "spaghetti carbonara                              | Spaghetti Carbonara | 1 | 1 |",           // exact, default 1 serving
            "chicken stir                                     | Chicken Stir Fry    | 2 | 1 |",           // partial / word overlap
            "GREEK SALAD                                      | Greek Salad         | 3 | 1 |",           // case-insensitive
            "spaghetti!! carbonara???                         | Spaghetti Carbonara | 1 | 1 |",           // special chars stripped
            "2 servings of spaghetti carbonara                | Spaghetti Carbonara | 1 | 2 |",           // integer servings
            "2 srv chicken stir fry                           | Chicken Stir Fry    | 2 | 2 |",           // 'srv' abbreviation
            "I had 2 servings of chicken stir fry for dinner  | Chicken Stir Fry    | 2 | 2 | DINNER",
            "1 portions of vegetable soup for lunch           | Vegetable Soup      | 4 | 1 | LUNCH"
        })
        void matchesMeal(String text, String mealName, Long mealId, String servings, MealSlot slot) {
            VoiceParseResult result = parse(sampleMeals, List.of(), text);

            assertTrue(result.parsed());
            assertEquals("meal", result.type());
            assertEquals(mealName, result.mealName());
            assertEquals(mealId, result.mealId());
            assertDecimal(servings, result.servings());
            assertEquals(slot, result.slot());
        }

        @Test
        @DisplayName("Should prefer meal match over product match")
        void shouldPreferMealOverProduct() {
            // "Banana Smoothie" (meal) vs "Banana" (product)
            VoiceParseResult result = parse(sampleMeals, sampleProducts, "banana smoothie");

            assertTrue(result.parsed());
            assertEquals("meal", result.type());
            assertEquals("Banana Smoothie", result.mealName());
        }
    }

    @Nested
    @DisplayName("Product matching and quantity")
    class ProductMatching {

        @ParameterizedTest(name = "\"{0}\" -> {1} {3}kg")
        @CsvSource(delimiter = '|', value = {
            "olive oil                           | Olive Oil      | 2 | 0.1  |",           // default 0.1 when no quantity
            "banana                              | Banana         | 5 | 0.1  |",
            "200g chicken                        | Chicken Breast | 1 | 0.2  |",           // partial name, g -> kg
            "200 grams chicken breast            | Chicken Breast | 1 | 0.2  |",
            "1 kg brown rice                     | Brown Rice     | 3 | 1    |",           // kg kept as-is
            "300 grams greek yogurt for breakfast| Greek Yogurt   | 4 | 0.3  | BREAKFAST",
            "greek yogurt 150g breakfast         | Greek Yogurt   | 4 | 0.15 | BREAKFAST"
        })
        void matchesProduct(String text, String productName, Long productId, String quantity, MealSlot slot) {
            VoiceParseResult result = parse(List.of(), sampleProducts, text);

            assertTrue(result.parsed());
            assertEquals("product", result.type());
            assertEquals(productName, result.productName());
            assertEquals(productId, result.productId());
            assertDecimal(quantity, result.quantity());
            assertEquals(slot, result.slot());
        }

        @Test
        @DisplayName("Should fall back to a product when no meal matches")
        void shouldMatchProductWhenNoMealMatches() {
            VoiceParseResult result = parse(sampleMeals, sampleProducts, "olive oil");

            assertTrue(result.parsed());
            assertEquals("product", result.type());
            assertEquals("Olive Oil", result.productName());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @ParameterizedTest(name = "throws for [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void shouldThrowForBlankText(String text) {
            var e = assertThrows(IllegalArgumentException.class, () -> voiceLogService.parse(text));

            assertEquals("No text provided", e.getMessage());
        }

        @ParameterizedTest(name = "\"{0}\" is not parsed")
        @ValueSource(strings = {
            "to",                               // words <= 2 chars are ignored
            "xyz",                              // score 3, below the threshold of 4
            "something completely unknown xyz"
        })
        void shouldNotParseUnmatchableInput(String text) {
            VoiceParseResult result = parse(sampleMeals, sampleProducts, text);

            assertFalse(result.parsed());
            assertNotNull(result.error());
        }

        @Test
        @DisplayName("Should handle empty meal and product lists")
        void shouldHandleEmptyLists() {
            assertFalse(parse(List.of(), List.of(), "chicken stir fry").parsed());
        }
    }
}
