package com.foodie.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests for {@link PhotoNutritionService}.
 * 
 * Tests the parsing of Cloudflare AI vision model responses into structured nutrition data.
 * Does not make actual API calls - tests the parseResponse() logic with sample responses.
 */
class PhotoNutritionServiceTest {

    private PhotoNutritionService service;
    private Method parseResponseMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Create service without credentials - we're testing parsing logic only
        service = new PhotoNutritionService("", "", "", 0);
        
        // Access the private parseResponse method for testing
        parseResponseMethod = PhotoNutritionService.class.getDeclaredMethod("parseResponse", String.class);
        parseResponseMethod.setAccessible(true);
    }

    private PhotoNutritionService.NutritionEstimate parseResponse(String json) throws Exception {
        return (PhotoNutritionService.NutritionEstimate) parseResponseMethod.invoke(service, json);
    }

    @Nested
    @DisplayName("Cloudflare Response Parsing")
    class CloudflareResponseParsing {

        @Test
        @DisplayName("Should parse well-formed Cloudflare AI response")
        void shouldParseWellFormedResponse() throws Exception {
            String cloudflareResponse = """
                {
                  "result": {
                    "response": "FOOD: Grilled chicken breast with steamed broccoli\\nCALORIES: 350\\nPROTEIN: 42g\\nCARBS: 12g\\nFAT: 14g\\nFIBER: 4g\\nWEIGHT: 300g"
                  },
                  "success": true
                }
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertTrue(result.isValid());
            assertEquals("Grilled chicken breast with steamed broccoli", result.description());
            assertEquals(0, new BigDecimal("350").compareTo(result.calories()));
            assertEquals(0, new BigDecimal("42").compareTo(result.protein()));
            assertEquals(0, new BigDecimal("12").compareTo(result.carbs()));
            assertEquals(0, new BigDecimal("14").compareTo(result.fat()));
            assertEquals(0, new BigDecimal("4").compareTo(result.fiber()));
            assertEquals(0, new BigDecimal("300").compareTo(result.estimatedWeightGrams()));
        }

        @Test
        @DisplayName("Should handle response with decimal values")
        void shouldHandleDecimalValues() throws Exception {
            String cloudflareResponse = """
                {
                  "result": {
                    "response": "FOOD: Greek yogurt with honey\\nCALORIES: 185.5\\nPROTEIN: 12.3g\\nCARBS: 24.7g\\nFAT: 4.2g\\nFIBER: 0.5g\\nWEIGHT: 200g"
                  },
                  "success": true
                }
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertTrue(result.isValid());
            assertEquals(0, new BigDecimal("185.5").compareTo(result.calories()));
            assertEquals(0, new BigDecimal("12.3").compareTo(result.protein()));
        }

        @Test
        @DisplayName("Should handle missing response field gracefully")
        void shouldHandleMissingResponseField() throws Exception {
            String cloudflareResponse = """
                {
                  "result": {},
                  "success": true
                }
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertFalse(result.isValid());
            assertEquals("Could not parse response", result.description());
        }

        @Test
        @DisplayName("Should handle null input - throws NullPointerException")
        void shouldHandleNullInput() throws Exception {
            // The actual implementation throws NPE for null input
            // This is expected behavior since null responses shouldn't occur in practice
            assertThrows(Exception.class, () -> parseResponse(null));
        }
    }

    @Nested
    @DisplayName("Nutrition Value Extraction")
    class NutritionValueExtraction {

        @Test
        @DisplayName("Should extract all nutrition fields correctly")
        void shouldExtractAllNutritionFields() throws Exception {
            String cloudflareResponse = """
                {"result":{"response":"FOOD: Pasta with tomato sauce\\nCALORIES: 450\\nPROTEIN: 15g\\nCARBS: 65g\\nFAT: 12g\\nFIBER: 6g\\nWEIGHT: 350g"},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertAll(
                () -> assertEquals("Pasta with tomato sauce", result.description()),
                () -> assertEquals(0, new BigDecimal("450").compareTo(result.calories())),
                () -> assertEquals(0, new BigDecimal("15").compareTo(result.protein())),
                () -> assertEquals(0, new BigDecimal("65").compareTo(result.carbs())),
                () -> assertEquals(0, new BigDecimal("12").compareTo(result.fat())),
                () -> assertEquals(0, new BigDecimal("6").compareTo(result.fiber())),
                () -> assertEquals(0, new BigDecimal("350").compareTo(result.estimatedWeightGrams()))
            );
        }

        @Test
        @DisplayName("Should handle missing optional fields")
        void shouldHandleMissingOptionalFields() throws Exception {
            // Some AI responses might not include all fields
            String cloudflareResponse = """
                {"result":{"response":"FOOD: Apple\\nCALORIES: 95\\nPROTEIN: 0g\\nCARBS: 25g\\nFAT: 0g"},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertEquals("Apple", result.description());
            assertEquals(0, new BigDecimal("95").compareTo(result.calories()));
            // Missing FIBER and WEIGHT should be null
            assertNull(result.fiber());
            assertNull(result.estimatedWeightGrams());
        }

        @Test
        @DisplayName("Should handle zero values")
        void shouldHandleZeroValues() throws Exception {
            String cloudflareResponse = """
                {"result":{"response":"FOOD: Black coffee\\nCALORIES: 2\\nPROTEIN: 0g\\nCARBS: 0g\\nFAT: 0g\\nFIBER: 0g\\nWEIGHT: 240g"},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertTrue(result.isValid());
            assertEquals(0, new BigDecimal("2").compareTo(result.calories()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.protein()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.carbs()));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should handle AI response with extra text before/after")
        void shouldHandleExtraText() throws Exception {
            String cloudflareResponse = """
                {"result":{"response":"Based on the image, I can see:\\nFOOD: Burger with fries\\nCALORIES: 850\\nPROTEIN: 35g\\nCARBS: 78g\\nFAT: 45g\\nFIBER: 5g\\nWEIGHT: 400g\\nThis looks like a typical fast food meal."},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            // Should still extract the structured data
            assertTrue(result.isValid());
            assertEquals(0, new BigDecimal("850").compareTo(result.calories()));
        }

        @Test
        @DisplayName("Should handle escaped newlines in response")
        void shouldHandleEscapedNewlines() throws Exception {
            String cloudflareResponse = "{\"result\":{\"response\":\"FOOD: Salad\\nCALORIES: 150\\nPROTEIN: 5g\\nCARBS: 20g\\nFAT: 7g\\nFIBER: 4g\\nWEIGHT: 200g\"},\"success\":true}";

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertTrue(result.isValid());
            assertEquals("Salad", result.description());
        }

        @Test
        @DisplayName("Should handle response with quotes in food description")
        void shouldHandleQuotesInDescription() throws Exception {
            String cloudflareResponse = """
                {"result":{"response":"FOOD: \\"Mom's\\" homemade lasagna\\nCALORIES: 550\\nPROTEIN: 28g\\nCARBS: 45g\\nFAT: 28g\\nFIBER: 4g\\nWEIGHT: 350g"},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertTrue(result.isValid());
            assertTrue(result.description().contains("lasagna"));
        }

        @Test
        @DisplayName("Should mark result as invalid when calories are missing")
        void shouldMarkInvalidWhenCaloriesMissing() throws Exception {
            String cloudflareResponse = """
                {"result":{"response":"FOOD: Unknown food\\nPROTEIN: 10g\\nCARBS: 20g\\nFAT: 5g"},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("Should mark result as invalid when description starts with Error")
        void shouldMarkInvalidWhenError() throws Exception {
            // Simulate an error response
            PhotoNutritionService.NutritionEstimate errorResult = 
                new PhotoNutritionService.NutritionEstimate(
                    "Error: Could not analyze image",
                    new BigDecimal("100"),
                    null, null, null, null, null
                );

            assertFalse(errorResult.isValid());
        }
    }

    @Nested
    @DisplayName("Real-World AI Response Samples")
    class RealWorldSamples {

        @Test
        @DisplayName("Should parse typical breakfast photo analysis")
        void shouldParseBreakfastAnalysis() throws Exception {
            String cloudflareResponse = """
                {"result":{"response":"FOOD: Scrambled eggs with toast and bacon\\nCALORIES: 520\\nPROTEIN: 28g\\nCARBS: 32g\\nFAT: 32g\\nFIBER: 2g\\nWEIGHT: 280g"},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertTrue(result.isValid());
            assertEquals("Scrambled eggs with toast and bacon", result.description());
            assertEquals(0, new BigDecimal("520").compareTo(result.calories()));
            assertEquals(0, new BigDecimal("28").compareTo(result.protein()));
        }

        @Test
        @DisplayName("Should parse multi-item meal photo analysis")
        void shouldParseMultiItemMeal() throws Exception {
            String cloudflareResponse = """
                {"result":{"response":"FOOD: Grilled salmon fillet with quinoa, roasted asparagus, and lemon butter sauce\\nCALORIES: 680\\nPROTEIN: 48g\\nCARBS: 35g\\nFAT: 38g\\nFIBER: 6g\\nWEIGHT: 420g"},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertTrue(result.isValid());
            assertTrue(result.description().contains("salmon"));
            assertTrue(result.description().contains("quinoa"));
            assertEquals(0, new BigDecimal("680").compareTo(result.calories()));
        }

        @Test
        @DisplayName("Should parse snack photo analysis")
        void shouldParseSnackAnalysis() throws Exception {
            String cloudflareResponse = """
                {"result":{"response":"FOOD: Handful of mixed nuts (almonds, cashews, walnuts)\\nCALORIES: 275\\nPROTEIN: 8g\\nCARBS: 10g\\nFAT: 24g\\nFIBER: 3g\\nWEIGHT: 45g"},"success":true}
                """;

            PhotoNutritionService.NutritionEstimate result = parseResponse(cloudflareResponse);

            assertTrue(result.isValid());
            assertEquals(0, new BigDecimal("275").compareTo(result.calories()));
            assertEquals(0, new BigDecimal("45").compareTo(result.estimatedWeightGrams()));
        }
    }

    @Nested
    @DisplayName("Service Availability")
    class ServiceAvailability {

        @Test
        @DisplayName("Should report unavailable when credentials are empty")
        void shouldReportUnavailableWithEmptyCredentials() {
            PhotoNutritionService emptyService = new PhotoNutritionService("", "", "", 0);
            assertFalse(emptyService.isAvailable());
        }

        @Test
        @DisplayName("Should report unavailable when account ID is blank")
        void shouldReportUnavailableWithBlankAccountId() {
            PhotoNutritionService blankService = new PhotoNutritionService("   ", "some-token", "", 0);
            assertFalse(blankService.isAvailable());
        }

        @Test
        @DisplayName("Should report unavailable when token is null")
        void shouldReportUnavailableWithNullToken() {
            PhotoNutritionService nullTokenService = new PhotoNutritionService("account-123", null, "", 0);
            assertFalse(nullTokenService.isAvailable());
        }

        @Test
        @DisplayName("Should report available when both credentials are provided")
        void shouldReportAvailableWithCredentials() {
            PhotoNutritionService configuredService = new PhotoNutritionService("account-123", "token-abc", "", 0);
            assertTrue(configuredService.isAvailable());
        }
    }
}
