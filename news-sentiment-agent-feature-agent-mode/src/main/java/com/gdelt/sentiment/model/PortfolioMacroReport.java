package com.gdelt.sentiment.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PortfolioMacroReport(
        MacroView macroView,
        List<StockImpact> stockImpacts,
        String portfolioOverallBias,
        Map<String, List<StockImpact>> sectorBreakdown,
        Instant timestamp
) {
    public PortfolioMacroReport {
        if (macroView == null) throw new IllegalArgumentException("macroView is required");
        if (stockImpacts == null) stockImpacts = List.of();
        if (portfolioOverallBias == null) portfolioOverallBias = "neutral";
        if (sectorBreakdown == null) sectorBreakdown = Map.of();
        if (timestamp == null) timestamp = Instant.now();
    }
}
