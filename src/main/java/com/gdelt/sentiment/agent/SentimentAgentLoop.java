package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.config.AppConfig;
import com.gdelt.sentiment.gdelt.GdeltArticle;
import com.gdelt.sentiment.rag.NewsRagService;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Runs the research loop: fetch (with dedupe) -> RAG add -> RAG retrieve -> score,
 * until confidence >= threshold or max iterations.
 */
public class SentimentAgentLoop {

    private static final Logger LOG = Logger.getLogger(SentimentAgentLoop.class.getName());

    private final GdeltFetcherStep fetcher;
    private final NewsRagService ragService;
    private final SentimentScorerAgent scorer;
    private final int maxIterations;
    private final double confidenceThreshold;

    public SentimentAgentLoop() {
        this(new GdeltFetcherStep(), new NewsRagService(), SentimentScorerAgent.create(),
             AppConfig.getAgentMaxIterations(), AppConfig.getAgentConfidenceThreshold());
    }

    public SentimentAgentLoop(GdeltFetcherStep fetcher, NewsRagService ragService,
                              SentimentScorerAgent scorer, int maxIterations, double confidenceThreshold) {
        this.fetcher = fetcher;
        this.ragService = ragService;
        this.scorer = scorer;
        this.maxIterations = maxIterations;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Run the loop until confidence >= threshold or max iterations.
     *
     * @param query the topic/query
     * @return final sentiment result (analysis, score, confidence)
     */
    public SentimentResult run(String query) {
        List<GdeltArticle> news = List.of();
        SentimentResult last = new SentimentResult("", 0.0, 0.0);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            LOG.info("Iteration " + (iteration + 1) + "/" + maxIterations);

            // 1. Fetch more (deduplicated)
            news = fetcher.fetchAndMerge(query, news, iteration);
            if (news.isEmpty()) {
                LOG.warning("No news retrieved for query: " + query);
                if (iteration == 0) return last;
                break;
            }

            // 2. RAG: add only unique (for future runs). Use freshly fetched news for scoring
            // so we only score articles from the configured timespan (e.g. last 5 days).
            // RAG retrieval would pull from the persistent store (all historical articles),
            // which can return older but semantically similar articles and skew results.
            ragService.addNewsOnlyUnique(news);
            List<String> relevant = news.stream()
                .map(GdeltArticle::getTextForEmbedding)
                .limit(30)
                .collect(Collectors.toList());
            String newsText = String.join("\n\n", relevant);

            // 3. Score
            last = scorer.score(query, newsText);
            LOG.info("Confidence: " + last.confidence() + ", Score: " + last.score());

            if (last.confidence() >= confidenceThreshold) {
                LOG.info("Confidence threshold reached.");
                break;
            }
        }

        return last;
    }
}
