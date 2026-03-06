package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.config.AppConfig;
import com.gdelt.sentiment.gdelt.ArticleContentFetcher;
import com.gdelt.sentiment.gdelt.GdeltArticle;
import com.gdelt.sentiment.rag.NewsRagService;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Tools exposed to the agentic runtime: search GDELT, summarize collected news,
 * score sentiment, and submit a final report. Holds per-run state.
 */
public class GdeltSentimentTools {

    private static final Logger LOG = Logger.getLogger(GdeltSentimentTools.class.getName());

    private final GdeltFetcherStep fetcher;
    private final NewsRagService ragService;
    private final SentimentScorerAgent scorer;
    private final boolean fetchFullContent;
    private final ArticleContentFetcher contentFetcher;

    private final List<GdeltArticle> collectedArticles = new ArrayList<>();
    private final Set<String> seenUrls = new HashSet<>();
    private SentimentResult lastReport;

    public GdeltSentimentTools() {
        this(new GdeltFetcherStep(),
             new NewsRagService(),
             MacroeconomicSentimentScorerAgent.create(),
             AppConfig.getGdeltFetchFullContent());
    }

    public GdeltSentimentTools(GdeltFetcherStep fetcher,
                               NewsRagService ragService,
                               SentimentScorerAgent scorer,
                               boolean fetchFullContent) {
        this.fetcher = fetcher;
        this.ragService = ragService;
        this.scorer = scorer;
        this.fetchFullContent = fetchFullContent;
        this.contentFetcher = fetchFullContent ? new ArticleContentFetcher() : null;
    }

    @Tool(name = "searchGdeltNews")
    public String searchGdeltNews(String query, Integer maxRecords) {
        if (query == null || query.isBlank()) {
            LOG.info("[Agent] searchGdeltNews called with no query.");
            return "No query provided.";
        }
        int safeMax = maxRecords != null && maxRecords > 0
            ? Math.min(maxRecords, AppConfig.getGdeltMaxRecordsPerRequest())
            : AppConfig.getGdeltMaxRecordsPerRequest();
        LOG.info("[Agent] searchGdeltNews: query=\"" + query + "\", maxRecords=" + safeMax + ", collectedSoFar=" + collectedArticles.size());

        List<GdeltArticle> current = new ArrayList<>(collectedArticles);
        // Use iteration 0 to keep deterministic behavior here; maxRecords already capped above.
        List<GdeltArticle> fetched = fetcher.fetchAndMerge(query, current, 0);

        int before = collectedArticles.size();
        for (GdeltArticle a : fetched) {
            String url = a.getUrl();
            if (url != null && !url.isBlank() && !seenUrls.contains(url)) {
                seenUrls.add(url);
                collectedArticles.add(a);
            }
        }

        if (fetchFullContent && contentFetcher != null) {
            enrichWithFullContent(collectedArticles);
        }

        if (AppConfig.getAgenticRagEnabled() && collectedArticles.size() > before) {
            try {
                LOG.info("[Agent] RAG: adding new articles to vector store.");
                List<GdeltArticle> newlyAdded = new ArrayList<>(collectedArticles.subList(before, collectedArticles.size()));
                ragService.addNewsOnlyUnique(newlyAdded);
                LOG.info("[Agent] RAG: added " + newlyAdded.size() + " new articles to vector store.");
            } catch (Exception e) {
                LOG.warning("[Agent] RAG add failed (continuing without store): " + e.getMessage());
            }
        }

        int added = collectedArticles.size() - before;
        LOG.info("[Agent] searchGdeltNews done: added=" + added + ", totalCollected=" + collectedArticles.size());
        String titles = collectedArticles.stream()
            .skip(Math.max(0, collectedArticles.size() - Math.min(10, collectedArticles.size())))
            .map(GdeltArticle::getTitle)
            .filter(t -> t != null && !t.isBlank())
            .collect(Collectors.joining(" | "));
        return "Added " + added + " articles (total " + collectedArticles.size() + "). Recent titles: " + titles;
    }

    @Tool(name = "getCollectedArticlesSummary")
    public String getCollectedArticlesSummary() {
        LOG.info("[Agent] getCollectedArticlesSummary: collected=" + collectedArticles.size());
        if (collectedArticles.isEmpty()) {
            return "No articles collected yet.";
        }
        if (fetchFullContent && contentFetcher != null) {
            enrichWithFullContent(collectedArticles);
        }
        return collectedArticles.stream()
            .limit(20)
            .map(a -> "- " + a.getTitle() + "\n" + a.getTextForEmbedding())
            .collect(Collectors.joining("\n\n"));
    }

    @Tool(name = "scoreSentiment")
    public String scoreSentiment(String topic) {
        LOG.info("[Agent] scoreSentiment: topic=\"" + topic + "\", articles=" + collectedArticles.size());
        if (topic == null || topic.isBlank()) {
            this.lastReport = new SentimentResult("No topic provided.", 0.0, 0.0);
            return "No topic provided.";
        }
        if (collectedArticles.isEmpty()) {
            LOG.info("[Agent] No articles collected; fetching from GDELT for topic before scoring.");
            fetchFromGdeltAndMerge(topic);
            if (collectedArticles.isEmpty()) {
                LOG.warning("[Agent] GDELT returned no articles for topic; cannot score.");
                this.lastReport = new SentimentResult(
                    "GDELT returned no articles for this topic. Try a different query or timespan.", 0.0, 0.0);
                return "No articles returned by GDELT for this topic. Cannot produce sentiment.";
            }
            LOG.info("[Agent] Fetched " + collectedArticles.size() + " articles from GDELT; proceeding to score.");
        }
        if (fetchFullContent && contentFetcher != null) {
            enrichWithFullContent(collectedArticles);
        }
        String currentRunText = collectedArticles.stream()
            .limit(50)
            .map(GdeltArticle::getTextForEmbedding)
            .collect(Collectors.joining("\n\n"));
        String newsText = currentRunText;
        if (AppConfig.getAgenticRagEnabled()) {
            try {
                List<String> retrieved = ragService.retrieveRelevant(topic, AppConfig.getRagTopK());
                if (!retrieved.isEmpty()) {
                    String ragContext = String.join("\n\n", retrieved);
                    newsText = currentRunText + "\n\n--- Relevant context from stored articles ---\n\n" + ragContext;
                    LOG.info("[Agent] RAG: merged " + retrieved.size() + " stored segments into scoring context.");
                }
            } catch (Exception e) {
                LOG.warning("[Agent] RAG retrieve failed (using current run only): " + e.getMessage());
            }
        }
        SentimentResult result = scorer.score(topic, newsText);
        this.lastReport = result;
        LOG.info("[Agent] scoreSentiment done: score=" + result.score() + ", confidence=" + result.confidence());
        return "Scored macro sentiment. Score=" + result.score()
            + ", confidence=" + result.confidence()
            + ". Analysis:\n" + result.analysis();
    }

    @Tool(name = "submitFinalReport")
    public String submitFinalReport(String analysis, Double score, Double confidence) {
        double s = score != null ? score : 0.0;
        double c = confidence != null ? confidence : 0.0;
        this.lastReport = new SentimentResult(analysis != null ? analysis : "", s, c);
        LOG.info("[Agent] submitFinalReport: score=" + s + ", confidence=" + c);
        return "Final report submitted.";
    }

    public SentimentResult getLastReport() {
        return lastReport;
    }

    public List<GdeltArticle> getCollectedArticles() {
        return new ArrayList<>(collectedArticles);
    }

    /**
     * Fetch articles from GDELT for the query and merge into collectedArticles (deduplicated by URL).
     * Used when scoreSentiment is called with no articles so we always attempt to retrieve news before scoring.
     */
    private void fetchFromGdeltAndMerge(String query) {
        List<GdeltArticle> current = new ArrayList<>(collectedArticles);
        List<GdeltArticle> fetched = fetcher.fetchAndMerge(query, current, 0);
        for (GdeltArticle a : fetched) {
            String url = a.getUrl();
            if (url != null && !url.isBlank() && !seenUrls.contains(url)) {
                seenUrls.add(url);
                collectedArticles.add(a);
            }
        }
        if (fetchFullContent && contentFetcher != null && !collectedArticles.isEmpty()) {
            enrichWithFullContent(collectedArticles);
        }
        if (AppConfig.getAgenticRagEnabled() && !collectedArticles.isEmpty()) {
            try {
                ragService.addNewsOnlyUnique(new ArrayList<>(collectedArticles));
                LOG.info("[Agent] RAG: added fetched articles to vector store.");
            } catch (Exception e) {
                LOG.warning("[Agent] RAG add failed after fetch: " + e.getMessage());
            }
        }
    }

    private void enrichWithFullContent(List<GdeltArticle> articles) {
        if (!fetchFullContent || contentFetcher == null) {
            return;
        }
        for (GdeltArticle article : articles) {
            if (article.getFullContent() == null || article.getFullContent().isBlank()) {
                String url = article.getUrl();
                String body = contentFetcher.fetchBody(url);
                if (body != null && !body.isBlank()) {
                    article.setFullContent(body);
                }
            }
        }
    }
}

