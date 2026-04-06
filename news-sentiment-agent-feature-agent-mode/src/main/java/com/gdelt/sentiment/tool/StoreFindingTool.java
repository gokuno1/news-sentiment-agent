package com.gdelt.sentiment.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.agent.WorkingMemory;
import com.gdelt.sentiment.model.Finding;
import com.gdelt.sentiment.model.MacroDimension;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class StoreFindingTool {

    private static final Logger log = LoggerFactory.getLogger(StoreFindingTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String NAME = "storeFinding";

    public static ToolSpecification spec() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("Record a research finding for a specific macro dimension. "
                        + "Call this after each search to store what you learned and which dimension it belongs to.")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("dimension", JsonEnumSchema.builder()
                                .enumValues(Arrays.stream(MacroDimension.values()).map(Enum::name).toList())
                                .description("The macro dimension this finding belongs to")
                                .build())
                        .addProperty("signal", JsonStringSchema.builder()
                                .description("Signal direction: bearish, neutral, bullish, hawkish, dovish, mixed, etc.")
                                .build())
                        .addProperty("evidence", JsonStringSchema.builder()
                                .description("Summary of what you found (2-3 sentences)")
                                .build())
                        .addProperty("sources", JsonArraySchema.builder()
                                .items(new JsonStringSchema())
                                .description("Source URLs or descriptions that support this finding")
                                .build())
                        .required("dimension", "signal", "evidence")
                        .build())
                .build();
    }

    public String execute(String arguments, WorkingMemory memory) {
        try {
            JsonNode args = MAPPER.readTree(arguments);
            MacroDimension dim = MacroDimension.valueOf(args.get("dimension").asText());
            String signal = args.get("signal").asText();
            String evidence = args.get("evidence").asText();

            List<String> sources = List.of();
            if (args.has("sources") && args.get("sources").isArray()) {
                sources = MAPPER.convertValue(args.get("sources"),
                        MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            }

            Finding finding = new Finding(dim, signal, evidence, sources);
            memory.storeFinding(finding);

            log.info("[StoreFinding] {} -> {} ({})", dim.getDisplayName(), signal, evidence.length() > 80 ? evidence.substring(0, 80) + "..." : evidence);
            return "{\"status\":\"stored\",\"dimension\":\"" + dim.name()
                    + "\",\"totalFindings\":" + memory.totalFindings() + "}";
        } catch (Exception e) {
            log.warn("[StoreFinding] Failed to parse arguments: {}", e.getMessage());
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
