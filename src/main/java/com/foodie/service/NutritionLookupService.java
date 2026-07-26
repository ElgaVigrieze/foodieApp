package com.foodie.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.foodie.model.Product;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

/**
 * Fetches nutrition data from Open Food Facts API.
 * https://wiki.openfoodfacts.org/API
 */
@Service
@Slf4j
public class NutritionLookupService {

    private static final String SEARCH_URL = "https://world.openfoodfacts.org/cgi/search.pl";

    private final RestClient restClient;

    public NutritionLookupService(
            @Value("${app.proxy.host:}") String proxyHost,
            @Value("${app.proxy.port:0}") int proxyPort) {

        SimpleClientHttpRequestFactory factory = new TrustAllRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);

        if (!proxyHost.isBlank() && proxyPort > 0) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
            log.info("NutritionLookupService using proxy: {}:{}", proxyHost, proxyPort);
        }

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Search Open Food Facts by product name and return nutrition per 100g.
     * Returns empty if no result found or API is unreachable.
     */
    public Optional<NutritionInfo> lookup(String productName) {
        try {
            SearchResponse response = restClient.get()
                    .uri(SEARCH_URL + "?search_terms={name}&search_simple=1&action=process&json=1&page_size=5&fields=product_name,nutriments", productName)
                    .retrieve()
                    .body(SearchResponse.class);

            if (response == null || response.products == null || response.products.isEmpty()) {
                log.info("No results found for: {}", productName);
                return Optional.empty();
            }

            for (OpenFoodFactsProduct product : response.products) {
                if (product.nutriments != null && product.nutriments.hasData()) {
                    NutritionInfo info = NutritionInfo.from(product.nutriments);
                    log.info("Found nutrition for '{}': {} kcal, {}g protein", productName, info.calories, info.protein);
                    return Optional.of(info);
                }
            }

            log.info("No nutrition data in results for: {}", productName);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Failed to lookup nutrition for '{}': {}", productName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lookup a product by barcode on Open Food Facts.
     * Returns product name + nutrition info if found.
     */
    public Optional<BarcodeResult> lookupBarcode(String barcode) {
        try {
            String url = "https://world.openfoodfacts.org/api/v2/product/" + barcode + ".json?fields=product_name,nutriments,brands,quantity";
            String response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (response == null || !response.contains("\"status\":1")) {
                log.info("Barcode not found on Open Food Facts: {}", barcode);
                return Optional.empty();
            }

            // Parse product name
            String name = extractJsonString(response, "product_name");
            String brands = extractJsonString(response, "brands");
            String quantity = extractJsonString(response, "quantity");

            if (name == null || name.isBlank()) {
                name = brands != null ? brands : "Unknown product";
            } else if (brands != null && !brands.isBlank() && !name.toLowerCase().contains(brands.toLowerCase())) {
                name = brands + " " + name;
            }

            // Parse nutriments
            BigDecimal calories = extractJsonNumber(response, "energy-kcal_100g");
            BigDecimal protein = extractJsonNumber(response, "proteins_100g");
            BigDecimal carbs = extractJsonNumber(response, "carbohydrates_100g");
            BigDecimal fat = extractJsonNumber(response, "fat_100g");
            BigDecimal fiber = extractJsonNumber(response, "fiber_100g");
            BigDecimal sugar = extractJsonNumber(response, "sugars_100g");

            log.info("Found barcode {}: {} ({} kcal/100g)", barcode, name, calories);
            return Optional.of(new BarcodeResult(name, quantity, calories, protein, carbs, fat, fiber, sugar));

        } catch (Exception e) {
            log.warn("Failed to lookup barcode '{}': {}", barcode, e.getMessage());
            return Optional.empty();
        }
    }

    private String extractJsonString(String json, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private BigDecimal extractJsonNumber(String json, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*([\\d.]+)");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    public record BarcodeResult(
            String name,
            String quantity,
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal carbs,
            BigDecimal fat,
            BigDecimal fiber,
            BigDecimal sugar
    ) {}

    /**
     * Fill in empty nutrition fields on a Product from Open Food Facts.
     * Only fills fields that are currently null.
     */
    public void autoFillNutrition(Product product) {
        if (product.getName() == null || product.getName().isBlank()) return;
        if (hasAllNutritionFilled(product)) return;

        lookup(product.getName()).ifPresent(info -> {
            if (product.getCalories() == null) product.setCalories(info.calories);
            if (product.getProtein() == null) product.setProtein(info.protein);
            if (product.getCarbs() == null) product.setCarbs(info.carbs);
            if (product.getFat() == null) product.setFat(info.fat);
            if (product.getFiber() == null) product.setFiber(info.fiber);
            if (product.getSugar() == null) product.setSugar(info.sugar);
        });
    }

    private boolean hasAllNutritionFilled(Product product) {
        return product.getCalories() != null
                && product.getProtein() != null
                && product.getCarbs() != null
                && product.getFat() != null
                && product.getFiber() != null
                && product.getSugar() != null;
    }

    // ── SSL Trust-All Factory (for corporate proxy MITM certs) ─────────────

    private static class TrustAllRequestFactory extends SimpleClientHttpRequestFactory {
        private static final SSLContext TRUST_ALL_SSL_CONTEXT;

        static {
            try {
                TRUST_ALL_SSL_CONTEXT = SSLContext.getInstance("TLS");
                TRUST_ALL_SSL_CONTEXT.init(null, new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                }, null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create trust-all SSL context", e);
            }
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            if (connection instanceof HttpsURLConnection httpsConn) {
                httpsConn.setSSLSocketFactory(TRUST_ALL_SSL_CONTEXT.getSocketFactory());
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }
            super.prepareConnection(connection, httpMethod);
        }
    }

    // ── Response DTOs ──────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchResponse {
        private List<OpenFoodFactsProduct> products;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenFoodFactsProduct {
        @JsonProperty("product_name")
        private String productName;
        private Nutriments nutriments;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Nutriments {
        @JsonProperty("energy-kcal_100g")
        private Double energyKcal100g;

        @JsonProperty("proteins_100g")
        private Double proteins100g;

        @JsonProperty("carbohydrates_100g")
        private Double carbohydrates100g;

        @JsonProperty("fat_100g")
        private Double fat100g;

        @JsonProperty("fiber_100g")
        private Double fiber100g;

        @JsonProperty("sugars_100g")
        private Double sugars100g;

        public boolean hasData() {
            return energyKcal100g != null || proteins100g != null || fat100g != null;
        }
    }

    /**
     * Normalized nutrition info per 100g.
     */
    public record NutritionInfo(
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal carbs,
            BigDecimal fat,
            BigDecimal fiber,
            BigDecimal sugar
    ) {
        public static NutritionInfo from(Nutriments n) {
            return new NutritionInfo(
                    toBigDecimal(n.energyKcal100g),
                    toBigDecimal(n.proteins100g),
                    toBigDecimal(n.carbohydrates100g),
                    toBigDecimal(n.fat100g),
                    toBigDecimal(n.fiber100g),
                    toBigDecimal(n.sugars100g)
            );
        }

        private static BigDecimal toBigDecimal(Double value) {
            return value != null ? BigDecimal.valueOf(value) : BigDecimal.ZERO;
        }
    }
}
