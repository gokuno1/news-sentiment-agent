package com.gdelt.sentiment.agent;

/**
 * Structured output from the SentimentScorer: detailed analysis, score, and confidence.
 */
public record SentimentResult(
    String analysis,
    double score,
    double confidence
) {
    public String analysis() {
        return analysis != null ? analysis : "";
    }
}
