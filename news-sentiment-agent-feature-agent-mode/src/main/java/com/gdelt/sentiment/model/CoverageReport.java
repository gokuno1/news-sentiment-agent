package com.gdelt.sentiment.model;

import java.util.List;
import java.util.Set;

public record CoverageReport(
        Set<MacroDimension> coveredDimensions,
        Set<MacroDimension> gaps,
        List<String> contradictions,
        int totalFindings
) {
    public CoverageReport {
        if (coveredDimensions == null) coveredDimensions = Set.of();
        if (gaps == null) gaps = Set.of();
        if (contradictions == null) contradictions = List.of();
    }

    public boolean isFullyCovered() {
        return gaps.isEmpty();
    }

    public double coverageRatio() {
        int total = coveredDimensions.size() + gaps.size();
        return total == 0 ? 0.0 : (double) coveredDimensions.size() / total;
    }
}
