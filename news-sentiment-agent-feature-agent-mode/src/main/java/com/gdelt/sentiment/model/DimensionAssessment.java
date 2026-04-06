package com.gdelt.sentiment.model;

import java.util.List;

public record DimensionAssessment(
        MacroDimension dimension,
        String signal,
        double score,
        String summary,
        List<String> sources,
        String lookDirection
) {
    public DimensionAssessment {
        if (dimension == null) throw new IllegalArgumentException("dimension is required");
        if (signal == null || signal.isBlank()) signal = "neutral";
        score = Math.max(-1.0, Math.min(1.0, score));
        if (summary == null) summary = "";
        if (sources == null) sources = List.of();
        if (lookDirection == null) lookDirection = "both";
    }
}
