package com.foodie.service;

import com.foodie.client.CloudflareAiClient;
import com.foodie.model.RecipeExtract;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts structured recipe data from:
 *  - a web URL (recipe blog, YouTube page, Instagram reel page)
 *  - an uploaded photo/screenshot
 *
 * Uses Cloudflare Workers AI via {@link CloudflareAiClient}.
 */
@Service
@Slf4j
public class RecipeScraperService {

    private final CloudflareAiClient cloudflareAiClient;
    private final RestClient webClient;

    public RecipeScraperService(
            CloudflareAiClient cloudflareAiClient,
            @Value("${app.cloudflare.connect-timeout-ms:15000}") int connectTimeout,
            @Value("${app.cloudflare.read-timeout-ms:60000}") int readTimeout) {
        this.cloudflareAiClient = cloudflareAiClient;

        // Separate RestClient for web scraping (not AI calls)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.webClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isAvailable() {
        return cloudflareAiClient.isAvailable();
    }

    // ── URL Validation (SSRF Protection) ───────────────────────────────────

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "[::1]", "metadata.google.internal"
    );

    /**
     * Validates a URL to prevent SSRF attacks.
     * Blocks: private IPs, localhost, cloud metadata endpoints, non-HTTP(S) schemes.
     */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL format: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP and HTTPS URLs are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must have a valid host");
        }

        String hostLower = host.toLowerCase();
        if (BLOCKED_HOSTS.contains(hostLower)) {
            throw new IllegalArgumentException("Access to " + host + " is not allowed");
        }

        if (hostLower.equals("169.254.169.254") ||
            hostLower.endsWith(".internal") ||
            hostLower.startsWith("metadata.")) {
            throw new IllegalArgumentException("Access to cloud metadata endpoints is not allowed");
        }

        try {
            InetAddress address = InetAddress.getByName(host);

            if (address.isLoopbackAddress()) {
                throw new IllegalArgumentException("Loopback addresses are not allowed");
            }
            if (address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("Private network addresses are not allowed");
            }
            if (address.isLinkLocalAddress()) {
                throw new IllegalArgumentException("Link-local addresses are not allowed");
            }
            if (address.isAnyLocalAddress()) {
                throw new IllegalArgumentException("Wildcard addresses are not allowed");
            }

            byte[] addr = address.getAddress();
            if (addr.length == 4) {
                int first = addr[0] & 0xFF;
                int second = addr[1] & 0xFF;

                if (first == 10) {
                    throw new IllegalArgumentException("Private network addresses (10.x.x.x) are not allowed");
                }
                if (first == 172 && second >= 16 && second <= 31) {
                    throw new IllegalArgumentException("Private network addresses (172.16-31.x.x) are not allowed");
                }
                if (first == 192 && second == 168) {
                    throw new IllegalArgumentException("Private network addresses (192.168.x.x) are not allowed");
                }
                if (first == 169 && second == 254) {
                    throw new IllegalArgumentException("Link-local addresses (169.254.x.x) are not allowed");
                }
            }

            if (addr.length == 16) {
                if ((addr[0] & 0xFF) == 0xfd) {
                    throw new IllegalArgumentException("IPv6 unique local addresses are not allowed");
                }
                if ((addr[0] & 0xFF) == 0xfe && (addr[1] & 0xC0) == 0x80) {
                    throw new IllegalArgumentException("IPv6 link-local addresses are not allowed");
                }
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not resolve hostname {}: {}", host, e.getMessage());
        }

        log.debug("URL validation passed for: {}", url);
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Fetch a recipe from a URL and extract structured data via Cloudflare AI.
     */
    public RecipeExtract extractFromUrl(String url) {
        log.info("Extracting recipe from URL: {}", url);
        validateUrl(url);

        String pageText = fetchPageText(url);

        String prompt;
        if (pageText == null || pageText.isBlank()) {
            log.info("Page scraping failed for {}, asking AI to recall from training data", url);
            prompt = buildUrlFallbackPrompt(url);
        } else {
            if (pageText.length() > 8_000) pageText = pageText.substring(0, 8_000);
            prompt = buildTextPrompt(pageText);
        }

        return cloudflareAiClient.callTextModel(
                        CloudflareAiClient.TEXT_MODEL,
                        "You are a recipe extraction assistant. Always respond with valid JSON only.",
                        prompt,
                        2048)
                .map(this::parseAiResponse)
                .orElseGet(() -> fallbackExtract(url));
    }

    /**
     * Extract a recipe from an image/screenshot using the Cloudflare vision model.
     */
    public RecipeExtract extractFromPhoto(byte[] imageBytes, String mimeType) {
        log.info("Extracting recipe from photo ({} bytes, {})", imageBytes.length, mimeType);

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String prompt = "This is a screenshot or photo of a recipe. " +
                "Extract ALL the recipe details and respond ONLY in this exact JSON format " +
                "(no extra text, no markdown code blocks):\n" + JSON_FORMAT_INSTRUCTIONS;

        return cloudflareAiClient.callVisionModel(
                        CloudflareAiClient.VISION_MODEL,
                        prompt,
                        base64,
                        mimeType,
                        2048)
                .map(this::parseAiResponse)
                .orElseGet(() -> fallbackExtract(null));
    }

    // ── Page fetching ──────────────────────────────────────────────────────

    private String fetchPageText(String url) {
        try {
            String html = webClient.get()
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

    private String stripHtml(String html) {
        return html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                   .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                   .replaceAll("(?i)<(br|p|li|h[1-6]|tr|div)[^>]*>", "\n")
                   .replaceAll("<[^>]+>", " ")
                   .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"")
                   .replaceAll("[ \\t]+", " ")
                   .replaceAll("\\n{3,}", "\n\n")
                   .trim();
    }

    // ── Prompt building ────────────────────────────────────────────────────

    private static final String JSON_FORMAT_INSTRUCTIONS = """
            {
              "name": "<the DISH title, e.g. 'Panna Cotta' or 'Chicken Tikka Masala' — NOT an ingredient name>",
              "category": "<one of: MAIN_COURSE, SOUP, SALAD, SNACK, DESSERT, DRINK, SAUCE, SIDE>",
              "servings": <number>,
              "instructions": "<preparation steps as plain text>",
              "ingredients": [
                {"name": "<ingredient name>", "quantity": <number>, "unit": "<g|kg|ml|L|piece>"},
                ...
              ]
            }

            Important rules:
            - "name" must be the recipe/dish title only — never an ingredient
            - Unit conversion rules — apply these BEFORE outputting:
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

    // ── JSON parsing ───────────────────────────────────────────────────────

    RecipeExtract parseAiResponse(String text) {
        if (text == null || text.isBlank()) return fallbackExtract(null);

        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        String name = extractJsonString(text, "name");
        String category = normaliseCategory(extractJsonString(text, "category"));
        int servings = extractJsonInt(text, "servings", 2);
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

        Pattern arrayPattern = Pattern.compile("\"ingredients\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher arrayMatcher = arrayPattern.matcher(json);
        if (!arrayMatcher.find()) return result;

        String arrayContent = arrayMatcher.group(1);

        Pattern objPattern = Pattern.compile("\\{[^}]+\\}", Pattern.DOTALL);
        Matcher objMatcher = objPattern.matcher(arrayContent);

        while (objMatcher.find()) {
            String obj = objMatcher.group();
            String name = extractJsonString(obj, "name");
            String unit = extractJsonString(obj, "unit");
            BigDecimal quantity = extractJsonDecimal(obj, "quantity");

            if (name == null || name.isBlank() || quantity == null) continue;

            BigDecimal normalisedQty = normaliseQuantity(quantity, unit);

            result.add(new RecipeExtract.IngredientLine(
                    name.trim(),
                    normalisedQty,
                    unit != null ? unit.toLowerCase() : "g"
            ));
        }
        return result;
    }

    private BigDecimal normaliseQuantity(BigDecimal qty, String unit) {
        if (unit == null) return qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        return switch (unit.toLowerCase().trim()) {
            case "g", "gr", "gram", "grams" -> qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
            case "ml", "milliliter", "milliliters" -> qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
            case "kg", "kilogram", "kilograms" -> qty;
            case "l", "liter", "liters", "litre" -> qty;
            case "tbsp", "tablespoon" -> qty.multiply(BigDecimal.valueOf(0.015));
            case "tsp", "teaspoon" -> qty.multiply(BigDecimal.valueOf(0.005));
            case "cup", "cups" -> qty.multiply(BigDecimal.valueOf(0.240));
            case "piece", "pieces", "pc", "pcs",
                 "slice", "slices", "clove", "cloves",
                 "whole", "large", "medium", "small" -> qty;
            default -> qty.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        };
    }

    private String normaliseCategory(String raw) {
        if (raw == null) return "MAIN_COURSE";
        String upper = raw.toUpperCase().replace(" ", "_").replace("-", "_");
        return switch (upper) {
            case "MAIN_COURSE", "MAIN", "MAIN COURSE" -> "MAIN_COURSE";
            case "SOUP", "STEW" -> "SOUP";
            case "SALAD" -> "SALAD";
            case "SNACK", "APPETIZER", "STARTER" -> "SNACK";
            case "DESSERT", "SWEET", "CAKE" -> "DESSERT";
            case "DRINK", "SMOOTHIE", "BEVERAGE" -> "DRINK";
            case "SAUCE", "DIP", "DRESSING" -> "SAUCE";
            case "SIDE", "SIDE_DISH" -> "SIDE";
            default -> "MAIN_COURSE";
        };
    }

    // ── JSON field extractors ──────────────────────────────────────────────

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
