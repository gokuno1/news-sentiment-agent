package com.gdelt.sentiment.model;

import java.util.Set;

public record MacroAnalysisRequest(
        String topic,
        Set<MacroDimension> dimensions
) {
    public MacroAnalysisRequest {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
        if (dimensions == null || dimensions.isEmpty()) {
            dimensions = Set.of(MacroDimension.values());
        }
    }

    public MacroAnalysisRequest(String topic) {
        this(topic, null);
    }
}
