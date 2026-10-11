package com.foodie.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * HTTP client for Open Food Facts API.
 * Handles all HTTP communication and JSON parsing.
 *
 * @see <a href="https://wiki.openfoodfacts.org/API">Open Food Facts API</a>
 */
@Component
@Slf4j
public class OpenFoodFactsClient {

    private static final String BASE_URL = "https://world.openfoodfacts.org";
    private static final String SEARCH_URL = BASE_URL + "/cgi/search.pl";
    private static final String PRODUCT_URL = BASE_URL + "/api/v2/product/";

    private final RestClient restClient;

    public OpenFoodFactsClient(
            @Value("${app.open-food-facts.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${app.open-food-facts.read-timeout-ms:10000}") int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Search for products by name.
     *
     * @param productName the product name to search for
     * @return search response with matching products, or empty if not found or error
     */
    public Optional<SearchResponse> searchByName(String productName) {
        try {
            SearchResponse response = restClient.get()
                    .uri(SEARCH_URL + "?search_terms={name}&search_simple=1&action=process&json=1&page_size=5&fields=product_name,nutriments", productName)
                    .retrieve()
                    .body(SearchResponse.class);

            if (response == null || response.getProducts() == null || response.getProducts().isEmpty()) {
                log.info("No results found for: {}", productName);
                return Optional.empty();
            }

            return Optional.of(response);

        } catch (Exception e) {
            log.warn("Failed to search for '{}': {}", productName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lookup a product by barcode.
     *
     * @param barcode the product barcode (EAN/UPC)
     * @return barcode response with product details, or empty if not found or error
     */
    public Optional<BarcodeResponse> getByBarcode(String barcode) {
        try {
            String url = PRODUCT_URL + barcode + ".json?fields=product_name,nutriments,brands,quantity";
            BarcodeApiResponse apiResponse = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(BarcodeApiResponse.class);

            if (apiResponse == null || apiResponse.getStatus() != 1 || apiResponse.getProduct() == null) {
                log.info("Barcode not found on Open Food Facts: {}", barcode);
                return Optional.empty();
            }

            BarcodeResponse response = mapToBarcodeResponse(apiResponse.getProduct());
            log.info("Found barcode {}: {} ({} kcal/100g)", barcode, response.getName(), response.getCalories());
            return Optional.of(response);

        } catch (Exception e) {
            log.warn("Failed to lookup barcode '{}': {}", barcode, e.getMessage());
            return Optional.empty();
        }
    }

    private BarcodeResponse mapToBarcodeResponse(BarcodeProduct product) {
        String name = product.getProductName();
        String brands = product.getBrands();

        // Build display name
        if (name == null || name.isBlank()) {
            name = brands != null ? brands : "Unknown product";
        } else if (brands != null && !brands.isBlank() && !name.toLowerCase().contains(brands.toLowerCase())) {
            name = brands + " " + name;
        }

        Nutriments n = product.getNutriments();
        return BarcodeResponse.builder()
                .name(name)
                .quantity(product.getQuantity())
                .calories(n != null ? n.getEnergyKcal100g() : null)
                .protein(n != null ? n.getProteins100g() : null)
                .carbs(n != null ? n.getCarbohydrates100g() : null)
                .fat(n != null ? n.getFat100g() : null)
                .fiber(n != null ? n.getFiber100g() : null)
                .sugar(n != null ? n.getSugars100g() : null)
                .build();
    }

    // ── Response DTOs ──────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResponse {
        private List<Product> products;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        @JsonProperty("product_name")
        private String productName;
        private Nutriments nutriments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BarcodeApiResponse {
        private int status;
        private BarcodeProduct product;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BarcodeProduct {
        @JsonProperty("product_name")
        private String productName;
        private String brands;
        private String quantity;
        private Nutriments nutriments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Nutriments {
        @JsonProperty("energy-kcal_100g")
        private BigDecimal energyKcal100g;

        @JsonProperty("proteins_100g")
        private BigDecimal proteins100g;

        @JsonProperty("carbohydrates_100g")
        private BigDecimal carbohydrates100g;

        @JsonProperty("fat_100g")
        private BigDecimal fat100g;

        @JsonProperty("fiber_100g")
        private BigDecimal fiber100g;

        @JsonProperty("sugars_100g")
        private BigDecimal sugars100g;

        public boolean hasData() {
            return energyKcal100g != null || proteins100g != null || fat100g != null;
        }
    }

    @Data
    @lombok.Builder
    public static class BarcodeResponse {
        private String name;
        private String quantity;
        private BigDecimal calories;
        private BigDecimal protein;
        private BigDecimal carbs;
        private BigDecimal fat;
        private BigDecimal fiber;
        private BigDecimal sugar;
    }
}
