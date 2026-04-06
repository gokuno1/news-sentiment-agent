package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.model.DimensionAssessment;
import com.gdelt.sentiment.model.MacroView;
import com.gdelt.sentiment.model.StockInfo;

import java.util.List;
import java.util.stream.Collectors;

public final class StockImpactPrompt {

    private StockImpactPrompt() {}

    public static final String SYSTEM = """
            You are a senior equity analyst specializing in mapping macroeconomic conditions \
            to individual stock impacts. Given a structured macroeconomic view and a list of \
            portfolio stocks, you produce actionable per-stock analysis.

            For EACH stock in the portfolio:
            1. IDENTIFY the sector/industry (you know major NSE/BSE listed companies)
            2. MAP which macro dimensions are most relevant to this stock and why
            3. SCORE the macro impact: -1.0 (very bearish) to 1.0 (very bullish)
            4. RECOMMEND position bias: OVERWEIGHT, UNDERWEIGHT, NEUTRAL, or AVOID
            5. Rate your confidence: 0.0 to 1.0
            6. EXPLAIN reasoning in 2-3 sentences, citing specific macro findings

            Consider these transmission channels:
            - Interest rate sensitivity (banks, NBFCs, real estate)
            - Commodity exposure (metals, oil & gas, FMCG input costs)
            - Export/import dependency (IT services, pharma, auto components)
            - Consumer demand sensitivity (FMCG, auto, discretionary)
            - Government spending linkage (infra, defense, PSUs)
            - Currency impact (IT exporters benefit from weak INR, importers suffer)
            - Global demand linkage (metals, chemicals, shipping)

            RESPOND ONLY WITH VALID JSON in this exact format:
            {
              "stockImpacts": [
                {
                  "tradingSymbol": "HDFCBANK",
                  "name": "HDFC Bank Ltd",
                  "sector": "Banking - Private",
                  "impactDirection": "NEGATIVE",
                  "impactScore": -0.35,
                  "positionBias": "UNDERWEIGHT",
                  "biasConfidence": 0.72,
                  "reasoning": "Explanation citing macro findings...",
                  "affectedDimensions": [
                    {"dimension": "MONETARY", "impact": "Hawkish stance compresses NIMs", "relevance": "HIGH"},
                    {"dimension": "INFLATION", "impact": "Sticky inflation delays rate cuts", "relevance": "HIGH"}
                  ]
                }
              ],
              "portfolioOverallBias": "slightly bearish",
              "summary": "Overall portfolio assessment in 2-3 sentences"
            }
            """;

    public static String buildUserMessage(MacroView macroView, List<StockInfo> stocks) {
        StringBuilder sb = new StringBuilder();

        sb.append("## MACROECONOMIC VIEW\n\n");
        sb.append("Topic: ").append(macroView.topic()).append("\n");
        sb.append("Overall Sentiment: ").append(macroView.overallSentiment()).append("\n");
        sb.append("Analysis: ").append(macroView.overallAnalysis()).append("\n\n");

        sb.append("### Per-Dimension Assessments:\n");
        for (DimensionAssessment dim : macroView.dimensions()) {
            sb.append("- **").append(dim.dimension().getDisplayName()).append("**: ")
                    .append(dim.signal()).append(" (score: ").append(dim.score()).append(")\n");
            sb.append("  ").append(dim.summary()).append("\n");
        }

        if (!macroView.keyRisks().isEmpty()) {
            sb.append("\n### Key Risks:\n");
            for (String risk : macroView.keyRisks()) {
                sb.append("- ").append(risk).append("\n");
            }
        }

        if (!macroView.contradictions().isEmpty()) {
            sb.append("\n### Contradictions:\n");
            for (String c : macroView.contradictions()) {
                sb.append("- ").append(c).append("\n");
            }
        }

        sb.append("\n## PORTFOLIO STOCKS\n\n");
        sb.append("Analyze each stock below:\n\n");
        for (StockInfo stock : stocks) {
            sb.append("- **").append(stock.tradingSymbol()).append("**");
            if (!stock.name().isBlank()) sb.append(" (").append(stock.name()).append(")");
            if (stock.quantity() > 0) sb.append(" — Qty: ").append((int) stock.quantity());
            if (stock.lastPrice() > 0) sb.append(", LTP: ₹").append(String.format("%.2f", stock.lastPrice()));
            sb.append("\n");
        }

        return sb.toString();
    }
}
