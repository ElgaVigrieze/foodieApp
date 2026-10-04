package com.foodie.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured recipe data from:
 *  - a web URL (recipe blog, YouTube page, Instagram reel page)
 *  - an uploaded photo/screenshot
 *
 * Uses Cloudflare Workers AI (Llama 3.2 Vision / text) — same credentials
 * already configured for PhotoNutritionService.
 */
@Service
@Slf4j
public class RecipeScraperService {

    // Cloudflare text model — fast, handles long HTML summaries well
    private static final String TEXT_MODEL  = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
    // Vision model — same one used by PhotoNutritionService
    private static final String VISION_MODEL = "@cf/meta/llama-3.2-11b-vision-instruct";

    private final RestClient restClient;
    private final String accountId;
    private final String apiToken;

    public RecipeScraperService(
            @Value("${app.cloudflare.account-id:}") String accountId,
            @Value("${app.cloudflare.api-token:}") String apiToken,
            @Value("${app.proxy.host:}") String proxyHost,
            @Value("${app.proxy.port:0}") int proxyPort) {

        this.accountId = accountId;
        this.apiToken  = apiToken;

        try {
            TrustManager[] trustAll = new TrustManager[]{ new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslCtx.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(15_000);
            factory.setReadTimeout(90_000);
            if (!proxyHost.isBlank() && proxyPort > 0) {
                factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
                log.info("RecipeScraperService using proxy: {}:{}", proxyHost, proxyPort);
            }
            this.restClient = RestClient.builder().requestFactory(factory).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to init RecipeScraperService", e);
        }
    }

    public boolean isAvailable() {
        return accountId != null && !accountId.isBlank()
                && apiToken != null && !apiToken.isBlank();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Fetch a recipe from a URL (blog, YouTube, Instagram reel page, etc.)
     * and extract structured data via the Cloudflare text LLM.
     *
     * Strategy:
     * 1. Try to fetch and scrape the page HTML
     * 2. If scraping fails (bot protection, 403, etc.) — fall back to asking
     *    the AI to use its training knowledge about the URL directly
     */
    public RecipeExtract extractFromUrl(String url) {
        log.info("Extracting recipe from URL: {}", url);

        String pageText = fetchPageText(url);

        String prompt;
        if (pageText == null || pageText.isBlank()) {
            // Scraping failed — ask the AI to recall the recipe from its training data
            log.info("Page scraping failed for {}, asking AI to recall from training data", url);
            prompt = buildUrlFallbackPrompt(url);
        } else {
            if (pageText.length() > 8_000) pageText = pageText.substring(0, 8_000);
            prompt = buildTextPrompt(pageText);
        }

        String aiResponse = callTextModel(prompt);
        if (aiResponse == null) return fallbackExtract(url);

        RecipeExtract extract = parseAiResponse(aiResponse);
        log.info("Extracted recipe: {} ({} ingredients)", extract.name(), extract.ingredients().size());
        return extract;
    }

    /**
     * Extract a recipe from an image/screenshot using the Cloudflare vision model.
     * Same approach as PhotoNutritionService but with a recipe-focused prompt.
     */
    public RecipeExtract extractFromPhoto(byte[] imageBytes, String mimeType) {
        log.info("Extracting recipe from photo ({} bytes, {})", imageBytes.length, mimeType);

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String prompt = "This is a screenshot or photo of a recipe. " +
                "Extract ALL the recipe details and respond ONLY in this exact JSON format " +
                "(no extra text, no markdown code blocks):\n" + JSON_FORMAT_INSTRUCTIONS;

        String requestBody = "{\"messages\":[{\"role\":\"user\",\"content\":[" +
                "{\"type\":\"text\",\"text\":\"" + escapeJson(prompt) + "\"}," +
                "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:" + mimeType + ";base64," + base64 + "\"}}" +
                "]}],\"max_tokens\":2048}";

        String aiResponse = callModel(VISION_MODEL, requestBody);
        if (aiResponse == null) return fallbackExtract(null);

        RecipeExtract extract = parseAiResponse(aiResponse);
        log.info("Photo recipe extracted: {} ({} ingredients)", extract.name(), extract.ingredients().size());
        return extract;
    }

    // ── Page fetching ──────────────────────────────────────────────────────

    private String fetchPageText(String url) {
        try {
            String html = restClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "identity")
                    .retrieve()
                    .body(String.class);

            if (html == null) return null;
            return stripHtml(html);
        } catch (Exception e) {
            log.warn("Failed to fetch URL {}: {} — {}", url, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Very lightweight HTML → readable text conversion.
     * Removes scripts, styles, tags and collapses whitespace.
     */
    private String stripHtml(String html) {
        // Remove scripts and styles entirely
        String text = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                          .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                          // Convert common block tags to newlines so recipe steps stay readable
                          .replaceAll("(?i)<(br|p|li|h[1-6]|tr|div)[^>]*>", "\n")
                          // Strip all remaining tags
                          .replaceAll("<[^>]+>", " ")
                          // Decode common HTML entities
                          .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                          .replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
                          // Collapse whitespace
                          .replaceAll("[ \\t]+", " ")
                          .replaceAll("\\n{3,}", "\n\n")
                          .trim();
        return text;
    }

    // ── Cloudflare AI calls ────────────────────────────────────────────────

    private static final String JSON_FORMAT_INSTRUCTIONS = """
            {
              "name": "<meal name>",
              "category": "<one of: MAIN_COURSE, SOUP, SALAD, SNACK, DESSERT, DRINK, SAUCE, SIDE>",
              "servings": <number>,
              "instructions": "<preparation steps as plain text>",
              "ingredients": [
                {"name": "<ingredient name>", "quantity": <number>, "unit": "<g|kg|ml|L|piece>"},
                ...
              ]
            }

            Unit conversion rules — apply these BEFORE outputting:
            - 1 teaspoon (tsp) = 5 ml → use unit "ml"
            - 1 tablespoon (tbsp) = 15 ml → use unit "ml"
            - 1 cup = 240 ml → use unit "ml"
            - Solid/dry ingredients (flour, sugar, salt, spices, cheese, butter, etc.) → convert to grams, use unit "g"
            - Liquid ingredients (water, milk, oil, cream, broth, etc.) → convert to ml, use unit "ml"
            - Countable items (eggs, cloves, slices, pieces) → keep as number, use unit "piece"
            - Never output "tsp", "tbsp", "cup", "oz", "lb" or any other measurement — always convert first.""";

    private String buildTextPrompt(String pageText) {
        return "Extract the recipe from the following web page text. " +
               "Respond ONLY with valid JSON in this exact format (no markdown, no explanation):\n" +
               JSON_FORMAT_INSTRUCTIONS +
               "\n\nPage text:\n" + pageText;
    }

    private String buildUrlFallbackPrompt(String url) {
        return "The following URL points to a recipe page that could not be scraped. " +
               "Use your training knowledge to recall the recipe from this URL and respond " +
               "ONLY with valid JSON in this exact format (no markdown, no explanation):\n" +
               JSON_FORMAT_INSTRUCTIONS +
               "\n\nRecipe URL: " + url;
    }

    private String callTextModel(String prompt) {
        // Build the request body using Jackson-style manual serialisation so the
        // page text is safely embedded without double-escaping issues.
        // We serialise each message content field individually.
        String systemEscaped = escapeJson("You are a recipe extraction assistant. Always respond with valid JSON only.");
        String userEscaped   = escapeJson(prompt);
        String requestBody = "{\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + systemEscaped + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + userEscaped + "\"}" +
                "],\"max_tokens\":2048}";
        return callModel(TEXT_MODEL, requestBody);
    }

    private String callModel(String model, String requestBody) {
        if (!isAvailable()) {
            log.warn("Cloudflare AI not configured — skipping AI extraction");
            return null;
        }
        try {
            String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId +
                         "/ai/run/" + model;
            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiToken)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            log.info("Cloudflare AI raw response: {}", response);
            return extractResponseText(response);
        } catch (Exception e) {
            log.error("Cloudflare AI call failed ({}): {}", model, e.getMessage());
            return null;
        }
    }

    /** Pull the inner "response" string out of {"result":{"response":"..."},...} */
    private String extractResponseText(String json) {
        if (json == null) return null;

        // DOTALL so the match works even if Cloudflare's envelope has literal newlines
        Pattern p = Pattern.compile("\"response\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*?)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (!m.find()) {
            // Fallback: some Cloudflare models stream back the content directly
            // without a nested "response" key — try the whole body as-is
            log.warn("No 'response' field in AI output, trying raw body. Output: {}", json);
            return json;
        }
        return m.group(1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
    }

    // ── JSON parsing ───────────────────────────────────────────────────────

    /**
     * Parse the AI's free-text JSON response into a RecipeExtract.
     * Uses regex-based extraction so we're not dependent on an extra JSON library
     * and can tolerate minor formatting variations from the LLM.
     */
    RecipeExtract parseAiResponse(String text) {
        if (text == null || text.isBlank()) return fallbackExtract(null);

        // The model sometimes wraps the JSON in ```json ... ``` — strip that
        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        String name         = extractJsonString(text, "name");
        String category     = normaliseCategory(extractJsonString(text, "category"));
        int    servings     = extractJsonInt(text, "servings", 2);
        String instructions = extractJsonString(text, "instructions");

        List<RecipeExtract.IngredientLine> ingredients = parseIngredients(text);

        return new RecipeExtract(
                name != null ? name : "Imported Recipe",
                category,
                servings,
                instructions != null ? instructions : "",
                ingredients
        );
    }

    private List<RecipeExtract.IngredientLine> parseIngredients(String json) {
        List<RecipeExtract.IngredientLine> result = new ArrayList<>();

        // Find the ingredients array block
        Pattern arrayPattern = Pattern.compile("\"ingredients\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher arrayMatcher = arrayPattern.matcher(json);
        if (!arrayMatcher.find()) return result;

        String arrayContent = arrayMatcher.group(1);

        // Each ingredient object: {"name":"...","quantity":...,"unit":"..."}
        Pattern objPattern = Pattern.compile("\\{[^}]+\\}", Pattern.DOTALL);
        Matcher objMatcher = objPattern.matcher(arrayContent);

        while (objMatcher.find()) {
            String obj  = objMatcher.group();
            String name = extractJsonString(obj, "name");
            String unit = extractJsonString(obj, "unit");

            // quantity can be int or decimal
            BigDecimal quantity = extractJsonDecimal(obj, "quantity");

            if (name == null || name.isBlank() || quantity == null) continue;

            // Normalise to storage units: g → kg, ml → L
            BigDecimal normalisedQty = normaliseQuantity(quantity, unit);

            result.add(new RecipeExtract.IngredientLine(
                    name.trim(),
                    normalisedQty,
                    unit != null ? unit.toLowerCase() : "g"
            ));
        }
        return result;
    }

    /**
     * Convert raw AI units to the app's storage units (kg / L / piece).
     * Nutrition data in the app is per 100 g or per 100 ml, quantities stored in kg/L.
     */
    private BigDecimal normaliseQuantity(BigDecimal qty, String unit) {
        if (unit == null) return qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        return switch (unit.toLowerCase().trim()) {
            case "g", "gr", "gram", "grams"        -> qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
            case "ml", "milliliter", "milliliters"  -> qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
            case "kg", "kilogram", "kilograms"      -> qty;
            case "l", "liter", "liters", "litre"   -> qty;
            case "tbsp", "tablespoon"               -> qty.multiply(BigDecimal.valueOf(0.015));  // ~15 ml
            case "tsp", "teaspoon"                  -> qty.multiply(BigDecimal.valueOf(0.005));  // ~5 ml
            case "cup", "cups"                      -> qty.multiply(BigDecimal.valueOf(0.240));  // ~240 ml
            case "piece", "pieces", "pc", "pcs",
                 "slice", "slices", "clove", "cloves",
                 "whole", "large", "medium", "small" -> qty;  // PIECE unit — keep as-is
            default                                 -> qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        };
    }

    private String normaliseCategory(String raw) {
        if (raw == null) return "MAIN_COURSE";
        String upper = raw.toUpperCase().replace(" ", "_").replace("-", "_");
        return switch (upper) {
            case "MAIN_COURSE", "MAIN", "MAIN COURSE" -> "MAIN_COURSE";
            case "SOUP", "STEW"                        -> "SOUP";
            case "SALAD"                               -> "SALAD";
            case "SNACK", "APPETIZER", "STARTER"       -> "SNACK";
            case "DESSERT", "SWEET", "CAKE"            -> "DESSERT";
            case "DRINK", "SMOOTHIE", "BEVERAGE"       -> "DRINK";
            case "SAUCE", "DIP", "DRESSING"            -> "SAUCE";
            case "SIDE", "SIDE_DISH"                   -> "SIDE";
            default                                    -> "MAIN_COURSE";
        };
    }

    // ── Minimal JSON field extractors ──────────────────────────────────────

    private String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1).replace("\\n", "\n").replace("\\\"", "\"") : null;
    }

    private int extractJsonInt(String json, String key, int defaultVal) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultVal;
    }

    private BigDecimal extractJsonDecimal(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*([\\d.]+)");
        Matcher m = p.matcher(json);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    /** Bare-minimum fallback when the AI cannot extract anything. */
    private RecipeExtract fallbackExtract(String url) {
        return new RecipeExtract(
                "Imported Recipe",
                "MAIN_COURSE",
                2,
                url != null ? "See original recipe: " + url : "",
                List.of()
        );
    }
}
