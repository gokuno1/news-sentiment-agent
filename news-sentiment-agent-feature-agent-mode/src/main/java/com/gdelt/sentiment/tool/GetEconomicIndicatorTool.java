package com.gdelt.sentiment.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.agent.WorkingMemory;
import com.gdelt.sentiment.client.FredClient;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GetEconomicIndicatorTool {

    private static final Logger log = LoggerFactory.getLogger(GetEconomicIndicatorTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String NAME = "getEconomicIndicator";

    private final FredClient fredClient;
    private final RateLimitedExecutor executor;

    public GetEconomicIndicatorTool(FredClient fredClient, RateLimitedExecutor executor) {
        this.fredClient = fredClient;
        this.executor = executor;
    }

    public static ToolSpecification spec() {
        String indicatorList = String.join(", ", FredClient.availableIndicators());
        return ToolSpecification.builder()
                .name(NAME)
                .description("Fetch actual economic data from FRED (Federal Reserve Economic Data). "
                        + "Returns recent data points for the specified indicator. "
                        + "Use this to verify news claims with hard data. "
                        + "Available indicators: " + indicatorList + ". "
                        + "You can also pass a raw FRED series ID directly.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("indicator", JsonStringSchema.builder()
                                .description("Indicator name (e.g. 'CPI', 'GDP', 'UNEMPLOYMENT') or FRED series ID")
                                .build())
                        .addProperty("limit", JsonIntegerSchema.builder()
                                .description("Number of recent data points to return (default 6, max 24)")
                                .build())
                        .required("indicator")
                        .build())
                .build();
    }

    public String execute(String arguments, WorkingMemory memory) {
        try {
            JsonNode args = MAPPER.readTree(arguments);
            String indicator = args.get("indicator").asText();
            int limit = args.has("limit") ? args.get("limit").asInt() : 6;
            limit = Math.max(1, Math.min(limit, 24));

            log.info("[EconomicIndicator] Fetching {} (limit={})", indicator, limit);

            final int finalLimit = limit;
            return executor.executeBlocking("FRED", () -> {
                FredClient.SeriesData data = fredClient.getObservations(indicator, finalLimit);
                return formatResult(indicator, data);
            });
        } catch (Exception e) {
            log.warn("[EconomicIndicator] Failed: {}", e.getMessage());
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String formatResult(String indicator, FredClient.SeriesData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"indicator\":\"").append(indicator).append("\",");
        sb.append("\"seriesId\":\"").append(data.seriesId()).append("\",");
        sb.append("\"units\":\"").append(data.units()).append("\",");
        sb.append("\"frequency\":\"").append(data.frequency()).append("\",");
        sb.append("\"dataPoints\":[");

        List<FredClient.Observation> obs = data.observations();
        for (int i = 0; i < obs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"date\":\"").append(obs.get(i).date())
                    .append("\",\"value\":\"").append(obs.get(i).value()).append("\"}");
        }

        sb.append("],\"count\":").append(obs.size());

        if (obs.size() >= 2) {
            try {
                double latest = Double.parseDouble(obs.get(0).value());
                double previous = Double.parseDouble(obs.get(1).value());
                double change = latest - previous;
                double pctChange = previous != 0 ? (change / previous) * 100 : 0;
                String trend = change > 0 ? "rising" : change < 0 ? "falling" : "flat";
                sb.append(",\"trend\":\"").append(trend).append("\"");
                sb.append(",\"latestValue\":").append(latest);
                sb.append(",\"change\":").append(String.format("%.4f", change));
                sb.append(",\"pctChange\":").append(String.format("%.2f", pctChange));
            } catch (NumberFormatException ignored) {
            }
        }

        sb.append("}");
        return sb.toString();
    }
}
