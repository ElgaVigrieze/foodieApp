package com.foodie.service;

import com.foodie.client.OpenFoodFactsClient;
import com.foodie.client.OpenFoodFactsClient.BarcodeResponse;
import com.foodie.client.OpenFoodFactsClient.Nutriments;
import com.foodie.client.OpenFoodFactsClient.Product;
import com.foodie.client.OpenFoodFactsClient.SearchResponse;
import com.foodie.controller.dto.NutritionBarcodeResult;
import com.foodie.controller.dto.NutritionInfo;
import com.foodie.model.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NutritionLookupService}.
 */
@ExtendWith(MockitoExtension.class)
class NutritionLookupServiceTest {

    @Mock
    private OpenFoodFactsClient openFoodFactsClient;

    @InjectMocks
    private NutritionLookupService service;

    @Nested
    @DisplayName("lookup by product name")
    class LookupByName {

        @Test
        @DisplayName("Should return nutrition info when product found with valid data")
        void shouldReturnNutritionWhenFound() {
            Nutriments nutriments = new Nutriments();
            nutriments.setEnergyKcal100g(new BigDecimal("250"));
            nutriments.setProteins100g(new BigDecimal("10.5"));
            nutriments.setCarbohydrates100g(new BigDecimal("30"));
            nutriments.setFat100g(new BigDecimal("12"));
            nutriments.setFiber100g(new BigDecimal("3.5"));
            nutriments.setSugars100g(new BigDecimal("8"));

            Product product = new Product();
            product.setProductName("Peanut Butter");
            product.setNutriments(nutriments);

            SearchResponse response = new SearchResponse();
            response.setProducts(List.of(product));

            when(openFoodFactsClient.searchByName("peanut butter")).thenReturn(Optional.of(response));

            Optional<NutritionInfo> result = service.lookup("peanut butter");

            assertThat(result).isPresent();
            assertThat(result.get().calories()).isEqualByComparingTo(new BigDecimal("250"));
            assertThat(result.get().protein()).isEqualByComparingTo(new BigDecimal("10.5"));
            assertThat(result.get().carbs()).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(result.get().fat()).isEqualByComparingTo(new BigDecimal("12"));
            assertThat(result.get().fiber()).isEqualByComparingTo(new BigDecimal("3.5"));
            assertThat(result.get().sugar()).isEqualByComparingTo(new BigDecimal("8"));
        }

        @Test
        @DisplayName("Should return empty when no products found")
        void shouldReturnEmptyWhenNoProductsFound() {
            when(openFoodFactsClient.searchByName("nonexistent")).thenReturn(Optional.empty());

            Optional<NutritionInfo> result = service.lookup("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should skip products without nutrition data")
        void shouldSkipProductsWithoutNutritionData() {
            // First product has no nutriments
            Product productWithoutData = new Product();
            productWithoutData.setProductName("Product 1");
            productWithoutData.setNutriments(null);

            // Second product has nutriments with data
            Nutriments nutriments = new Nutriments();
            nutriments.setEnergyKcal100g(new BigDecimal("100"));
            nutriments.setProteins100g(new BigDecimal("5"));

            Product productWithData = new Product();
            productWithData.setProductName("Product 2");
            productWithData.setNutriments(nutriments);

            SearchResponse response = new SearchResponse();
            response.setProducts(List.of(productWithoutData, productWithData));

            when(openFoodFactsClient.searchByName("test")).thenReturn(Optional.of(response));

            Optional<NutritionInfo> result = service.lookup("test");

            assertThat(result).isPresent();
            assertThat(result.get().calories()).isEqualByComparingTo(new BigDecimal("100"));
        }

        @Test
        @DisplayName("Should return empty when all products lack nutrition data")
        void shouldReturnEmptyWhenAllProductsLackData() {
            Nutriments emptyNutriments = new Nutriments(); // hasData() returns false

            Product product = new Product();
            product.setProductName("Product");
            product.setNutriments(emptyNutriments);

            SearchResponse response = new SearchResponse();
            response.setProducts(List.of(product));

            when(openFoodFactsClient.searchByName("test")).thenReturn(Optional.of(response));

            Optional<NutritionInfo> result = service.lookup("test");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("lookup by barcode")
    class LookupByBarcode {

        @Test
        @DisplayName("Should return barcode result when found")
        void shouldReturnBarcodeResultWhenFound() {
            BarcodeResponse barcodeResponse = BarcodeResponse.builder()
                    .name("Organic Milk")
                    .quantity("1L")
                    .calories(new BigDecimal("64"))
                    .protein(new BigDecimal("3.3"))
                    .carbs(new BigDecimal("4.8"))
                    .fat(new BigDecimal("3.6"))
                    .fiber(BigDecimal.ZERO)
                    .sugar(new BigDecimal("4.8"))
                    .build();

            when(openFoodFactsClient.getByBarcode("3017620422003")).thenReturn(Optional.of(barcodeResponse));

            Optional<NutritionBarcodeResult> result = service.lookupBarcode("3017620422003");

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("Organic Milk");
            assertThat(result.get().quantity()).isEqualTo("1L");
            assertThat(result.get().calories()).isEqualByComparingTo(new BigDecimal("64"));
        }

        @Test
        @DisplayName("Should return empty when barcode not found")
        void shouldReturnEmptyWhenBarcodeNotFound() {
            when(openFoodFactsClient.getByBarcode("0000000000000")).thenReturn(Optional.empty());

            Optional<NutritionBarcodeResult> result = service.lookupBarcode("0000000000000");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Nutriments hasData check")
    class NutrimentsHasData {

        @Test
        @DisplayName("Should return true when calories present")
        void shouldReturnTrueWhenCaloriesPresent() {
            Nutriments nutriments = new Nutriments();
            nutriments.setEnergyKcal100g(new BigDecimal("100"));

            assertThat(nutriments.hasData()).isTrue();
        }

        @Test
        @DisplayName("Should return true when protein present")
        void shouldReturnTrueWhenProteinPresent() {
            Nutriments nutriments = new Nutriments();
            nutriments.setProteins100g(new BigDecimal("10"));

            assertThat(nutriments.hasData()).isTrue();
        }

        @Test
        @DisplayName("Should return true when fat present")
        void shouldReturnTrueWhenFatPresent() {
            Nutriments nutriments = new Nutriments();
            nutriments.setFat100g(new BigDecimal("5"));

            assertThat(nutriments.hasData()).isTrue();
        }

        @Test
        @DisplayName("Should return false when no key nutrients present")
        void shouldReturnFalseWhenNoKeyNutrients() {
            Nutriments nutriments = new Nutriments();
            nutriments.setCarbohydrates100g(new BigDecimal("20"));
            nutriments.setFiber100g(new BigDecimal("5"));

            assertThat(nutriments.hasData()).isFalse();
        }

        @Test
        @DisplayName("Should return false when all null")
        void shouldReturnFalseWhenAllNull() {
            Nutriments nutriments = new Nutriments();

            assertThat(nutriments.hasData()).isFalse();
        }
    }

    @Nested
    @DisplayName("autoFillNutrition")
    class AutoFillNutrition {

        @Test
        @DisplayName("Should not modify product with empty name")
        void shouldNotModifyProductWithEmptyName() {
            com.foodie.model.db.Product product = new com.foodie.model.db.Product();
            product.setName("");
            product.setUnit(Unit.KG);

            service.autoFillNutrition(product);

            assertThat(product.getCalories()).isNull();
        }

        @Test
        @DisplayName("Should not modify product with null name")
        void shouldNotModifyProductWithNullName() {
            com.foodie.model.db.Product product = new com.foodie.model.db.Product();
            product.setName(null);
            product.setUnit(Unit.KG);

            service.autoFillNutrition(product);

            assertThat(product.getCalories()).isNull();
        }

        @Test
        @DisplayName("Should not modify product with all nutrition already filled")
        void shouldNotModifyFullyFilledProduct() {
            com.foodie.model.db.Product product = new com.foodie.model.db.Product();
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

            assertThat(product.getCalories()).isEqualByComparingTo(originalCalories);
        }

        @Test
        @DisplayName("Should fill missing nutrition fields from lookup")
        void shouldFillMissingFields() {
            Nutriments nutriments = new Nutriments();
            nutriments.setEnergyKcal100g(new BigDecimal("200"));
            nutriments.setProteins100g(new BigDecimal("15"));
            nutriments.setCarbohydrates100g(new BigDecimal("25"));
            nutriments.setFat100g(new BigDecimal("8"));
            nutriments.setFiber100g(new BigDecimal("4"));
            nutriments.setSugars100g(new BigDecimal("6"));

            Product apiProduct = new Product();
            apiProduct.setProductName("Chicken Breast");
            apiProduct.setNutriments(nutriments);

            SearchResponse response = new SearchResponse();
            response.setProducts(List.of(apiProduct));

            when(openFoodFactsClient.searchByName("Chicken Breast")).thenReturn(Optional.of(response));

            com.foodie.model.db.Product product = new com.foodie.model.db.Product();
            product.setName("Chicken Breast");
            product.setUnit(Unit.KG);
            // Only calories is filled
            product.setCalories(new BigDecimal("150"));

            service.autoFillNutrition(product);

            // Calories should remain unchanged (was already filled)
            assertThat(product.getCalories()).isEqualByComparingTo(new BigDecimal("150"));
            // Other fields should be filled
            assertThat(product.getProtein()).isEqualByComparingTo(new BigDecimal("15"));
            assertThat(product.getCarbs()).isEqualByComparingTo(new BigDecimal("25"));
            assertThat(product.getFat()).isEqualByComparingTo(new BigDecimal("8"));
            assertThat(product.getFiber()).isEqualByComparingTo(new BigDecimal("4"));
            assertThat(product.getSugar()).isEqualByComparingTo(new BigDecimal("6"));
        }
    }
}