package com.gdelt.sentiment.model;

public record PortfolioImpactRequest(
        String topic,
        String accessToken
) {
    public PortfolioImpactRequest {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
    }

    public PortfolioImpactRequest(String topic) {
        this(topic, null);
    }
}
