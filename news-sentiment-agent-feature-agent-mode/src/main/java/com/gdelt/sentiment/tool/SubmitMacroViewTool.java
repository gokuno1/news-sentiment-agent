package com.gdelt.sentiment.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.agent.WorkingMemory;
import com.gdelt.sentiment.model.DimensionAssessment;
import com.gdelt.sentiment.model.MacroDimension;
import com.gdelt.sentiment.model.MacroView;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SubmitMacroViewTool {

    private static final Logger log = LoggerFactory.getLogger(SubmitMacroViewTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String NAME = "submitMacroView";

    public static ToolSpecification spec() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Submit your final structured macroeconomic assessment. "
                        + "Call this ONLY when you have sufficient evidence across all relevant dimensions. "
                        + "Include an overall sentiment score and analysis summary.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("overallSentiment", JsonNumberSchema.builder()
                                .description("Overall macro sentiment from -1.0 (very bearish) to 1.0 (very bullish)")
                                .build())
                        .addProperty("overallAnalysis", JsonStringSchema.builder()
                                .description("3-5 sentence overall macroeconomic assessment synthesizing all dimensions")
                                .build())
                        .addProperty("keyRisks", JsonArraySchema.builder()
                                .items(new JsonStringSchema())
                                .description("Top 3-5 key risks to the macro outlook")
                                .build())
                        .required("overallSentiment", "overallAnalysis")
                        .build())
                .build();
    }

    public String execute(String arguments, WorkingMemory memory, String topic) {
        try {
            JsonNode args = MAPPER.readTree(arguments);

            double overallSentiment = args.get("overallSentiment").asDouble();
            String overallAnalysis = args.get("overallAnalysis").asText();

            List<String> keyRisks = new ArrayList<>();
            if (args.has("keyRisks") && args.get("keyRisks").isArray()) {
                for (JsonNode risk : args.get("keyRisks")) {
                    keyRisks.add(risk.asText());
                }
            }

            MacroView baseView = memory.buildMacroView(topic);

            MacroView finalView = new MacroView(
                    topic,
                    baseView.dimensions(),
                    overallSentiment,
                    overallAnalysis,
                    keyRisks,
                    memory.contradictions(),
                    Instant.now()
            );

            memory.submitFinalView(finalView);

            log.info("[SubmitMacroView] Final view submitted: score={}, dimensions={}, risks={}",
                    overallSentiment, finalView.dimensions().size(), keyRisks.size());

            return "{\"status\":\"submitted\",\"dimensions\":" + finalView.dimensions().size()
                    + ",\"overallSentiment\":" + overallSentiment + "}";
        } catch (Exception e) {
            log.warn("[SubmitMacroView] Failed: {}", e.getMessage());
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
