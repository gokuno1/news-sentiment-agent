package com.gdelt.sentiment.model;

import java.time.Instant;
import java.util.List;

public record MacroView(
        String topic,
        List<DimensionAssessment> dimensions,
        double overallSentiment,
        String overallAnalysis,
        List<String> keyRisks,
        List<String> contradictions,
        Instant timestamp
) {
    public MacroView {
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
        if (dimensions == null) dimensions = List.of();
        overallSentiment = Math.max(-1.0, Math.min(1.0, overallSentiment));
        if (overallAnalysis == null) overallAnalysis = "";
        if (keyRisks == null) keyRisks = List.of();
        if (contradictions == null) contradictions = List.of();
        if (timestamp == null) timestamp = Instant.now();
    }
}
