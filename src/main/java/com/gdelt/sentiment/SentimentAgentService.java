package com.gdelt.sentiment;

import com.gdelt.sentiment.agent.SentimentAgentLoop;
import com.gdelt.sentiment.agent.SentimentResult;

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

        SentimentAgentLoop loop = new SentimentAgentLoop();
        SentimentResult result = loop.run(query);

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
        return new SentimentAgentLoop().run(query);
    }
}
