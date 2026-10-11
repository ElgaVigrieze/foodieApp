package com.foodie.util;

/**
 * Utility methods for fuzzy text matching.
 * Used by VoiceLogService and RecipeIngredientMatcherService.
 */
public final class TextMatchingUtils {

    /** Minimum score required to consider a match valid. */
    public static final int MIN_MATCH_SCORE = 4;

    private TextMatchingUtils() {
        // Utility class
    }

    /**
     * Calculate word-overlap score between two strings.
     * Score = sum of lengths of shared words longer than 2 characters.
     *
     * @param searchText the text to search for (should be lowercase)
     * @param targetName the name to match against (should be lowercase)
     * @return the overlap score
     */
    public static int wordOverlapScore(String searchText, String targetName) {
        String[] words = searchText.split("\\s+");
        int score = 0;

        for (String word : words) {
            if (word.length() > 2 && targetName.contains(word)) {
                score += word.length();
            }
        }

        // Also check full substring in both directions for short names
        if (targetName.contains(searchText) || searchText.contains(targetName)) {
            score = Math.max(score, targetName.length());
        }

        return score;
    }

    /**
     * Find the best matching item from a list using word overlap scoring.
     *
     * @param items list of items to search
     * @param searchText text to match (will be lowercased)
     * @param nameExtractor function to extract the name from each item
     * @param <T> the item type
     * @return the best matching item, or null if no match meets the threshold
     */
    public static <T> T findBestMatch(Iterable<T> items, String searchText, java.util.function.Function<T, String> nameExtractor) {
        String searchLower = searchText.toLowerCase();
        T best = null;
        int bestScore = 0;

        for (T item : items) {
            String name = nameExtractor.apply(item);
            if (name == null) continue;

            int score = wordOverlapScore(searchLower, name.toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }

        return bestScore >= MIN_MATCH_SCORE ? best : null;
    }
}
