package com.foodie.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * HTTP client for Cloudflare Workers AI API.
 * Handles authentication, request building, and response parsing.
 * Used by PhotoNutritionService and RecipeScraperService.
 */
@Component
@Slf4j
public class CloudflareAiClient {

    // Available models
    public static final String TEXT_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
    public static final String VISION_MODEL = "@cf/meta/llama-3.2-11b-vision-instruct";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String accountId;
    private final String apiToken;

    public CloudflareAiClient(
            @Value("${app.cloudflare.account-id:}") String accountId,
            @Value("${app.cloudflare.api-token:}") String apiToken,
            @Value("${app.cloudflare.connect-timeout-ms:15000}") int connectTimeout,
            @Value("${app.cloudflare.read-timeout-ms:60000}") int readTimeout,
            ObjectMapper objectMapper) {
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Check if Cloudflare AI is configured and available.
     */
    public boolean isAvailable() {
        return accountId != null && !accountId.isBlank()
                && apiToken != null && !apiToken.isBlank();
    }

    /**
     * Call a text model with a simple prompt.
     *
     * @param model the model identifier (use TEXT_MODEL or VISION_MODEL constants)
     * @param systemPrompt optional system prompt (can be null)
     * @param userPrompt the user prompt
     * @param maxTokens maximum tokens in response
     * @return the response text, or empty if failed
     */
    public Optional<String> callTextModel(String model, String systemPrompt, String userPrompt, int maxTokens) {
        if (!isAvailable()) {
            log.warn("Cloudflare AI not configured — skipping AI call");
            return Optional.empty();
        }

        try {
            TextRequest request = new TextRequest();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                request.messages.add(new Message("system", systemPrompt));
            }
            request.messages.add(new Message("user", userPrompt));
            request.maxTokens = maxTokens;

            String requestBody = objectMapper.writeValueAsString(request);
            return callModel(model, requestBody);
        } catch (Exception e) {
            log.error("Failed to build text request: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Call a vision model with an image.
     *
     * @param model the model identifier (typically VISION_MODEL)
     * @param prompt the text prompt
     * @param base64Image the base64-encoded image
     * @param mimeType the image MIME type (e.g., "image/jpeg")
     * @param maxTokens maximum tokens in response
     * @return the response text, or empty if failed
     */
    public Optional<String> callVisionModel(String model, String prompt, String base64Image, String mimeType, int maxTokens) {
        if (!isAvailable()) {
            log.warn("Cloudflare AI not configured — skipping AI call");
            return Optional.empty();
        }

        // Vision requests use a different format with content array
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
              "max_tokens": %d
            }
            """.formatted(escapeJson(prompt), mimeType, base64Image, maxTokens);

        return callModel(model, requestBody);
    }

    /**
     * Call a model with a raw JSON request body.
     * Use this for custom request formats not covered by other methods.
     *
     * @param model the model identifier
     * @param requestBody the raw JSON request body
     * @return the response text, or empty if failed
     */
    public Optional<String> callModel(String model, String requestBody) {
        if (!isAvailable()) {
            log.warn("Cloudflare AI not configured — skipping AI call");
            return Optional.empty();
        }

        try {
            String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/" + model;

            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiToken)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.debug("Cloudflare AI raw response: {}", response);
            return extractResponseText(response);
        } catch (Exception e) {
            log.error("Cloudflare AI call failed ({}): {}", model, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extract the response text from Cloudflare API envelope.
     * Handles multiple response formats:
     * 1. {"result":{"response":"..."}} — Workers AI text format (string)
     * 2. {"result":{"response":{"response":"..."}}} — Nested response format
     * 3. {"result":{"choices":[{"message":{"content":"..."}}]}} — OpenAI-compatible format
     */
    private Optional<String> extractResponseText(String json) {
        if (json == null) return Optional.empty();

        try {
            var tree = objectMapper.readTree(json);
            var result = tree.path("result");

            if (result.isMissingNode()) {
                log.warn("No result in Cloudflare response");
                return Optional.empty();
            }

            // Format 1: result.response as string
            var response = result.path("response");
            if (!response.isMissingNode()) {
                if (response.isTextual()) {
                    return Optional.of(response.asText());
                }
                // Format 2: result.response as object with nested response field
                if (response.isObject()) {
                    var nestedResponse = response.path("response");
                    if (nestedResponse.isTextual()) {
                        return Optional.of(nestedResponse.asText());
                    }
                    // Try content field as fallback
                    var content = response.path("content");
                    if (content.isTextual()) {
                        return Optional.of(content.asText());
                    }
                }
            }

            // Format 3: result.choices[].message.content (OpenAI-compatible)
            var choices = result.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                var content = choices.get(0).path("message").path("content");
                if (content.isTextual()) {
                    return Optional.of(content.asText());
                }
            }

            log.warn("Could not extract response text from Cloudflare response: {}", json);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to parse Cloudflare response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }

    // ── Request DTOs ───────────────────────────────────────────────────────

    @Data
    private static class TextRequest {
        private List<Message> messages = new java.util.ArrayList<>();
        private int maxTokens;

        @com.fasterxml.jackson.annotation.JsonProperty("max_tokens")
        public int getMaxTokens() {
            return maxTokens;
        }
    }

    @Data
    @lombok.AllArgsConstructor
    private static class Message {
        private String role;
        private String content;
    }
}
