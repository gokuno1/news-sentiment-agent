package com.gdelt.sentiment.tool;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Preprocesses queries for GDELT's keyword-based search.
 * Strips analytical/sentiment language, truncates to effective keywords,
 * and generates alternative suggestions for empty results.
 */
public class GdeltQueryPreprocessor {

    private static final Set<String> ANALYTICAL_STOPWORDS = Set.of(
            "hawkish", "dovish", "bullish", "bearish", "positive", "negative",
            "outlook", "stance", "perspective", "analysis", "sentiment",
            "forecast", "prediction", "expectation", "concern", "worry",
            "rising", "falling", "surging", "declining", "slowing",
            "accelerating", "deteriorating", "improving", "strengthening",
            "weakening", "volatile", "stable", "uncertain", "robust",
            "fragile", "resilient", "vulnerable", "aggressive", "cautious",
            "current", "recent", "latest", "today", "impact", "effect",
            "significant", "potential", "likely", "expected", "possible"
    );

    private static final Set<String> COMMON_STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to",
            "for", "of", "with", "by", "from", "is", "are", "was", "were",
            "be", "been", "being", "have", "has", "had", "do", "does",
            "did", "will", "would", "could", "should", "may", "might",
            "shall", "can", "this", "that", "these", "those", "it", "its",
            "how", "what", "which", "who", "when", "where", "why"
    );

    private static final Map<String, List<String>> ALTERNATIVE_SUGGESTIONS = Map.ofEntries(
            Map.entry("economic growth", List.of("GDP growth", "recession", "PMI manufacturing")),
            Map.entry("inflation", List.of("CPI consumer prices", "PPI producer prices", "inflation rate")),
            Map.entry("employment", List.of("unemployment rate", "jobs report", "nonfarm payrolls")),
            Map.entry("monetary policy", List.of("Federal Reserve interest rate", "central bank rate decision")),
            Map.entry("trade", List.of("trade balance", "tariffs", "imports exports")),
            Map.entry("fiscal", List.of("government spending", "budget deficit", "stimulus")),
            Map.entry("gold", List.of("gold prices", "gold rally", "safe haven")),
            Map.entry("oil", List.of("crude oil prices", "OPEC", "oil production")),
            Map.entry("currency", List.of("dollar index", "exchange rate", "forex")),
            Map.entry("stock market", List.of("equity market", "stock index", "market rally"))
    );

    /**
     * Preprocess a query for GDELT: strip analytical language, keep keywords, truncate.
     */
    public static String preprocess(String query) {
        if (query == null || query.isBlank()) return query;

        String[] words = query.toLowerCase().split("\\s+");
        List<String> keywords = new ArrayList<>();
        for (String word : words) {
            String clean = word.replaceAll("[^a-zA-Z0-9]", "");
            if (clean.isBlank()) continue;
            if (ANALYTICAL_STOPWORDS.contains(clean)) continue;
            if (COMMON_STOPWORDS.contains(clean)) continue;
            keywords.add(word);
        }

        if (keywords.isEmpty()) {
            return query.length() > 50 ? query.substring(0, 50) : query;
        }

        return keywords.stream()
                .limit(5)
                .collect(Collectors.joining(" "));
    }

    /**
     * Suggest alternative keywords when GDELT returns empty results.
     */
    public static List<String> suggestAlternatives(String query) {
        String lower = query.toLowerCase();
        List<String> suggestions = new ArrayList<>();

        for (var entry : ALTERNATIVE_SUGGESTIONS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                suggestions.addAll(entry.getValue());
            }
        }

        if (suggestions.isEmpty()) {
            String preprocessed = preprocess(query);
            String[] words = preprocessed.split("\\s+");
            if (words.length > 2) {
                suggestions.add(words[0] + " " + words[1]);
                suggestions.add(words[words.length - 2] + " " + words[words.length - 1]);
            }
        }

        return suggestions.stream().limit(3).toList();
    }
}
