package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.gdelt.GdeltArticle;
import com.gdelt.sentiment.gdelt.GdeltClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic step that fetches more articles from GDELT and merges only new (unique by URL) ones.
 */
public class GdeltFetcherStep {

    private final GdeltClient gdeltClient;

    public GdeltFetcherStep() {
        this(new GdeltClient());
    }

    public GdeltFetcherStep(GdeltClient gdeltClient) {
        this.gdeltClient = gdeltClient;
    }

    /**
     * Fetch more articles for the query and merge only those not already in currentNews.
     * Uses maxRecords = 50 * (iteration + 1) so each iteration can fetch more.
     *
     * @param query       the search query/topic
     * @param currentNews existing articles (URLs used for deduplication)
     * @param iteration   0-based iteration number
     * @return new list containing currentNews + newly fetched articles (no duplicates by URL)
     */
    public List<GdeltArticle> fetchAndMerge(String query, List<GdeltArticle> currentNews, int iteration) {
        Set<String> existingUrls = new HashSet<>();
        for (GdeltArticle a : currentNews) {
            if (a.getUrl() != null && !a.getUrl().isBlank()) {
                existingUrls.add(a.getUrl());
            }
        }
        int maxRecords = 50 * (iteration + 1);
        List<GdeltArticle> fetched = gdeltClient.fetchArticles(query, maxRecords);
        List<GdeltArticle> merged = new ArrayList<>(currentNews);
        for (GdeltArticle a : fetched) {
            if (a.getUrl() != null && !a.getUrl().isBlank() && !existingUrls.contains(a.getUrl())) {
                existingUrls.add(a.getUrl());
                merged.add(a);
            }
        }
        return merged;
    }
}
