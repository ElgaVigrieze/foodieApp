package com.foodie.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PhotoNutritionService {

    private final RestClient restClient;
    private final String accountId;
    private final String apiToken;

    public PhotoNutritionService(
            @Value("${app.cloudflare.account-id:}") String accountId,
            @Value("${app.cloudflare.api-token:}") String apiToken,
            @Value("${app.proxy.host:}") String proxyHost,
            @Value("${app.proxy.port:0}") int proxyPort) {
        this.accountId = accountId;
        this.apiToken = apiToken;

        try {
            // Bypass SSL verification (corporate proxy intercepts certificates)
            TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());

            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(15000);
            factory.setReadTimeout(60000);
            if (!proxyHost.isBlank() && proxyPort > 0) {
                factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
            }
            this.restClient = RestClient.builder().requestFactory(factory).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to init PhotoNutritionService", e);
        }
    }

    public boolean isAvailable() {
        return accountId != null && !accountId.isBlank()
                && apiToken != null && !apiToken.isBlank();
    }

    /**
     * Analyze a food photo and return estimated nutrition using Cloudflare Workers AI.
     */
    public NutritionEstimate analyzePhoto(byte[] imageBytes, String mimeType) {
        if (!isAvailable()) {
            return new NutritionEstimate("Photo analysis not configured. Set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN.", null, null, null, null, null, null);
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String prompt = "Analyze this food photo. Estimate the total nutritional content of everything visible. " +
                "Respond ONLY in this exact format:\\nFOOD: <brief description>\\nCALORIES: <number>\\nPROTEIN: <number>g\\nCARBS: <number>g\\nFAT: <number>g\\nFIBER: <number>g\\nWEIGHT: <number>g";

        String requestBody = """
            {
              "messages": [
                {
                  "role": "user",
                  "content": [
                    {"type": "text", "text": "%s"},
                    {"type": "image_url", "image_url": {"url": "data:%s;base64,%s"}}
                  ]
                }
              ],
              "max_tokens": 512
            }
            """.formatted(prompt, mimeType, base64Image);

        try {
            String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId +
                    "/ai/run/@cf/meta/llama-3.2-11b-vision-instruct";

            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiToken)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.info("Cloudflare AI response: {}", response);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Cloudflare AI error: {}", e.getMessage());
            return new NutritionEstimate("Error: " + e.getMessage(), null, null, null, null, null, null);
        }
    }

    private NutritionEstimate parseResponse(String json) {
        // Cloudflare response format: {"result":{"response":"..."},"success":true,...}
        // Extract the response text
        Pattern responsePattern = Pattern.compile("\"response\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher responseMatcher = responsePattern.matcher(json);

        if (!responseMatcher.find()) {
            log.warn("Could not find 'response' in: {}", json);
            return new NutritionEstimate("Could not parse response", null, null, null, null, null, null);
        }

        String text = responseMatcher.group(1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\/", "/");
        log.info("Parsed AI text: {}", text);

        String food = extractValue(text, "FOOD:\\s*(.+)");
        BigDecimal calories = extractNumber(text, "CALORIES:\\s*(\\d+\\.?\\d*)");
        BigDecimal protein = extractNumber(text, "PROTEIN:\\s*(\\d+\\.?\\d*)");
        BigDecimal carbs = extractNumber(text, "CARBS:\\s*(\\d+\\.?\\d*)");
        BigDecimal fat = extractNumber(text, "FAT:\\s*(\\d+\\.?\\d*)");
        BigDecimal fiber = extractNumber(text, "FIBER:\\s*(\\d+\\.?\\d*)");
        BigDecimal weight = extractNumber(text, "WEIGHT:\\s*(\\d+\\.?\\d*)");

        return new NutritionEstimate(food, calories, protein, carbs, fat, fiber, weight);
    }

    private String extractValue(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private BigDecimal extractNumber(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    public record NutritionEstimate(
            String description,
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal carbs,
            BigDecimal fat,
            BigDecimal fiber,
            BigDecimal estimatedWeightGrams
    ) {
        public boolean isValid() {
            return calories != null && description != null && !description.startsWith("Error") && !description.startsWith("Could not");
        }
    }
}
