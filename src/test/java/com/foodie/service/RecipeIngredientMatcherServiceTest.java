package com.foodie.service;

import com.foodie.controller.dto.MatchedIngredient;
import com.foodie.model.db.Product;
import com.foodie.model.RecipeExtract;
import com.foodie.model.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    /** Stubs the product catalogue and runs the matcher over the given ingredient lines. */
    private List<MatchedIngredient> match(List<Product> products, RecipeExtract.IngredientLine... lines) {
        when(productService.findAll()).thenReturn(products);
        return matcherService.matchAll(new RecipeExtract("Test", "MAIN_COURSE", 1, "", List.of(lines)));
    }

    @Nested
    @DisplayName("Matching existing products")
    class Matching {

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(delimiter = '|', value = {
            "chicken breast       | Chicken Breast  | 0.5  | kg",    // exact
            "OLIVE OIL            | Olive Oil       | 0.05 | L",     // case-insensitive
            "parmesan             | Parmesan Cheese | 0.1  | kg",    // word overlap
            "spaghetti            | Spaghetti Pasta | 0.4  | kg",    // partial -> full name
            "olive                | Olive Oil       | 0.05 | L",     // meets score threshold
            "'  chicken breast  ' | Chicken Breast  | 0.5  | kg",    // leading/trailing spaces
            "eggs                 | Eggs            | 4    | piece"
        })
        void matchesExistingProduct(String ingredient, String expectedName, String qty, String unit) {
            List<MatchedIngredient> results = match(sampleProducts, ingredient(ingredient, qty, unit));

            assertEquals(1, results.size());
            assertFalse(results.get(0).isNew());
            assertEquals(expectedName, results.get(0).product().getName());
        }

        @Test
        @DisplayName("Should prefer higher scoring match")
        void shouldPreferHigherScoringMatch() {
            List<MatchedIngredient> results = match(
                List.of(
                    createProduct(1L, "Tomato", Unit.KG),
                    createProduct(2L, "Tomato Sauce", Unit.LITER),
                    createProduct(3L, "Cherry Tomatoes", Unit.KG)),
                ingredient("tomato sauce", "0.5", "L"));

            assertFalse(results.get(0).isNew());
            assertEquals("Tomato Sauce", results.get(0).product().getName());
        }

        @ParameterizedTest(name = "quantity {1} preserved for \"{0}\"")
        @CsvSource({"brown rice, 0.35, kg", "butter, 0.0625, kg", "eggs, 4, piece"})
        void preservesQuantity(String ingredient, String qty, String unit) {
            List<MatchedIngredient> results = match(sampleProducts, ingredient(ingredient, qty, unit));

            assertEquals(0, new BigDecimal(qty).compareTo(results.get(0).quantity()));
        }
    }

    @Nested
    @DisplayName("No match - new product creation")
    class NewProducts {

        @Test
        @DisplayName("Should mark as new, capitalised and unpersisted, when no match found")
        void shouldMarkAsNewWhenNoMatch() {
            List<MatchedIngredient> results = match(sampleProducts, ingredient("avocado", "0.2", "kg"));

            assertEquals(1, results.size());
            assertTrue(results.get(0).isNew());
            assertEquals("Avocado", results.get(0).product().getName());
            assertNull(results.get(0).product().getId());
        }

        @Test
        @DisplayName("Should capitalise only the first letter of multi-word names")
        void shouldCapitaliseNewProductNames() {
            List<MatchedIngredient> results = match(List.of(), ingredient("fresh mozzarella", "0.2", "kg"));

            assertTrue(results.get(0).isNew());
            assertEquals("Fresh mozzarella", results.get(0).product().getName());
        }

        @Test
        @DisplayName("Should not match when score is below threshold")
        void shouldNotMatchWhenScoreBelowThreshold() {
            // words <= 2 chars are ignored, so "xy" can never reach the score threshold
            assertTrue(match(sampleProducts, ingredient("xy", "0.05", "kg")).get(0).isNew());
        }

        @ParameterizedTest(name = "{0} [{1}] -> {2}")
        @CsvSource({
            "zucchini,           g,            KG",
            "coconut cream,      L,            LITER",
            "bay leaves,         piece,        PIECE",
            "bread,              slice,        PIECE",
            "garlic,             clove,        PIECE",
            "soy sauce,          tbsp,         LITER",
            "water,              cup,          LITER",
            "mystery ingredient, unknown_unit, KG"
        })
        void infersUnitFromRawUnit(String ingredient, String rawUnit, Unit expected) {
            List<MatchedIngredient> results = match(List.of(), ingredient(ingredient, "1", rawUnit));

            assertTrue(results.get(0).isNew());
            assertEquals(expected, results.get(0).product().getUnit());
        }
    }

    @Nested
    @DisplayName("Whole recipes and edge cases")
    class Recipes {

        @Test
        @DisplayName("Should match all ingredients in a recipe")
        void shouldMatchAllIngredients() {
            List<MatchedIngredient> results = match(sampleProducts,
                ingredient("spaghetti pasta", "0.4", "kg"),
                ingredient("tomato sauce", "0.5", "L"),
                ingredient("parmesan cheese", "0.1", "kg"),
                ingredient("fresh basil", "0.02", "kg"),
                ingredient("garlic cloves", "3", "piece"));

            assertEquals(5, results.size());
            assertEquals(5, results.stream().filter(m -> !m.isNew()).count());
        }

        @Test
        @DisplayName("Should handle mix of matched and new ingredients")
        void shouldHandleMixedMatchAndNew() {
            List<MatchedIngredient> results = match(sampleProducts,
                ingredient("chicken breast", "0.5", "kg"),
                ingredient("coconut cream", "0.4", "L"),
                ingredient("brown rice", "0.3", "kg"),
                ingredient("lemongrass", "0.03", "kg"),
                ingredient("fish sauce", "0.03", "L"));

            assertEquals(5, results.size());
            assertTrue(results.stream().filter(m -> !m.isNew()).count() >= 2, "chicken and rice should match");
            assertTrue(results.stream().filter(MatchedIngredient::isNew).count() >= 2,
                "coconut cream and fish sauce should be new");
        }

        @Test
        @DisplayName("Should handle empty ingredient list")
        void shouldHandleEmptyIngredientList() {
            assertTrue(match(sampleProducts).isEmpty());
        }

        @Test
        @DisplayName("Should handle empty product database")
        void shouldHandleEmptyProductDatabase() {
            List<MatchedIngredient> results = match(List.of(), ingredient("chicken", "0.5", "kg"));

            assertEquals(1, results.size());
            assertTrue(results.get(0).isNew());
        }
    }
}
