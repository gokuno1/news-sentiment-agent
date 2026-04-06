package com.gdelt.sentiment.model;

import java.util.List;

public record StockImpact(
        String tradingSymbol,
        String name,
        String sector,
        ImpactDirection impactDirection,
        double impactScore,
        PositionBias positionBias,
        double biasConfidence,
        String reasoning,
        List<DimensionImpact> affectedDimensions
) {
    public enum ImpactDirection { POSITIVE, NEGATIVE, NEUTRAL }

    public enum PositionBias { OVERWEIGHT, UNDERWEIGHT, NEUTRAL, AVOID }

    public StockImpact {
        if (tradingSymbol == null) tradingSymbol = "";
        if (name == null) name = "";
        if (sector == null) sector = "Unknown";
        if (impactDirection == null) impactDirection = ImpactDirection.NEUTRAL;
        impactScore = Math.max(-1.0, Math.min(1.0, impactScore));
        if (positionBias == null) positionBias = PositionBias.NEUTRAL;
        biasConfidence = Math.max(0.0, Math.min(1.0, biasConfidence));
        if (reasoning == null) reasoning = "";
        if (affectedDimensions == null) affectedDimensions = List.of();
    }
}
