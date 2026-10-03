package com.foodie.service;

import com.foodie.model.Product;
import com.foodie.model.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for {@link RecipeIngredientMatcherService}.
 * 
 * Tests the fuzzy matching logic that maps AI-extracted ingredients to existing products.
 * This is critical for the recipe import feature - regressions here would cause:
 * - Wrong products being matched to ingredients
 * - Unnecessary duplicate products being created
 * - Incorrect quantities being assigned
 */
@ExtendWith(MockitoExtension.class)
class RecipeIngredientMatcherServiceTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private RecipeIngredientMatcherService matcherService;

    private List<Product> sampleProducts;

    @BeforeEach
    void setUp() {
        sampleProducts = List.of(
            createProduct(1L, "Chicken Breast", Unit.KG),
            createProduct(2L, "Olive Oil", Unit.LITER),
            createProduct(3L, "Brown Rice", Unit.KG),
            createProduct(4L, "Greek Yogurt", Unit.KG),
            createProduct(5L, "Garlic Cloves", Unit.PIECE),
            createProduct(6L, "Fresh Basil", Unit.KG),
            createProduct(7L, "Parmesan Cheese", Unit.KG),
            createProduct(8L, "Spaghetti Pasta", Unit.KG),
            createProduct(9L, "Tomato Sauce", Unit.LITER),
            createProduct(10L, "Salt", Unit.KG),
            createProduct(11L, "Black Pepper", Unit.KG),
            createProduct(12L, "Butter", Unit.KG),
            createProduct(13L, "All Purpose Flour", Unit.KG),
            createProduct(14L, "Whole Milk", Unit.LITER),
            createProduct(15L, "Eggs", Unit.PIECE)
        );
    }

    private Product createProduct(Long id, String name, Unit unit) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setUnit(unit);
        return product;
    }

    private RecipeExtract.IngredientLine ingredient(String name, String quantity, String unit) {
        return new RecipeExtract.IngredientLine(name, new BigDecimal(quantity), unit);
    }

    @Nested
    @DisplayName("Exact and Close Matching")
    class ExactAndCloseMatching {

        @Test
        @DisplayName("Should match ingredient to product with exact name")
        void shouldMatchExactName() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test Recipe", "MAIN_COURSE", 4, "",
                List.of(ingredient("chicken breast", "0.5", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isNew());
            assertEquals("Chicken Breast", results.get(0).product().getName());
            assertEquals(1L, results.get(0).product().getId());
        }

        @Test
        @DisplayName("Should match case-insensitively")
        void shouldMatchCaseInsensitively() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("OLIVE OIL", "0.05", "L"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isNew());
            assertEquals("Olive Oil", results.get(0).product().getName());
        }

        @Test
        @DisplayName("Should match with word overlap")
        void shouldMatchWithWordOverlap() {
            when(productService.findAll()).thenReturn(sampleProducts);

            // "parmesan" should match "Parmesan Cheese"
            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("parmesan", "0.1", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isNew());
            assertEquals("Parmesan Cheese", results.get(0).product().getName());
        }

        @Test
        @DisplayName("Should match partial ingredient to full product name")
        void shouldMatchPartialToFull() {
            when(productService.findAll()).thenReturn(sampleProducts);

            // "spaghetti" should match "Spaghetti Pasta"
            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("spaghetti", "0.4", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(1, results.size());
            assertFalse(results.get(0).isNew());
            assertEquals("Spaghetti Pasta", results.get(0).product().getName());
        }
    }

    @Nested
    @DisplayName("No Match - New Product Creation")
    class NoMatchNewProductCreation {

        @Test
        @DisplayName("Should mark as new when no match found")
        void shouldMarkAsNewWhenNoMatch() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("avocado", "0.2", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(1, results.size());
            assertTrue(results.get(0).isNew());
            assertEquals("Avocado", results.get(0).product().getName()); // Capitalised
            assertNull(results.get(0).product().getId()); // Not persisted yet
        }

        @Test
        @DisplayName("Should infer KG unit for solid ingredients")
        void shouldInferKgUnitForSolids() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("zucchini", "0.3", "g"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertTrue(results.get(0).isNew());
            assertEquals(Unit.KG, results.get(0).product().getUnit());
        }

        @Test
        @DisplayName("Should infer LITER unit for liquid ingredients")
        void shouldInferLiterUnitForLiquids() {
            when(productService.findAll()).thenReturn(List.of()); // Empty to force new product

            RecipeExtract extract = new RecipeExtract(
                "Test", "DRINK", 1, "",
                List.of(ingredient("coconut cream", "0.4", "L")) // Use L not ml for LITER inference
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertTrue(results.get(0).isNew());
            assertEquals(Unit.LITER, results.get(0).product().getUnit());
        }

        @Test
        @DisplayName("Should infer PIECE unit for countable ingredients")
        void shouldInferPieceUnitForCountables() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("bay leaves", "3", "piece"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertTrue(results.get(0).isNew());
            assertEquals(Unit.PIECE, results.get(0).product().getUnit());
        }

        @Test
        @DisplayName("Should capitalise new product names")
        void shouldCapitaliseNewProductNames() {
            when(productService.findAll()).thenReturn(List.of()); // Empty to force new product

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("fresh mozzarella", "0.2", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertTrue(results.get(0).isNew());
            assertEquals("Fresh mozzarella", results.get(0).product().getName());
        }
    }

    @Nested
    @DisplayName("Matching Score Threshold")
    class MatchingScoreThreshold {

        @Test
        @DisplayName("Should not match when score is below threshold")
        void shouldNotMatchWhenScoreBelowThreshold() {
            when(productService.findAll()).thenReturn(sampleProducts);

            // "xy" (2 chars) is too short to match anything
            // The score threshold is 4, and words <= 2 chars are ignored
            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("xy", "0.05", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            // Should create new product because "xy" doesn't meet threshold
            assertTrue(results.get(0).isNew());
        }

        @Test
        @DisplayName("Should match when score meets threshold")
        void shouldMatchWhenScoreMeetsThreshold() {
            when(productService.findAll()).thenReturn(sampleProducts);

            // "olive" (5 chars) should be enough to match "Olive Oil"
            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("olive", "0.05", "L"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertFalse(results.get(0).isNew());
            assertEquals("Olive Oil", results.get(0).product().getName());
        }

        @Test
        @DisplayName("Should prefer higher scoring match")
        void shouldPreferHigherScoringMatch() {
            // Add products where one is a better match
            List<Product> products = List.of(
                createProduct(1L, "Tomato", Unit.KG),
                createProduct(2L, "Tomato Sauce", Unit.LITER),
                createProduct(3L, "Cherry Tomatoes", Unit.KG)
            );
            when(productService.findAll()).thenReturn(products);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("tomato sauce", "0.5", "L"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertFalse(results.get(0).isNew());
            assertEquals("Tomato Sauce", results.get(0).product().getName());
        }
    }

    @Nested
    @DisplayName("Quantity Preservation")
    class QuantityPreservation {

        @Test
        @DisplayName("Should preserve quantity from ingredient line")
        void shouldPreserveQuantity() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("brown rice", "0.35", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(0, new BigDecimal("0.35").compareTo(results.get(0).quantity()));
        }

        @Test
        @DisplayName("Should handle decimal quantities")
        void shouldHandleDecimalQuantities() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("butter", "0.0625", "kg")) // 62.5g
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(0, new BigDecimal("0.0625").compareTo(results.get(0).quantity()));
        }

        @Test
        @DisplayName("Should handle piece quantities")
        void shouldHandlePieceQuantities() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("eggs", "4", "piece"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertFalse(results.get(0).isNew());
            assertEquals("Eggs", results.get(0).product().getName());
            assertEquals(0, new BigDecimal("4").compareTo(results.get(0).quantity()));
        }
    }

    @Nested
    @DisplayName("Multiple Ingredients Matching")
    class MultipleIngredientsMatching {

        @Test
        @DisplayName("Should match all ingredients in a recipe")
        void shouldMatchAllIngredients() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Pasta Recipe", "MAIN_COURSE", 4, "",
                List.of(
                    ingredient("spaghetti pasta", "0.4", "kg"),
                    ingredient("tomato sauce", "0.5", "L"),
                    ingredient("parmesan cheese", "0.1", "kg"),
                    ingredient("fresh basil", "0.02", "kg"),
                    ingredient("garlic cloves", "3", "piece")
                )
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(5, results.size());
            
            // All should match existing products
            long matchedCount = results.stream().filter(m -> !m.isNew()).count();
            assertEquals(5, matchedCount);
        }

        @Test
        @DisplayName("Should handle mix of matched and new ingredients")
        void shouldHandleMixedMatchAndNew() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Thai Curry", "MAIN_COURSE", 4, "",
                List.of(
                    ingredient("chicken breast", "0.5", "kg"),  // exists
                    ingredient("coconut cream", "0.4", "L"),    // new (no match)
                    ingredient("brown rice", "0.3", "kg"),      // exists
                    ingredient("lemongrass", "0.03", "kg"),     // new (no match)
                    ingredient("fish sauce", "0.03", "L")       // new
                )
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(5, results.size());
            
            // Count matches vs new
            long matchedCount = results.stream().filter(m -> !m.isNew()).count();
            long newCount = results.stream().filter(RecipeIngredientMatcherService.MatchedIngredient::isNew).count();
            
            assertTrue(matchedCount >= 2, "At least chicken and rice should match"); 
            assertTrue(newCount >= 2, "At least coconut cream and fish sauce should be new");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle empty ingredient list")
        void shouldHandleEmptyIngredientList() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Empty Recipe", "MAIN_COURSE", 1, "",
                List.of()
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Should handle empty product database")
        void shouldHandleEmptyProductDatabase() {
            when(productService.findAll()).thenReturn(List.of());

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("chicken", "0.5", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(1, results.size());
            assertTrue(results.get(0).isNew());
        }

        @Test
        @DisplayName("Should handle ingredient with trailing/leading spaces")
        void shouldHandleSpacesInIngredientName() {
            when(productService.findAll()).thenReturn(sampleProducts);

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("  chicken breast  ", "0.5", "kg"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            // Should still match after trimming
            assertFalse(results.get(0).isNew());
            assertEquals("Chicken Breast", results.get(0).product().getName());
        }
    }

    @Nested
    @DisplayName("Unit Inference from Raw Unit String")
    class UnitInferenceFromRawUnit {

        @Test
        @DisplayName("Should infer PIECE from 'slice'")
        void shouldInferPieceFromSlice() {
            when(productService.findAll()).thenReturn(List.of());

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("bread", "2", "slice"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(Unit.PIECE, results.get(0).product().getUnit());
        }

        @Test
        @DisplayName("Should infer PIECE from 'clove'")
        void shouldInferPieceFromClove() {
            when(productService.findAll()).thenReturn(List.of());

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("garlic", "4", "clove"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(Unit.PIECE, results.get(0).product().getUnit());
        }

        @Test
        @DisplayName("Should infer LITER from 'tbsp'")
        void shouldInferLiterFromTbsp() {
            when(productService.findAll()).thenReturn(List.of());

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("soy sauce", "0.03", "tbsp"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(Unit.LITER, results.get(0).product().getUnit());
        }

        @Test
        @DisplayName("Should infer LITER from 'cup'")
        void shouldInferLiterFromCup() {
            when(productService.findAll()).thenReturn(List.of());

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("water", "0.48", "cup"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(Unit.LITER, results.get(0).product().getUnit());
        }

        @Test
        @DisplayName("Should default to KG for unknown units")
        void shouldDefaultToKgForUnknownUnits() {
            when(productService.findAll()).thenReturn(List.of());

            RecipeExtract extract = new RecipeExtract(
                "Test", "MAIN_COURSE", 1, "",
                List.of(ingredient("mystery ingredient", "0.1", "unknown_unit"))
            );

            List<RecipeIngredientMatcherService.MatchedIngredient> results = matcherService.matchAll(extract);

            assertEquals(Unit.KG, results.get(0).product().getUnit());
        }
    }
}
