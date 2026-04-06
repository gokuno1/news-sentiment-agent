package com.gdelt.sentiment.model;

public record DimensionImpact(
        MacroDimension dimension,
        String impact,
        String relevance
) {
    public DimensionImpact {
        if (dimension == null) throw new IllegalArgumentException("dimension is required");
        if (impact == null) impact = "";
        if (relevance == null) relevance = "MEDIUM";
    }
}
