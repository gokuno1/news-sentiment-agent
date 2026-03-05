package com.gdelt.sentiment;

import com.gdelt.sentiment.agent.SentimentAgentLoop;
import com.gdelt.sentiment.agent.SentimentResult;
import com.gdelt.sentiment.agent.GdeltSentimentTools;
import com.gdelt.sentiment.agent.SentimentResearchAgent;
import com.gdelt.sentiment.config.AppConfig;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.util.logging.Logger;

/**
 * Entrypoint for the GDELT Sentiment AI Agent.
 * Accepts a topic/query and returns detailed analysis, sentiment score, and confidence.
 */
public class SentimentAgentService {

    private static final Logger LOG = Logger.getLogger(SentimentAgentService.class.getName());

    public static void main(String[] args) {
        String query = args.length > 0 ? String.join(" ", args) : "geopolitical tension AND Import Export AND tariffs";
        LOG.info("Query: " + query);

        SentimentResult result;
        String mode = AppConfig.getAgentMode();
        if ("agentic".equalsIgnoreCase(mode)) {
            result = runAgentic(query);
        } else {
            SentimentAgentLoop loop = new SentimentAgentLoop();
            result = loop.run(query);
        }

        System.out.println("--- Analysis ---");
        System.out.println(result.analysis());
        System.out.println();
        System.out.println("Sentiment score: " + result.score());
        System.out.println("Confidence: " + result.confidence());
    }

    /**
     * Programmatic API: run sentiment analysis for the given query.
     */
    public static SentimentResult analyze(String query) {
        String mode = AppConfig.getAgentMode();
        if ("agentic".equalsIgnoreCase(mode)) {
            return runAgentic(query);
        }
        return new SentimentAgentLoop().run(query);
    }

    private static SentimentResult runAgentic(String query) {
        GdeltSentimentTools tools = new GdeltSentimentTools();
        SentimentResearchAgent agent = AgenticServices
            .agentBuilder(SentimentResearchAgent.class)
            .chatModel(OllamaChatModel.builder()
                .baseUrl(AppConfig.getOllamaBaseUrl())
                .modelName(AppConfig.getOllamaModelName())
                .temperature(0.2)
                .timeout(java.time.Duration.ofSeconds(120))
                .build())
            .tools(tools)
            .build();

        try {
            agent.run(query);
        } catch (Exception e) {
            LOG.warning("Agentic run failed, falling back to empty result: " + e.getMessage());
        }

        SentimentResult report = tools.getLastReport();
        return report != null ? report : new SentimentResult("", 0.0, 0.0);
    }
}
