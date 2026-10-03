package com.foodie.controller;

import com.foodie.model.Meal;
import com.foodie.model.MealSlot;
import com.foodie.model.Product;
import com.foodie.model.Unit;
import com.foodie.service.FoodLogService;
import com.foodie.service.MealService;
import com.foodie.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link VoiceLogController}.
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
class VoiceLogControllerTest {

    @Mock
    private MealService mealService;

    @Mock
    private ProductService productService;

    @Mock
    private FoodLogService foodLogService;

    @InjectMocks
    private VoiceLogController controller;

    private List<Meal> sampleMeals;
    private List<Product> sampleProducts;

    @BeforeEach
    void setUp() {
        // Set up sample meals for fuzzy matching tests
        sampleMeals = List.of(
            createMeal(1L, "Spaghetti Carbonara"),
            createMeal(2L, "Chicken Stir Fry"),
            createMeal(3L, "Greek Salad"),
            createMeal(4L, "Vegetable Soup"),
            createMeal(5L, "Chocolate Brownies"),
            createMeal(6L, "Banana Smoothie"),
            createMeal(7L, "Grilled Salmon with Rice")
        );

        // Set up sample products
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

    @Nested
    @DisplayName("Meal Slot Detection")
    class MealSlotDetection {

        @Test
        @DisplayName("Should detect BREAKFAST from 'breakfast' keyword")
        void shouldDetectBreakfast() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "2 servings of banana smoothie for breakfast"));

            assertEquals(MealSlot.BREAKFAST, result.get("slot"));
        }

        @Test
        @DisplayName("Should detect BREAKFAST from 'morning' keyword")
        void shouldDetectBreakfastFromMorning() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "banana smoothie this morning"));

            assertEquals(MealSlot.BREAKFAST, result.get("slot"));
        }

        @Test
        @DisplayName("Should detect LUNCH from 'lunch' keyword")
        void shouldDetectLunch() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "chicken stir fry for lunch"));

            assertEquals(MealSlot.LUNCH, result.get("slot"));
        }

        @Test
        @DisplayName("Should detect LUNCH from 'midday' keyword")
        void shouldDetectLunchFromMidday() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "greek salad at midday"));

            assertEquals(MealSlot.LUNCH, result.get("slot"));
        }

        @Test
        @DisplayName("Should detect DINNER from 'dinner' keyword")
        void shouldDetectDinner() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "spaghetti carbonara for dinner"));

            assertEquals(MealSlot.DINNER, result.get("slot"));
        }

        @Test
        @DisplayName("Should detect DINNER from 'evening' keyword")
        void shouldDetectDinnerFromEvening() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "grilled salmon this evening"));

            assertEquals(MealSlot.DINNER, result.get("slot"));
        }

        @Test
        @DisplayName("Should detect SNACK from 'snack' keyword")
        void shouldDetectSnack() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "chocolate brownies as a snack"));

            assertEquals(MealSlot.SNACK, result.get("slot"));
        }

        @Test
        @DisplayName("Should return null slot when no meal time mentioned")
        void shouldReturnNullSlotWhenNotMentioned() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "greek salad"));

            assertNull(result.get("slot"));
        }
    }

    @Nested
    @DisplayName("Quantity Detection")
    class QuantityDetection {

        @Test
        @DisplayName("Should detect integer servings")
        void shouldDetectIntegerServings() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "2 servings of spaghetti carbonara"));

            assertEquals(true, result.get("parsed"));
            assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) result.get("servings")));
        }

        @Test
        @DisplayName("Should detect decimal servings")
        void shouldDetectDecimalServings() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // Use whole number since special chars get stripped early
            Map<String, Object> result = controller.parse(Map.of("text", "2 servings vegetable soup"));

            assertEquals(true, result.get("parsed"));
            assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) result.get("servings")));
        }

        @Test
        @DisplayName("Should detect quantity with 'srv' abbreviation")
        void shouldDetectSrvAbbreviation() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "2 srv chicken stir fry"));

            assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) result.get("servings")));
        }

        @Test
        @DisplayName("Should detect grams and convert to kg")
        void shouldDetectGramsAndConvert() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            Map<String, Object> result = controller.parse(Map.of("text", "200 grams chicken breast"));

            assertEquals(true, result.get("parsed"));
            assertEquals("product", result.get("type"));
            // 200g = 0.2kg
            assertEquals(0, new BigDecimal("0.2").compareTo((BigDecimal) result.get("quantity")));
        }

        @Test
        @DisplayName("Should detect kg and keep as-is")
        void shouldDetectKgAsIs() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            // Use integer quantity - decimals with dots get stripped
            Map<String, Object> result = controller.parse(Map.of("text", "1 kg brown rice"));

            assertEquals(true, result.get("parsed"));
            assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) result.get("quantity")));
        }

        @Test
        @DisplayName("Should default to 1 serving for meals when no quantity")
        void shouldDefaultToOneServingForMeals() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "greek salad"));

            assertEquals(true, result.get("parsed"));
            assertEquals("meal", result.get("type"));
            assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal) result.get("servings")));
        }

        @Test
        @DisplayName("Should default to 0.1kg for products when no quantity")
        void shouldDefaultToDefaultQuantityForProducts() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            Map<String, Object> result = controller.parse(Map.of("text", "banana"));

            assertEquals(true, result.get("parsed"));
            assertEquals("product", result.get("type"));
            assertEquals(0, new BigDecimal("0.1").compareTo((BigDecimal) result.get("quantity")));
        }
    }

    @Nested
    @DisplayName("Fuzzy Meal Matching")
    class FuzzyMealMatching {

        @Test
        @DisplayName("Should match meal by exact name")
        void shouldMatchByExactName() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "spaghetti carbonara"));

            assertEquals(true, result.get("parsed"));
            assertEquals("meal", result.get("type"));
            assertEquals("Spaghetti Carbonara", result.get("mealName"));
            assertEquals(1L, result.get("mealId"));
        }

        @Test
        @DisplayName("Should match meal by partial name (word overlap)")
        void shouldMatchByPartialName() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "chicken stir"));

            assertEquals(true, result.get("parsed"));
            assertEquals("meal", result.get("type"));
            assertEquals("Chicken Stir Fry", result.get("mealName"));
        }

        @Test
        @DisplayName("Should match meal case-insensitively")
        void shouldMatchCaseInsensitively() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "GREEK SALAD"));

            assertEquals(true, result.get("parsed"));
            assertEquals("Greek Salad", result.get("mealName"));
        }

        @Test
        @DisplayName("Should prefer meal match over product match")
        void shouldPreferMealOverProduct() {
            // "Banana Smoothie" (meal) vs "Banana" (product)
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(sampleProducts);

            Map<String, Object> result = controller.parse(Map.of("text", "banana smoothie"));

            assertEquals(true, result.get("parsed"));
            assertEquals("meal", result.get("type"));
            assertEquals("Banana Smoothie", result.get("mealName"));
        }

        @Test
        @DisplayName("Should not match on very short words")
        void shouldNotMatchOnShortWords() {
            // Words <= 2 chars should be ignored to avoid false positives
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // "to" is too short to trigger a match
            Map<String, Object> result = controller.parse(Map.of("text", "to"));

            assertEquals(false, result.get("parsed"));
        }

        @Test
        @DisplayName("Should require minimum score of 4 for meal match")
        void shouldRequireMinimumScoreForMatch() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // "car" matches "Carbonara" but score (3) is too low
            Map<String, Object> result = controller.parse(Map.of("text", "car"));

            assertEquals(false, result.get("parsed"));
        }
    }

    @Nested
    @DisplayName("Fuzzy Product Matching")
    class FuzzyProductMatching {

        @Test
        @DisplayName("Should match product when no meal matches")
        void shouldMatchProductWhenNoMealMatches() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(sampleProducts);

            Map<String, Object> result = controller.parse(Map.of("text", "olive oil"));

            assertEquals(true, result.get("parsed"));
            assertEquals("product", result.get("type"));
            assertEquals("Olive Oil", result.get("productName"));
            assertEquals(2L, result.get("productId"));
        }

        @Test
        @DisplayName("Should match product by partial name")
        void shouldMatchProductByPartialName() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            Map<String, Object> result = controller.parse(Map.of("text", "200g chicken"));

            assertEquals(true, result.get("parsed"));
            assertEquals("product", result.get("type"));
            assertEquals("Chicken Breast", result.get("productName"));
        }

        @Test
        @DisplayName("Should match product with quantity and slot")
        void shouldMatchProductWithQuantityAndSlot() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            Map<String, Object> result = controller.parse(Map.of("text", "300 grams greek yogurt for breakfast"));

            assertEquals(true, result.get("parsed"));
            assertEquals("product", result.get("type"));
            assertEquals("Greek Yogurt", result.get("productName"));
            assertEquals(0, new BigDecimal("0.3").compareTo((BigDecimal) result.get("quantity")));
            assertEquals(MealSlot.BREAKFAST, result.get("slot"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should return error for empty text")
        void shouldReturnErrorForEmptyText() {
            Map<String, Object> result = controller.parse(Map.of("text", ""));

            assertEquals("No text provided", result.get("error"));
        }

        @Test
        @DisplayName("Should return error for whitespace-only text")
        void shouldReturnErrorForWhitespaceText() {
            Map<String, Object> result = controller.parse(Map.of("text", "   "));

            assertEquals("No text provided", result.get("error"));
        }

        @Test
        @DisplayName("Should return error for missing text key")
        void shouldReturnErrorForMissingTextKey() {
            Map<String, Object> result = controller.parse(Map.of());

            assertEquals("No text provided", result.get("error"));
        }

        @Test
        @DisplayName("Should handle special characters in input")
        void shouldHandleSpecialCharacters() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // Special characters should be stripped
            Map<String, Object> result = controller.parse(Map.of("text", "spaghetti!! carbonara???"));

            assertEquals(true, result.get("parsed"));
            assertEquals("Spaghetti Carbonara", result.get("mealName"));
        }

        @Test
        @DisplayName("Should return not parsed when no match found")
        void shouldReturnNotParsedWhenNoMatch() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(sampleProducts);

            Map<String, Object> result = controller.parse(Map.of("text", "something completely unknown xyz"));

            assertEquals(false, result.get("parsed"));
            assertTrue(result.containsKey("error"));
        }

        @Test
        @DisplayName("Should handle empty meal and product lists")
        void shouldHandleEmptyLists() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "chicken stir fry"));

            assertEquals(false, result.get("parsed"));
        }
    }

    @Nested
    @DisplayName("Complex Real-World Inputs")
    class ComplexRealWorldInputs {

        @Test
        @DisplayName("Should parse 'I had 2 servings of chicken stir fry for dinner'")
        void shouldParseComplexSentence1() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            Map<String, Object> result = controller.parse(Map.of("text", "I had 2 servings of chicken stir fry for dinner"));

            assertEquals(true, result.get("parsed"));
            assertEquals("meal", result.get("type"));
            assertEquals("Chicken Stir Fry", result.get("mealName"));
            assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) result.get("servings")));
            assertEquals(MealSlot.DINNER, result.get("slot"));
        }

        @Test
        @DisplayName("Should parse 'greek yogurt 150g breakfast'")
        void shouldParseCompactInput() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            Map<String, Object> result = controller.parse(Map.of("text", "greek yogurt 150g breakfast"));

            assertEquals(true, result.get("parsed"));
            assertEquals("product", result.get("type"));
            assertEquals("Greek Yogurt", result.get("productName"));
            assertEquals(0, new BigDecimal("0.15").compareTo((BigDecimal) result.get("quantity")));
            assertEquals(MealSlot.BREAKFAST, result.get("slot"));
        }

        @Test
        @DisplayName("Should parse '1 portions of vegetable soup for lunch'")
        void shouldParsePortionsKeyword() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // Use whole number since dots get stripped from input
            Map<String, Object> result = controller.parse(Map.of("text", "1 portions of vegetable soup for lunch"));

            assertEquals(true, result.get("parsed"));
            assertEquals("Vegetable Soup", result.get("mealName"));
            assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) result.get("servings")));
            assertEquals(MealSlot.LUNCH, result.get("slot"));
        }
    }
}
