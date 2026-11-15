package com.foodie.service;

import com.foodie.client.CloudflareAiClient;
import com.foodie.client.SafeHttpFetcher;
import com.foodie.model.RecipeExtract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link RecipeScraperService}.
 *
 * These tests focus on the parsing logic that converts AI responses into structured data.
 * They don't call the actual AI API - instead they test the parseAiResponse() method
 * with realistic AI output samples to catch regressions in parsing logic.
 */
@ExtendWith(MockitoExtension.class)
class RecipeScraperServiceTest {

    @Mock
    private CloudflareAiClient cloudflareAiClient;

    private RecipeScraperService service;

    @BeforeEach
    void setUp() {
        // Create service with mocked client and default timeouts
        service = new RecipeScraperService(cloudflareAiClient, new SafeHttpFetcher(15000, 60000));
    }

    /** Builds a minimal AI response around the given raw ingredient JSON objects. */
    private static String recipeJson(String category, String... ingredientObjects) {
        return "{\"name\": \"Test\", \"category\": \"" + category + "\", \"servings\": 1, \"instructions\": \"\", "
            + "\"ingredients\": [" + String.join(",", ingredientObjects) + "]}";
    }

    private static String ingredientJson(String name, String quantity, String unit) {
        return "{\"name\": \"" + name + "\", \"quantity\": " + quantity + ", \"unit\": \"" + unit + "\"}";
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

        @ParameterizedTest(name = "fallback for input [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void shouldReturnFallbackForBlankInput(String input) {
            RecipeExtract result = service.parseAiResponse(input);

            assertEquals("Imported Recipe", result.name());
            assertEquals("MAIN_COURSE", result.category());
            assertTrue(result.ingredients().isEmpty());
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            RecipeExtract result = service.parseAiResponse("{ name: Pasta, ingredients: broken }");

            // Should not throw, should return fallback or partial data
            assertNotNull(result);
            assertNotNull(result.name());
        }

        @Test
        @DisplayName("Should handle empty ingredients array")
        void shouldHandleEmptyIngredientsArray() {
            RecipeExtract result = service.parseAiResponse(
                "{\"name\": \"Water\", \"category\": \"DRINK\", \"servings\": 1, "
                    + "\"instructions\": \"Pour water.\", \"ingredients\": []}");

            assertTrue(result.ingredients().isEmpty());
            assertEquals("Water", result.name());
        }
    }

    @Nested
    @DisplayName("Unit Conversion")
    class UnitConversion {

        @ParameterizedTest(name = "{1} {2} -> {3}")
        @CsvSource({
            "flour,    500, g,     0.5",    // g -> kg
            "milk,     250, ml,    0.25",   // ml -> L
            "eggs,     3,   piece, 3",      // pieces unchanged
            "olive oil, 2,  tbsp,  0.030",  // 2 * 0.015 L
            "salt,     1,   tsp,   0.005",  // 1 * 0.005 L
            "flour,    2,   cup,   0.480",  // 2 * 0.240 L
            "beef,     1.5, kg,    1.5",    // kg unchanged
            "butter,   0.5, kg,    0.5"     // decimal quantity unchanged
        })
        void convertsToBaseUnit(String name, String quantity, String unit, String expected) {
            RecipeExtract result = service.parseAiResponse(recipeJson("MAIN_COURSE", ingredientJson(name, quantity, unit)));

            assertEquals(1, result.ingredients().size());
            assertEquals(0, new BigDecimal(expected).compareTo(result.ingredients().get(0).quantity()));
        }
    }

    @Nested
    @DisplayName("Category Normalization")
    class CategoryNormalization {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "MAIN,          MAIN_COURSE",
            "APPETIZER,     SNACK",
            "SMOOTHIE,      DRINK",
            "DESSERT,       DESSERT",       // valid categories pass through
            "UNKNOWN_THING, MAIN_COURSE"    // unknown defaults to main course
        })
        void normalizesCategory(String raw, String expected) {
            assertEquals(expected, service.parseAiResponse(recipeJson(raw)).category());
        }
    }

    @Nested
    @DisplayName("Ingredient Parsing Edge Cases")
    class IngredientParsingEdgeCases {

        @ParameterizedTest(name = "skips invalid ingredient {0}")
        @CsvSource(delimiter = '|', value = {
            "missing name     | {\"name\": \"\", \"quantity\": 100, \"unit\": \"g\"}",
            "missing quantity | {\"name\": \"salt\", \"unit\": \"g\"}"
        })
        void skipsInvalidIngredients(String description, String invalidIngredient) {
            RecipeExtract result = service.parseAiResponse(
                recipeJson("MAIN_COURSE", invalidIngredient, ingredientJson("valid ingredient", "200", "g")));

            assertEquals(1, result.ingredients().size());
            assertEquals("valid ingredient", result.ingredients().get(0).name());
        }
    }

    @Nested
    @DisplayName("Real-World AI Response Samples")
    class RealWorldSamples {

        @Test
        @DisplayName("Should parse typical recipe blog extraction")
        void shouldParseTypicalRecipeBlogExtraction() {
            // Simulates what the AI typically returns from a recipe blog (escaped newlines in instructions)
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

            RecipeExtract.IngredientLine chicken = result.ingredients().stream()
                    .filter(i -> i.name().equals("chicken breast"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(0, new BigDecimal("0.5").compareTo(chicken.quantity())); // 500g -> 0.5kg
        }
    }
}
