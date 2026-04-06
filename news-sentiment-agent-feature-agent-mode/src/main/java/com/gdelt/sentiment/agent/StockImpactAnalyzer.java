package com.gdelt.sentiment.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.model.*;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Maps a MacroView to individual stock impacts using Claude.
 * Separate from the research agent -- different skill (equity analysis vs macro research).
 */
@Component
public class StockImpactAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(StockImpactAnalyzer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel chatModel;

    public StockImpactAnalyzer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public PortfolioMacroReport analyze(MacroView macroView, List<StockInfo> stocks) {
        if (stocks.isEmpty()) {
            log.warn("[StockImpact] No stocks provided for analysis");
            return new PortfolioMacroReport(macroView, List.of(), "no stocks", Map.of(), Instant.now());
        }

        log.info("[StockImpact] Analyzing {} stocks against macro view", stocks.size());

        String userMessage = StockImpactPrompt.buildUserMessage(macroView, stocks);

        try {
            ChatResponse response = chatModel.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(StockImpactPrompt.SYSTEM),
                            UserMessage.from(userMessage)
                    ))
                    .build());

            String responseText = response.aiMessage().text();
            return parseResponse(macroView, responseText);
        } catch (Exception e) {
            log.error("[StockImpact] Claude call failed: {}", e.getMessage());
            return new PortfolioMacroReport(macroView, List.of(), "error: " + e.getMessage(), Map.of(), Instant.now());
        }
    }

    private PortfolioMacroReport parseResponse(MacroView macroView, String responseText) {
        try {
            String json = extractJson(responseText);
            JsonNode root = MAPPER.readTree(json);

            List<StockImpact> impacts = new ArrayList<>();
            JsonNode impactsNode = root.get("stockImpacts");
            if (impactsNode != null && impactsNode.isArray()) {
                for (JsonNode item : impactsNode) {
                    impacts.add(parseStockImpact(item));
                }
            }

            String overallBias = root.has("portfolioOverallBias")
                    ? root.get("portfolioOverallBias").asText() : "neutral";

            Map<String, List<StockImpact>> sectorBreakdown = impacts.stream()
                    .collect(Collectors.groupingBy(StockImpact::sector));

            log.info("[StockImpact] Parsed {} stock impacts, overall bias: {}", impacts.size(), overallBias);

            return new PortfolioMacroReport(macroView, impacts, overallBias, sectorBreakdown, Instant.now());
        } catch (Exception e) {
            log.warn("[StockImpact] Failed to parse response: {}", e.getMessage());
            return new PortfolioMacroReport(macroView, List.of(), "parse error", Map.of(), Instant.now());
        }
    }

    private StockImpact parseStockImpact(JsonNode node) {
        String symbol = node.has("tradingSymbol") ? node.get("tradingSymbol").asText() : "";
        String name = node.has("name") ? node.get("name").asText() : "";
        String sector = node.has("sector") ? node.get("sector").asText() : "Unknown";

        StockImpact.ImpactDirection direction = StockImpact.ImpactDirection.NEUTRAL;
        if (node.has("impactDirection")) {
            try {
                direction = StockImpact.ImpactDirection.valueOf(node.get("impactDirection").asText().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        double impactScore = node.has("impactScore") ? node.get("impactScore").asDouble() : 0.0;

        StockImpact.PositionBias bias = StockImpact.PositionBias.NEUTRAL;
        if (node.has("positionBias")) {
            try {
                bias = StockImpact.PositionBias.valueOf(node.get("positionBias").asText().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        double confidence = node.has("biasConfidence") ? node.get("biasConfidence").asDouble() : 0.5;
        String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";

        List<DimensionImpact> affectedDimensions = new ArrayList<>();
        if (node.has("affectedDimensions") && node.get("affectedDimensions").isArray()) {
            for (JsonNode dimNode : node.get("affectedDimensions")) {
                try {
                    MacroDimension dim = MacroDimension.valueOf(dimNode.get("dimension").asText().toUpperCase());
                    String impact = dimNode.has("impact") ? dimNode.get("impact").asText() : "";
                    String relevance = dimNode.has("relevance") ? dimNode.get("relevance").asText() : "MEDIUM";
                    affectedDimensions.add(new DimensionImpact(dim, impact, relevance));
                } catch (Exception ignored) {}
            }
        }

        return new StockImpact(symbol, name, sector, direction, impactScore, bias, confidence, reasoning, affectedDimensions);
    }

    /**
     * Extract JSON from Claude's response, handling markdown fences.
     */
    private String extractJson(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();

        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        trimmed = trimmed.trim();

        int braceStart = trimmed.indexOf('{');
        if (braceStart > 0) {
            trimmed = trimmed.substring(braceStart);
        }

        int braceEnd = trimmed.lastIndexOf('}');
        if (braceEnd >= 0 && braceEnd < trimmed.length() - 1) {
            trimmed = trimmed.substring(0, braceEnd + 1);
        }

        return trimmed;
    }
}
