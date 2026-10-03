package com.foodie.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link RecipeScraperService}.
 * 
 * These tests focus on the parsing logic that converts AI responses into structured data.
 * They don't call the actual AI API - instead they test the parseAiResponse() method
 * with realistic AI output samples to catch regressions in parsing logic.
 */
class RecipeScraperServiceTest {

    private RecipeScraperService service;

    @BeforeEach
    void setUp() {
        // Create service without Cloudflare credentials - we're testing parsing, not API calls
        service = new RecipeScraperService("", "", "", 0);
    }

    @Nested
    @DisplayName("AI Response Parsing")
    class AiResponseParsing {

        @Test
        @DisplayName("Should parse well-formed JSON response from AI")
        void shouldParseWellFormedJsonResponse() {
            String aiResponse = """
                {
                  "name": "Spaghetti Carbonara",
                  "category": "MAIN_COURSE",
                  "servings": 4,
                  "instructions": "1. Cook pasta. 2. Fry bacon. 3. Mix eggs with cheese. 4. Combine all.",
                  "ingredients": [
                    {"name": "spaghetti", "quantity": 400, "unit": "g"},
                    {"name": "bacon", "quantity": 200, "unit": "g"},
                    {"name": "eggs", "quantity": 4, "unit": "piece"},
                    {"name": "parmesan", "quantity": 100, "unit": "g"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertEquals("Spaghetti Carbonara", result.name());
            assertEquals("MAIN_COURSE", result.category());
            assertEquals(4, result.servings());
            assertTrue(result.instructions().contains("Cook pasta"));
            assertEquals(4, result.ingredients().size());
        }

        @Test
        @DisplayName("Should handle JSON wrapped in markdown code blocks")
        void shouldHandleMarkdownCodeBlocks() {
            String aiResponse = """
                ```json
                {
                  "name": "Greek Salad",
                  "category": "SALAD",
                  "servings": 2,
                  "instructions": "Chop vegetables, add feta, dress with olive oil.",
                  "ingredients": [
                    {"name": "cucumber", "quantity": 200, "unit": "g"},
                    {"name": "tomatoes", "quantity": 300, "unit": "g"}
                  ]
                }
                ```
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertEquals("Greek Salad", result.name());
            assertEquals("SALAD", result.category());
            assertEquals(2, result.ingredients().size());
        }

        @Test
        @DisplayName("Should return fallback for null input")
        void shouldReturnFallbackForNullInput() {
            RecipeExtract result = service.parseAiResponse(null);

            assertEquals("Imported Recipe", result.name());
            assertEquals("MAIN_COURSE", result.category());
            assertTrue(result.ingredients().isEmpty());
        }

        @Test
        @DisplayName("Should return fallback for empty input")
        void shouldReturnFallbackForEmptyInput() {
            RecipeExtract result = service.parseAiResponse("   ");

            assertEquals("Imported Recipe", result.name());
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            String malformed = "{ name: Pasta, ingredients: broken }";

            RecipeExtract result = service.parseAiResponse(malformed);

            // Should not throw, should return fallback or partial data
            assertNotNull(result);
            assertNotNull(result.name());
        }
    }

    @Nested
    @DisplayName("Unit Conversion")
    class UnitConversion {

        @Test
        @DisplayName("Should convert grams to kg (divide by 1000)")
        void shouldConvertGramsToKg() {
            String aiResponse = """
                {
                  "name": "Test",
                  "category": "MAIN_COURSE",
                  "servings": 1,
                  "instructions": "",
                  "ingredients": [
                    {"name": "flour", "quantity": 500, "unit": "g"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertEquals(1, result.ingredients().size());
            RecipeExtract.IngredientLine flour = result.ingredients().get(0);
            // 500g = 0.5kg
            assertEquals(0, new BigDecimal("0.5").compareTo(flour.quantity()));
        }

        @Test
        @DisplayName("Should convert ml to L (divide by 1000)")
        void shouldConvertMlToLiter() {
            String aiResponse = """
                {
                  "name": "Smoothie",
                  "category": "DRINK",
                  "servings": 1,
                  "instructions": "",
                  "ingredients": [
                    {"name": "milk", "quantity": 250, "unit": "ml"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            RecipeExtract.IngredientLine milk = result.ingredients().get(0);
            // 250ml = 0.25L
            assertEquals(0, new BigDecimal("0.25").compareTo(milk.quantity()));
        }

        @Test
        @DisplayName("Should keep piece units unchanged")
        void shouldKeepPieceUnitsUnchanged() {
            String aiResponse = """
                {
                  "name": "Omelette",
                  "category": "MAIN_COURSE",
                  "servings": 1,
                  "instructions": "",
                  "ingredients": [
                    {"name": "eggs", "quantity": 3, "unit": "piece"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            RecipeExtract.IngredientLine eggs = result.ingredients().get(0);
            assertEquals(0, new BigDecimal("3").compareTo(eggs.quantity()));
        }

        @Test
        @DisplayName("Should convert tablespoon to liters")
        void shouldConvertTablespoonToLiters() {
            String aiResponse = """
                {
                  "name": "Dressing",
                  "category": "SAUCE",
                  "servings": 1,
                  "instructions": "",
                  "ingredients": [
                    {"name": "olive oil", "quantity": 2, "unit": "tbsp"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            RecipeExtract.IngredientLine oil = result.ingredients().get(0);
            // 2 tbsp = 2 * 0.015L = 0.03L
            assertEquals(0, new BigDecimal("0.030").compareTo(oil.quantity()));
        }

        @Test
        @DisplayName("Should convert teaspoon to liters")
        void shouldConvertTeaspoonToLiters() {
            String aiResponse = """
                {
                  "name": "Sauce",
                  "category": "SAUCE",
                  "servings": 1,
                  "instructions": "",
                  "ingredients": [
                    {"name": "salt", "quantity": 1, "unit": "tsp"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            RecipeExtract.IngredientLine salt = result.ingredients().get(0);
            // 1 tsp = 0.005L
            assertEquals(0, new BigDecimal("0.005").compareTo(salt.quantity()));
        }

        @Test
        @DisplayName("Should convert cups to liters")
        void shouldConvertCupsToLiters() {
            String aiResponse = """
                {
                  "name": "Pancakes",
                  "category": "MAIN_COURSE",
                  "servings": 4,
                  "instructions": "",
                  "ingredients": [
                    {"name": "flour", "quantity": 2, "unit": "cup"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            RecipeExtract.IngredientLine flour = result.ingredients().get(0);
            // 2 cups = 2 * 0.240L = 0.48L
            assertEquals(0, new BigDecimal("0.480").compareTo(flour.quantity()));
        }

        @Test
        @DisplayName("Should keep kg unchanged")
        void shouldKeepKgUnchanged() {
            String aiResponse = """
                {
                  "name": "Roast",
                  "category": "MAIN_COURSE",
                  "servings": 6,
                  "instructions": "",
                  "ingredients": [
                    {"name": "beef", "quantity": 1.5, "unit": "kg"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            RecipeExtract.IngredientLine beef = result.ingredients().get(0);
            assertEquals(0, new BigDecimal("1.5").compareTo(beef.quantity()));
        }
    }

    @Nested
    @DisplayName("Category Normalization")
    class CategoryNormalization {

        @Test
        @DisplayName("Should normalize MAIN to MAIN_COURSE")
        void shouldNormalizeMainToMainCourse() {
            String aiResponse = """
                {"name": "Pasta", "category": "MAIN", "servings": 2, "instructions": "", "ingredients": []}
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);
            assertEquals("MAIN_COURSE", result.category());
        }

        @Test
        @DisplayName("Should normalize APPETIZER to SNACK")
        void shouldNormalizeAppetizerToSnack() {
            String aiResponse = """
                {"name": "Bruschetta", "category": "APPETIZER", "servings": 4, "instructions": "", "ingredients": []}
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);
            assertEquals("SNACK", result.category());
        }

        @Test
        @DisplayName("Should normalize SMOOTHIE to DRINK")
        void shouldNormalizeSmoothieToDrink() {
            String aiResponse = """
                {"name": "Berry Smoothie", "category": "SMOOTHIE", "servings": 1, "instructions": "", "ingredients": []}
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);
            assertEquals("DRINK", result.category());
        }

        @Test
        @DisplayName("Should default unknown category to MAIN_COURSE")
        void shouldDefaultUnknownCategoryToMainCourse() {
            String aiResponse = """
                {"name": "Mystery Dish", "category": "UNKNOWN_THING", "servings": 2, "instructions": "", "ingredients": []}
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);
            assertEquals("MAIN_COURSE", result.category());
        }
    }

    @Nested
    @DisplayName("Ingredient Parsing Edge Cases")
    class IngredientParsingEdgeCases {

        @Test
        @DisplayName("Should skip ingredients with missing name")
        void shouldSkipIngredientsWithMissingName() {
            String aiResponse = """
                {
                  "name": "Test",
                  "category": "MAIN_COURSE",
                  "servings": 1,
                  "instructions": "",
                  "ingredients": [
                    {"name": "", "quantity": 100, "unit": "g"},
                    {"name": "valid ingredient", "quantity": 200, "unit": "g"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertEquals(1, result.ingredients().size());
            assertEquals("valid ingredient", result.ingredients().get(0).name());
        }

        @Test
        @DisplayName("Should skip ingredients with missing quantity")
        void shouldSkipIngredientsWithMissingQuantity() {
            String aiResponse = """
                {
                  "name": "Test",
                  "category": "MAIN_COURSE",
                  "servings": 1,
                  "instructions": "",
                  "ingredients": [
                    {"name": "salt", "unit": "g"},
                    {"name": "pepper", "quantity": 5, "unit": "g"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertEquals(1, result.ingredients().size());
            assertEquals("pepper", result.ingredients().get(0).name());
        }

        @Test
        @DisplayName("Should handle decimal quantities")
        void shouldHandleDecimalQuantities() {
            String aiResponse = """
                {
                  "name": "Test",
                  "category": "MAIN_COURSE",
                  "servings": 1,
                  "instructions": "",
                  "ingredients": [
                    {"name": "butter", "quantity": 0.5, "unit": "kg"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertEquals(0, new BigDecimal("0.5").compareTo(result.ingredients().get(0).quantity()));
        }

        @Test
        @DisplayName("Should handle empty ingredients array")
        void shouldHandleEmptyIngredientsArray() {
            String aiResponse = """
                {"name": "Water", "category": "DRINK", "servings": 1, "instructions": "Pour water.", "ingredients": []}
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertTrue(result.ingredients().isEmpty());
            assertEquals("Water", result.name());
        }
    }

    @Nested
    @DisplayName("Real-World AI Response Samples")
    class RealWorldSamples {

        @Test
        @DisplayName("Should parse typical recipe blog extraction")
        void shouldParseTypicalRecipeBlogExtraction() {
            // Simulates what the AI typically returns from a recipe blog
            String aiResponse = """
                {
                  "name": "Classic Chicken Stir Fry",
                  "category": "MAIN_COURSE",
                  "servings": 4,
                  "instructions": "1. Cut chicken into bite-sized pieces.\\n2. Heat oil in a wok over high heat.\\n3. Stir-fry chicken until golden.\\n4. Add vegetables and sauce.\\n5. Serve over rice.",
                  "ingredients": [
                    {"name": "chicken breast", "quantity": 500, "unit": "g"},
                    {"name": "broccoli florets", "quantity": 200, "unit": "g"},
                    {"name": "bell pepper", "quantity": 1, "unit": "piece"},
                    {"name": "soy sauce", "quantity": 3, "unit": "tbsp"},
                    {"name": "sesame oil", "quantity": 1, "unit": "tbsp"},
                    {"name": "garlic cloves", "quantity": 3, "unit": "piece"},
                    {"name": "ginger", "quantity": 20, "unit": "g"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertEquals("Classic Chicken Stir Fry", result.name());
            assertEquals("MAIN_COURSE", result.category());
            assertEquals(4, result.servings());
            assertEquals(7, result.ingredients().size());
            
            // Verify unit conversions happened
            RecipeExtract.IngredientLine chicken = result.ingredients().stream()
                    .filter(i -> i.name().equals("chicken breast"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(0, new BigDecimal("0.5").compareTo(chicken.quantity())); // 500g -> 0.5kg
        }

        @Test
        @DisplayName("Should parse dessert recipe with mixed units")
        void shouldParseDessertRecipeWithMixedUnits() {
            String aiResponse = """
                {
                  "name": "Chocolate Brownies",
                  "category": "DESSERT",
                  "servings": 12,
                  "instructions": "Melt chocolate and butter. Mix in sugar and eggs. Add flour. Bake at 180C for 25 minutes.",
                  "ingredients": [
                    {"name": "dark chocolate", "quantity": 200, "unit": "g"},
                    {"name": "butter", "quantity": 150, "unit": "g"},
                    {"name": "sugar", "quantity": 1, "unit": "cup"},
                    {"name": "eggs", "quantity": 3, "unit": "piece"},
                    {"name": "flour", "quantity": 100, "unit": "g"},
                    {"name": "vanilla extract", "quantity": 1, "unit": "tsp"}
                  ]
                }
                """;

            RecipeExtract result = service.parseAiResponse(aiResponse);

            assertEquals("Chocolate Brownies", result.name());
            assertEquals("DESSERT", result.category());
            assertEquals(6, result.ingredients().size());

            // Check eggs stayed as pieces
            RecipeExtract.IngredientLine eggs = result.ingredients().stream()
                    .filter(i -> i.name().equals("eggs"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(0, new BigDecimal("3").compareTo(eggs.quantity()));
        }
    }
}
