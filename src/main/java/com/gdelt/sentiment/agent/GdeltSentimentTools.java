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
import java.util.stream.Collectors;

/**
 * Tools exposed to the agentic runtime: search GDELT, summarize collected news,
 * score sentiment, and submit a final report. Holds per-run state.
 */
public class GdeltSentimentTools {

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
            return "No query provided.";
        }
        int safeMax = maxRecords != null && maxRecords > 0
            ? Math.min(maxRecords, AppConfig.getGdeltMaxRecordsPerRequest())
            : AppConfig.getGdeltMaxRecordsPerRequest();

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

        int added = collectedArticles.size() - before;
        String titles = collectedArticles.stream()
            .skip(Math.max(0, collectedArticles.size() - Math.min(10, collectedArticles.size())))
            .map(GdeltArticle::getTitle)
            .filter(t -> t != null && !t.isBlank())
            .collect(Collectors.joining(" | "));
        return "Added " + added + " articles (total " + collectedArticles.size() + "). Recent titles: " + titles;
    }

    @Tool(name = "getCollectedArticlesSummary")
    public String getCollectedArticlesSummary() {
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
        if (topic == null || topic.isBlank()) {
            return "No topic provided.";
        }
        if (collectedArticles.isEmpty()) {
            return "No articles collected to score.";
        }
        if (fetchFullContent && contentFetcher != null) {
            enrichWithFullContent(collectedArticles);
        }
        String newsText = collectedArticles.stream()
            .limit(50)
            .map(GdeltArticle::getTextForEmbedding)
            .collect(Collectors.joining("\n\n"));
        SentimentResult result = scorer.score(topic, newsText);
        this.lastReport = result;
        return "Scored macro sentiment. Score=" + result.score()
            + ", confidence=" + result.confidence()
            + ". Analysis:\n" + result.analysis();
    }

    @Tool(name = "submitFinalReport")
    public String submitFinalReport(String analysis, Double score, Double confidence) {
        double s = score != null ? score : 0.0;
        double c = confidence != null ? confidence : 0.0;
        this.lastReport = new SentimentResult(analysis != null ? analysis : "", s, c);
        return "Final report submitted.";
    }

    public SentimentResult getLastReport() {
        return lastReport;
    }

    public List<GdeltArticle> getCollectedArticles() {
        return new ArrayList<>(collectedArticles);
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

