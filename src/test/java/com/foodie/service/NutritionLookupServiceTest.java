package com.foodie.service;

import com.foodie.model.Product;
import com.foodie.model.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link NutritionLookupService}.
 * 
 * Tests the parsing of Open Food Facts API responses.
 * Does not make actual API calls - tests the JSON parsing logic with sample responses.
 */
class NutritionLookupServiceTest {

    private NutritionLookupService service;
    private Method extractJsonStringMethod;
    private Method extractJsonNumberMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Create service without proxy config
        service = new NutritionLookupService("", 0);

        // Access private methods for testing
        extractJsonStringMethod = NutritionLookupService.class.getDeclaredMethod("extractJsonString", String.class, String.class);
        extractJsonStringMethod.setAccessible(true);

        extractJsonNumberMethod = NutritionLookupService.class.getDeclaredMethod("extractJsonNumber", String.class, String.class);
        extractJsonNumberMethod.setAccessible(true);
    }

    private String extractJsonString(String json, String key) throws Exception {
        return (String) extractJsonStringMethod.invoke(service, json, key);
    }

    private BigDecimal extractJsonNumber(String json, String key) throws Exception {
        return (BigDecimal) extractJsonNumberMethod.invoke(service, json, key);
    }

    @Nested
    @DisplayName("JSON String Extraction")
    class JsonStringExtraction {

        @Test
        @DisplayName("Should extract simple string value")
        void shouldExtractSimpleString() throws Exception {
            String json = "{\"product_name\": \"Organic Peanut Butter\"}";

            String result = extractJsonString(json, "product_name");

            assertEquals("Organic Peanut Butter", result);
        }

        @Test
        @DisplayName("Should extract string with spaces around colon")
        void shouldExtractStringWithSpaces() throws Exception {
            String json = "{\"brands\"   :   \"Skippy\"}";

            String result = extractJsonString(json, "brands");

            assertEquals("Skippy", result);
        }

        @Test
        @DisplayName("Should return null for missing key")
        void shouldReturnNullForMissingKey() throws Exception {
            String json = "{\"product_name\": \"Milk\"}";

            String result = extractJsonString(json, "brands");

            assertNull(result);
        }

        @Test
        @DisplayName("Should handle empty string value")
        void shouldHandleEmptyString() throws Exception {
            String json = "{\"quantity\": \"\"}";

            String result = extractJsonString(json, "quantity");

            assertEquals("", result);
        }

        @Test
        @DisplayName("Should extract string from nested JSON")
        void shouldExtractFromNestedJson() throws Exception {
            String json = "{\"product\": {\"product_name\": \"Cheese\"}, \"status\": 1}";

            String result = extractJsonString(json, "product_name");

            assertEquals("Cheese", result);
        }
    }

    @Nested
    @DisplayName("JSON Number Extraction")
    class JsonNumberExtraction {

        @Test
        @DisplayName("Should extract integer value")
        void shouldExtractInteger() throws Exception {
            String json = "{\"energy-kcal_100g\": 250}";

            BigDecimal result = extractJsonNumber(json, "energy-kcal_100g");

            assertEquals(0, new BigDecimal("250").compareTo(result));
        }

        @Test
        @DisplayName("Should extract decimal value")
        void shouldExtractDecimal() throws Exception {
            String json = "{\"proteins_100g\": 12.5}";

            BigDecimal result = extractJsonNumber(json, "proteins_100g");

            assertEquals(0, new BigDecimal("12.5").compareTo(result));
        }

        @Test
        @DisplayName("Should return null for missing numeric key")
        void shouldReturnNullForMissingNumericKey() throws Exception {
            String json = "{\"proteins_100g\": 10}";

            BigDecimal result = extractJsonNumber(json, "fiber_100g");

            assertNull(result);
        }

        @Test
        @DisplayName("Should extract zero value")
        void shouldExtractZeroValue() throws Exception {
            String json = "{\"sugars_100g\": 0}";

            BigDecimal result = extractJsonNumber(json, "sugars_100g");

            assertEquals(0, BigDecimal.ZERO.compareTo(result));
        }

        @Test
        @DisplayName("Should extract small decimal value")
        void shouldExtractSmallDecimal() throws Exception {
            String json = "{\"fiber_100g\": 0.5}";

            BigDecimal result = extractJsonNumber(json, "fiber_100g");

            assertEquals(0, new BigDecimal("0.5").compareTo(result));
        }
    }

    @Nested
    @DisplayName("Barcode Lookup Response Parsing")
    class BarcodeLookupParsing {

        @Test
        @DisplayName("Should parse complete barcode response")
        void shouldParseCompleteResponse() throws Exception {
            String json = """
                {
                  "status": 1,
                  "product_name": "Whole Milk",
                  "brands": "Organic Valley",
                  "quantity": "1L",
                  "energy-kcal_100g": 64,
                  "proteins_100g": 3.3,
                  "carbohydrates_100g": 4.8,
                  "fat_100g": 3.6,
                  "fiber_100g": 0,
                  "sugars_100g": 4.8
                }
                """;

            // Test individual field extraction
            assertEquals("Whole Milk", extractJsonString(json, "product_name"));
            assertEquals("Organic Valley", extractJsonString(json, "brands"));
            assertEquals("1L", extractJsonString(json, "quantity"));
            assertEquals(0, new BigDecimal("64").compareTo(extractJsonNumber(json, "energy-kcal_100g")));
            assertEquals(0, new BigDecimal("3.3").compareTo(extractJsonNumber(json, "proteins_100g")));
            assertEquals(0, new BigDecimal("4.8").compareTo(extractJsonNumber(json, "carbohydrates_100g")));
            assertEquals(0, new BigDecimal("3.6").compareTo(extractJsonNumber(json, "fat_100g")));
            assertEquals(0, BigDecimal.ZERO.compareTo(extractJsonNumber(json, "fiber_100g")));
            assertEquals(0, new BigDecimal("4.8").compareTo(extractJsonNumber(json, "sugars_100g")));
        }

        @Test
        @DisplayName("Should handle response with missing optional fields")
        void shouldHandleMissingOptionalFields() throws Exception {
            String json = """
                {
                  "status": 1,
                  "product_name": "Generic Bread",
                  "energy-kcal_100g": 265,
                  "proteins_100g": 9,
                  "carbohydrates_100g": 49
                }
                """;

            assertEquals("Generic Bread", extractJsonString(json, "product_name"));
            assertNull(extractJsonString(json, "brands"));
            assertNull(extractJsonString(json, "quantity"));
            assertNull(extractJsonNumber(json, "fat_100g"));
            assertNull(extractJsonNumber(json, "fiber_100g"));
        }

        @Test
        @DisplayName("Should handle response with special characters in name")
        void shouldHandleSpecialCharactersInName() throws Exception {
            String json = "{\"product_name\": \"M&M's Peanut Chocolate\", \"status\": 1}";

            String result = extractJsonString(json, "product_name");

            assertEquals("M&M's Peanut Chocolate", result);
        }
    }

    @Nested
    @DisplayName("NutritionInfo Record")
    class NutritionInfoRecord {

        @Test
        @DisplayName("Should create NutritionInfo from Nutriments")
        void shouldCreateFromNutriments() {
            NutritionLookupService.Nutriments nutriments = new NutritionLookupService.Nutriments();
            nutriments.setEnergyKcal100g(250.0);
            nutriments.setProteins100g(10.5);
            nutriments.setCarbohydrates100g(30.0);
            nutriments.setFat100g(12.0);
            nutriments.setFiber100g(3.5);
            nutriments.setSugars100g(8.0);

            NutritionLookupService.NutritionInfo info = NutritionLookupService.NutritionInfo.from(nutriments);

            assertEquals(0, new BigDecimal("250").compareTo(info.calories()));
            assertEquals(0, new BigDecimal("10.5").compareTo(info.protein()));
            assertEquals(0, new BigDecimal("30").compareTo(info.carbs()));
            assertEquals(0, new BigDecimal("12").compareTo(info.fat()));
            assertEquals(0, new BigDecimal("3.5").compareTo(info.fiber()));
            assertEquals(0, new BigDecimal("8").compareTo(info.sugar()));
        }

        @Test
        @DisplayName("Should handle null values in Nutriments")
        void shouldHandleNullNutriments() {
            NutritionLookupService.Nutriments nutriments = new NutritionLookupService.Nutriments();
            // All fields are null

            NutritionLookupService.NutritionInfo info = NutritionLookupService.NutritionInfo.from(nutriments);

            // Null values should become BigDecimal.ZERO
            assertEquals(0, BigDecimal.ZERO.compareTo(info.calories()));
            assertEquals(0, BigDecimal.ZERO.compareTo(info.protein()));
            assertEquals(0, BigDecimal.ZERO.compareTo(info.carbs()));
            assertEquals(0, BigDecimal.ZERO.compareTo(info.fat()));
            assertEquals(0, BigDecimal.ZERO.compareTo(info.fiber()));
            assertEquals(0, BigDecimal.ZERO.compareTo(info.sugar()));
        }

        @Test
        @DisplayName("Should handle partial Nutriments data")
        void shouldHandlePartialNutriments() {
            NutritionLookupService.Nutriments nutriments = new NutritionLookupService.Nutriments();
            nutriments.setEnergyKcal100g(100.0);
            nutriments.setProteins100g(5.0);
            // Other fields are null

            NutritionLookupService.NutritionInfo info = NutritionLookupService.NutritionInfo.from(nutriments);

            assertEquals(0, new BigDecimal("100").compareTo(info.calories()));
            assertEquals(0, new BigDecimal("5").compareTo(info.protein()));
            assertEquals(0, BigDecimal.ZERO.compareTo(info.carbs()));
            assertEquals(0, BigDecimal.ZERO.compareTo(info.fat()));
        }
    }

    @Nested
    @DisplayName("Nutriments hasData Check")
    class NutrimentsHasData {

        @Test
        @DisplayName("Should return true when calories present")
        void shouldReturnTrueWhenCaloriesPresent() {
            NutritionLookupService.Nutriments nutriments = new NutritionLookupService.Nutriments();
            nutriments.setEnergyKcal100g(100.0);

            assertTrue(nutriments.hasData());
        }

        @Test
        @DisplayName("Should return true when protein present")
        void shouldReturnTrueWhenProteinPresent() {
            NutritionLookupService.Nutriments nutriments = new NutritionLookupService.Nutriments();
            nutriments.setProteins100g(10.0);

            assertTrue(nutriments.hasData());
        }

        @Test
        @DisplayName("Should return true when fat present")
        void shouldReturnTrueWhenFatPresent() {
            NutritionLookupService.Nutriments nutriments = new NutritionLookupService.Nutriments();
            nutriments.setFat100g(5.0);

            assertTrue(nutriments.hasData());
        }

        @Test
        @DisplayName("Should return false when no key nutrients present")
        void shouldReturnFalseWhenNoKeyNutrients() {
            NutritionLookupService.Nutriments nutriments = new NutritionLookupService.Nutriments();
            // Only carbs and fiber, no calories/protein/fat
            nutriments.setCarbohydrates100g(20.0);
            nutriments.setFiber100g(5.0);

            assertFalse(nutriments.hasData());
        }

        @Test
        @DisplayName("Should return false when all null")
        void shouldReturnFalseWhenAllNull() {
            NutritionLookupService.Nutriments nutriments = new NutritionLookupService.Nutriments();

            assertFalse(nutriments.hasData());
        }
    }

    @Nested
    @DisplayName("BarcodeResult Record")
    class BarcodeResultRecord {

        @Test
        @DisplayName("Should create BarcodeResult with all fields")
        void shouldCreateWithAllFields() {
            NutritionLookupService.BarcodeResult result = new NutritionLookupService.BarcodeResult(
                "Test Product",
                "500g",
                new BigDecimal("250"),
                new BigDecimal("10"),
                new BigDecimal("30"),
                new BigDecimal("12"),
                new BigDecimal("5"),
                new BigDecimal("8")
            );

            assertEquals("Test Product", result.name());
            assertEquals("500g", result.quantity());
            assertEquals(0, new BigDecimal("250").compareTo(result.calories()));
            assertEquals(0, new BigDecimal("10").compareTo(result.protein()));
            assertEquals(0, new BigDecimal("30").compareTo(result.carbs()));
            assertEquals(0, new BigDecimal("12").compareTo(result.fat()));
            assertEquals(0, new BigDecimal("5").compareTo(result.fiber()));
            assertEquals(0, new BigDecimal("8").compareTo(result.sugar()));
        }

        @Test
        @DisplayName("Should allow null values for optional fields")
        void shouldAllowNullOptionalFields() {
            NutritionLookupService.BarcodeResult result = new NutritionLookupService.BarcodeResult(
                "Basic Product",
                null,
                new BigDecimal("100"),
                null,
                null,
                null,
                null,
                null
            );

            assertEquals("Basic Product", result.name());
            assertNull(result.quantity());
            assertEquals(0, new BigDecimal("100").compareTo(result.calories()));
            assertNull(result.protein());
        }
    }

    @Nested
    @DisplayName("AutoFill Nutrition")
    class AutoFillNutrition {

        @Test
        @DisplayName("Should not modify product with empty name")
        void shouldNotModifyProductWithEmptyName() {
            Product product = new Product();
            product.setName("");
            product.setUnit(Unit.KG);

            service.autoFillNutrition(product);

            // Should not throw, should not modify
            assertNull(product.getCalories());
        }

        @Test
        @DisplayName("Should not modify product with null name")
        void shouldNotModifyProductWithNullName() {
            Product product = new Product();
            product.setName(null);
            product.setUnit(Unit.KG);

            service.autoFillNutrition(product);

            assertNull(product.getCalories());
        }

        @Test
        @DisplayName("Should not modify product with all nutrition already filled")
        void shouldNotModifyFullyFilledProduct() {
            Product product = new Product();
            product.setName("Test Product");
            product.setUnit(Unit.KG);
            product.setCalories(new BigDecimal("100"));
            product.setProtein(new BigDecimal("10"));
            product.setCarbs(new BigDecimal("20"));
            product.setFat(new BigDecimal("5"));
            product.setFiber(new BigDecimal("3"));
            product.setSugar(new BigDecimal("8"));

            BigDecimal originalCalories = product.getCalories();

            service.autoFillNutrition(product);

            // Values should remain unchanged
            assertEquals(0, originalCalories.compareTo(product.getCalories()));
        }
    }

    @Nested
    @DisplayName("Real-World Open Food Facts Response Samples")
    class RealWorldSamples {

        @Test
        @DisplayName("Should parse typical chocolate bar response")
        void shouldParseChocolateBarResponse() throws Exception {
            String json = """
                {
                  "status": 1,
                  "product_name": "Dark Chocolate 70%",
                  "brands": "Lindt",
                  "quantity": "100g",
                  "energy-kcal_100g": 598,
                  "proteins_100g": 7.8,
                  "carbohydrates_100g": 31.5,
                  "fat_100g": 46.3,
                  "fiber_100g": 12.7,
                  "sugars_100g": 23.4
                }
                """;

            assertEquals("Dark Chocolate 70%", extractJsonString(json, "product_name"));
            assertEquals("Lindt", extractJsonString(json, "brands"));
            assertEquals(0, new BigDecimal("598").compareTo(extractJsonNumber(json, "energy-kcal_100g")));
            assertEquals(0, new BigDecimal("46.3").compareTo(extractJsonNumber(json, "fat_100g")));
        }

        @Test
        @DisplayName("Should parse typical yogurt response")
        void shouldParseYogurtResponse() throws Exception {
            String json = """
                {
                  "status": 1,
                  "product_name": "Greek Style Yogurt",
                  "brands": "Fage",
                  "quantity": "500g",
                  "energy-kcal_100g": 97,
                  "proteins_100g": 9,
                  "carbohydrates_100g": 4,
                  "fat_100g": 5,
                  "fiber_100g": 0,
                  "sugars_100g": 4
                }
                """;

            assertEquals("Greek Style Yogurt", extractJsonString(json, "product_name"));
            assertEquals(0, new BigDecimal("97").compareTo(extractJsonNumber(json, "energy-kcal_100g")));
            assertEquals(0, new BigDecimal("9").compareTo(extractJsonNumber(json, "proteins_100g")));
        }

        @Test
        @DisplayName("Should parse response with brand in name combination")
        void shouldParseBrandNameCombination() throws Exception {
            // When brand is separate, the service combines them
            String json = """
                {
                  "status": 1,
                  "product_name": "Peanut Butter Crunchy",
                  "brands": "Skippy",
                  "energy-kcal_100g": 588
                }
                """;

            String name = extractJsonString(json, "product_name");
            String brand = extractJsonString(json, "brands");

            assertEquals("Peanut Butter Crunchy", name);
            assertEquals("Skippy", brand);
            // In the service, these would be combined to "Skippy Peanut Butter Crunchy"
        }
    }
}
