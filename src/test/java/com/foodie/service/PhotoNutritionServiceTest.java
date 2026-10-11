package com.foodie.service;

import com.foodie.client.CloudflareAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@link PhotoNutritionService}.
 * 
 * Tests the parsing of Cloudflare AI vision model responses into structured nutrition data.
 * Uses mocked CloudflareAiClient to test parsing logic without actual API calls.
 */
@ExtendWith(MockitoExtension.class)
class PhotoNutritionServiceTest {

    @Mock
    private CloudflareAiClient cloudflareAiClient;

    private PhotoNutritionService service;

    @BeforeEach
    void setUp() {
        service = new PhotoNutritionService(cloudflareAiClient);
    }

    @Nested
    @DisplayName("Photo Analysis Response Parsing")
    class PhotoAnalysisResponseParsing {

        @Test
        @DisplayName("Should parse well-formed AI response")
        void shouldParseWellFormedResponse() {
            String aiResponse = "FOOD: Grilled chicken breast with steamed broccoli\nCALORIES: 350\nPROTEIN: 42g\nCARBS: 12g\nFAT: 14g\nFIBER: 4g\nWEIGHT: 300g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

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
        void shouldHandleDecimalValues() {
            String aiResponse = "FOOD: Greek yogurt with honey\nCALORIES: 185.5\nPROTEIN: 12.3g\nCARBS: 24.7g\nFAT: 4.2g\nFIBER: 0.5g\nWEIGHT: 200g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

            assertTrue(result.isValid());
            assertEquals(0, new BigDecimal("185.5").compareTo(result.calories()));
            assertEquals(0, new BigDecimal("12.3").compareTo(result.protein()));
        }

        @Test
        @DisplayName("Should return error when AI returns empty response")
        void shouldReturnErrorWhenEmptyResponse() {
            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.empty());

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

            assertFalse(result.isValid());
            assertTrue(result.description().startsWith("Error"));
        }
    }

    @Nested
    @DisplayName("Nutrition Value Extraction")
    class NutritionValueExtraction {

        @Test
        @DisplayName("Should extract all nutrition fields correctly")
        void shouldExtractAllNutritionFields() {
            String aiResponse = "FOOD: Pasta with tomato sauce\nCALORIES: 450\nPROTEIN: 15g\nCARBS: 65g\nFAT: 12g\nFIBER: 6g\nWEIGHT: 350g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

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
        void shouldHandleMissingOptionalFields() {
            String aiResponse = "FOOD: Apple\nCALORIES: 95\nPROTEIN: 0g\nCARBS: 25g\nFAT: 0g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

            assertEquals("Apple", result.description());
            assertEquals(0, new BigDecimal("95").compareTo(result.calories()));
            assertNull(result.fiber());
            assertNull(result.estimatedWeightGrams());
        }

        @Test
        @DisplayName("Should handle zero values")
        void shouldHandleZeroValues() {
            String aiResponse = "FOOD: Black coffee\nCALORIES: 2\nPROTEIN: 0g\nCARBS: 0g\nFAT: 0g\nFIBER: 0g\nWEIGHT: 240g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

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
        void shouldHandleExtraText() {
            String aiResponse = "Based on the image, I can see:\nFOOD: Burger with fries\nCALORIES: 850\nPROTEIN: 35g\nCARBS: 78g\nFAT: 45g\nFIBER: 5g\nWEIGHT: 400g\nThis looks like a typical fast food meal.";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

            assertTrue(result.isValid());
            assertEquals(0, new BigDecimal("850").compareTo(result.calories()));
        }

        @Test
        @DisplayName("Should mark result as invalid when calories are missing")
        void shouldMarkInvalidWhenCaloriesMissing() {
            String aiResponse = "FOOD: Unknown food\nPROTEIN: 10g\nCARBS: 20g\nFAT: 5g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("Should mark result as invalid when description starts with Error")
        void shouldMarkInvalidWhenError() {
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
        void shouldParseBreakfastAnalysis() {
            String aiResponse = "FOOD: Scrambled eggs with toast and bacon\nCALORIES: 520\nPROTEIN: 28g\nCARBS: 32g\nFAT: 32g\nFIBER: 2g\nWEIGHT: 280g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

            assertTrue(result.isValid());
            assertEquals("Scrambled eggs with toast and bacon", result.description());
            assertEquals(0, new BigDecimal("520").compareTo(result.calories()));
            assertEquals(0, new BigDecimal("28").compareTo(result.protein()));
        }

        @Test
        @DisplayName("Should parse multi-item meal photo analysis")
        void shouldParseMultiItemMeal() {
            String aiResponse = "FOOD: Grilled salmon fillet with quinoa, roasted asparagus, and lemon butter sauce\nCALORIES: 680\nPROTEIN: 48g\nCARBS: 35g\nFAT: 38g\nFIBER: 6g\nWEIGHT: 420g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

            assertTrue(result.isValid());
            assertTrue(result.description().contains("salmon"));
            assertTrue(result.description().contains("quinoa"));
            assertEquals(0, new BigDecimal("680").compareTo(result.calories()));
        }

        @Test
        @DisplayName("Should parse snack photo analysis")
        void shouldParseSnackAnalysis() {
            String aiResponse = "FOOD: Handful of mixed nuts (almonds, cashews, walnuts)\nCALORIES: 275\nPROTEIN: 8g\nCARBS: 10g\nFAT: 24g\nFIBER: 3g\nWEIGHT: 45g";

            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            when(cloudflareAiClient.callVisionModel(any(), any(), any(), any(), anyInt()))
                    .thenReturn(Optional.of(aiResponse));

            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");

            assertTrue(result.isValid());
            assertEquals(0, new BigDecimal("275").compareTo(result.calories()));
            assertEquals(0, new BigDecimal("45").compareTo(result.estimatedWeightGrams()));
        }
    }

    @Nested
    @DisplayName("Service Availability")
    class ServiceAvailability {

        @Test
        @DisplayName("Should report unavailable when client is not available")
        void shouldReportUnavailableWhenClientNotAvailable() {
            when(cloudflareAiClient.isAvailable()).thenReturn(false);
            assertFalse(service.isAvailable());
        }

        @Test
        @DisplayName("Should report available when client is available")
        void shouldReportAvailableWhenClientAvailable() {
            when(cloudflareAiClient.isAvailable()).thenReturn(true);
            assertTrue(service.isAvailable());
        }

        @Test
        @DisplayName("Should return config message when not available")
        void shouldReturnConfigMessageWhenNotAvailable() {
            when(cloudflareAiClient.isAvailable()).thenReturn(false);
            
            PhotoNutritionService.NutritionEstimate result = service.analyzePhoto(new byte[]{1, 2, 3}, "image/jpeg");
            
            assertFalse(result.isValid());
            assertTrue(result.description().contains("not configured"));
        }
    }
}
