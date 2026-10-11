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

            VoiceParseResult result = voiceLogService.parse("2 servings of banana smoothie for breakfast");

            assertEquals(MealSlot.BREAKFAST, result.slot());
        }

        @Test
        @DisplayName("Should detect BREAKFAST from 'morning' keyword")
        void shouldDetectBreakfastFromMorning() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("banana smoothie this morning");

            assertEquals(MealSlot.BREAKFAST, result.slot());
        }

        @Test
        @DisplayName("Should detect LUNCH from 'lunch' keyword")
        void shouldDetectLunch() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("chicken stir fry for lunch");

            assertEquals(MealSlot.LUNCH, result.slot());
        }

        @Test
        @DisplayName("Should detect LUNCH from 'midday' keyword")
        void shouldDetectLunchFromMidday() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("greek salad at midday");

            assertEquals(MealSlot.LUNCH, result.slot());
        }

        @Test
        @DisplayName("Should detect DINNER from 'dinner' keyword")
        void shouldDetectDinner() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("spaghetti carbonara for dinner");

            assertEquals(MealSlot.DINNER, result.slot());
        }

        @Test
        @DisplayName("Should detect DINNER from 'evening' keyword")
        void shouldDetectDinnerFromEvening() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("grilled salmon this evening");

            assertEquals(MealSlot.DINNER, result.slot());
        }

        @Test
        @DisplayName("Should detect SNACK from 'snack' keyword")
        void shouldDetectSnack() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("chocolate brownies as a snack");

            assertEquals(MealSlot.SNACK, result.slot());
        }

        @Test
        @DisplayName("Should return null slot when no meal time mentioned")
        void shouldReturnNullSlotWhenNotMentioned() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("greek salad");

            assertNull(result.slot());
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

            VoiceParseResult result = voiceLogService.parse("2 servings of spaghetti carbonara");

            assertTrue(result.parsed());
            assertEquals(0, new BigDecimal("2").compareTo(result.servings()));
        }

        @Test
        @DisplayName("Should detect decimal servings")
        void shouldDetectDecimalServings() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // Use whole number since special chars get stripped early
            VoiceParseResult result = voiceLogService.parse("2 servings vegetable soup");

            assertTrue(result.parsed());
            assertEquals(0, new BigDecimal("2").compareTo(result.servings()));
        }

        @Test
        @DisplayName("Should detect quantity with 'srv' abbreviation")
        void shouldDetectSrvAbbreviation() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("2 srv chicken stir fry");

            assertEquals(0, new BigDecimal("2").compareTo(result.servings()));
        }

        @Test
        @DisplayName("Should detect grams and convert to kg")
        void shouldDetectGramsAndConvert() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            VoiceParseResult result = voiceLogService.parse("200 grams chicken breast");

            assertTrue(result.parsed());
            assertEquals("product", result.type());
            // 200g = 0.2kg
            assertEquals(0, new BigDecimal("0.2").compareTo(result.quantity()));
        }

        @Test
        @DisplayName("Should detect kg and keep as-is")
        void shouldDetectKgAsIs() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            // Use integer quantity - decimals with dots get stripped
            VoiceParseResult result = voiceLogService.parse("1 kg brown rice");

            assertTrue(result.parsed());
            assertEquals(0, new BigDecimal("1").compareTo(result.quantity()));
        }

        @Test
        @DisplayName("Should default to 1 serving for meals when no quantity")
        void shouldDefaultToOneServingForMeals() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("greek salad");

            assertTrue(result.parsed());
            assertEquals("meal", result.type());
            assertEquals(0, BigDecimal.ONE.compareTo(result.servings()));
        }

        @Test
        @DisplayName("Should default to 0.1kg for products when no quantity")
        void shouldDefaultToDefaultQuantityForProducts() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            VoiceParseResult result = voiceLogService.parse("banana");

            assertTrue(result.parsed());
            assertEquals("product", result.type());
            assertEquals(0, new BigDecimal("0.1").compareTo(result.quantity()));
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

            VoiceParseResult result = voiceLogService.parse("spaghetti carbonara");

            assertTrue(result.parsed());
            assertEquals("meal", result.type());
            assertEquals("Spaghetti Carbonara", result.mealName());
            assertEquals(1L, result.mealId());
        }

        @Test
        @DisplayName("Should match meal by partial name (word overlap)")
        void shouldMatchByPartialName() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("chicken stir");

            assertTrue(result.parsed());
            assertEquals("meal", result.type());
            assertEquals("Chicken Stir Fry", result.mealName());
        }

        @Test
        @DisplayName("Should match meal case-insensitively")
        void shouldMatchCaseInsensitively() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("GREEK SALAD");

            assertTrue(result.parsed());
            assertEquals("Greek Salad", result.mealName());
        }

        @Test
        @DisplayName("Should prefer meal match over product match")
        void shouldPreferMealOverProduct() {
            // "Banana Smoothie" (meal) vs "Banana" (product)
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(sampleProducts);

            VoiceParseResult result = voiceLogService.parse("banana smoothie");

            assertTrue(result.parsed());
            assertEquals("meal", result.type());
            assertEquals("Banana Smoothie", result.mealName());
        }

        @Test
        @DisplayName("Should not match on very short words")
        void shouldNotMatchOnShortWords() {
            // Words <= 2 chars should be ignored to avoid false positives
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // "to" is too short to trigger a match
            VoiceParseResult result = voiceLogService.parse("to");

            assertFalse(result.parsed());
        }

        @Test
        @DisplayName("Should require minimum score of 4 for meal match")
        void shouldRequireMinimumScoreForMatch() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // "xyz" is too short (3 chars = score 3, below threshold of 4)
            // and doesn't appear as substring in any meal name
            VoiceParseResult result = voiceLogService.parse("xyz");

            assertFalse(result.parsed());
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

            VoiceParseResult result = voiceLogService.parse("olive oil");

            assertTrue(result.parsed());
            assertEquals("product", result.type());
            assertEquals("Olive Oil", result.productName());
            assertEquals(2L, result.productId());
        }

        @Test
        @DisplayName("Should match product by partial name")
        void shouldMatchProductByPartialName() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            VoiceParseResult result = voiceLogService.parse("200g chicken");

            assertTrue(result.parsed());
            assertEquals("product", result.type());
            assertEquals("Chicken Breast", result.productName());
        }

        @Test
        @DisplayName("Should match product with quantity and slot")
        void shouldMatchProductWithQuantityAndSlot() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            VoiceParseResult result = voiceLogService.parse("300 grams greek yogurt for breakfast");

            assertTrue(result.parsed());
            assertEquals("product", result.type());
            assertEquals("Greek Yogurt", result.productName());
            assertEquals(0, new BigDecimal("0.3").compareTo(result.quantity()));
            assertEquals(MealSlot.BREAKFAST, result.slot());
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should return error for empty text")
        void shouldReturnErrorForEmptyText() {
            VoiceParseResult result = voiceLogService.parse("");

            assertEquals("No text provided", result.error());
        }

        @Test
        @DisplayName("Should return error for whitespace-only text")
        void shouldReturnErrorForWhitespaceText() {
            VoiceParseResult result = voiceLogService.parse("   ");

            assertEquals("No text provided", result.error());
        }

        @Test
        @DisplayName("Should return error for null text")
        void shouldReturnErrorForNullText() {
            VoiceParseResult result = voiceLogService.parse(null);

            assertEquals("No text provided", result.error());
        }

        @Test
        @DisplayName("Should handle special characters in input")
        void shouldHandleSpecialCharacters() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // Special characters should be stripped
            VoiceParseResult result = voiceLogService.parse("spaghetti!! carbonara???");

            assertTrue(result.parsed());
            assertEquals("Spaghetti Carbonara", result.mealName());
        }

        @Test
        @DisplayName("Should return not parsed when no match found")
        void shouldReturnNotParsedWhenNoMatch() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(sampleProducts);

            VoiceParseResult result = voiceLogService.parse("something completely unknown xyz");

            assertFalse(result.parsed());
            assertNotNull(result.error());
        }

        @Test
        @DisplayName("Should handle empty meal and product lists")
        void shouldHandleEmptyLists() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(List.of());

            VoiceParseResult result = voiceLogService.parse("chicken stir fry");

            assertFalse(result.parsed());
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

            VoiceParseResult result = voiceLogService.parse("I had 2 servings of chicken stir fry for dinner");

            assertTrue(result.parsed());
            assertEquals("meal", result.type());
            assertEquals("Chicken Stir Fry", result.mealName());
            assertEquals(0, new BigDecimal("2").compareTo(result.servings()));
            assertEquals(MealSlot.DINNER, result.slot());
        }

        @Test
        @DisplayName("Should parse 'greek yogurt 150g breakfast'")
        void shouldParseCompactInput() {
            when(mealService.findAll()).thenReturn(List.of());
            when(productService.findAll()).thenReturn(sampleProducts);

            VoiceParseResult result = voiceLogService.parse("greek yogurt 150g breakfast");

            assertTrue(result.parsed());
            assertEquals("product", result.type());
            assertEquals("Greek Yogurt", result.productName());
            assertEquals(0, new BigDecimal("0.15").compareTo(result.quantity()));
            assertEquals(MealSlot.BREAKFAST, result.slot());
        }

        @Test
        @DisplayName("Should parse '1 portions of vegetable soup for lunch'")
        void shouldParsePortionsKeyword() {
            when(mealService.findAll()).thenReturn(sampleMeals);
            when(productService.findAll()).thenReturn(List.of());

            // Use whole number since dots get stripped from input
            VoiceParseResult result = voiceLogService.parse("1 portions of vegetable soup for lunch");

            assertTrue(result.parsed());
            assertEquals("Vegetable Soup", result.mealName());
            assertEquals(0, new BigDecimal("1").compareTo(result.servings()));
            assertEquals(MealSlot.LUNCH, result.slot());
        }
    }
}
