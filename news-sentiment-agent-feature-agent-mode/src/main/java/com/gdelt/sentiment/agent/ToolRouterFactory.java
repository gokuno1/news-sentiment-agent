package com.gdelt.sentiment.agent;

import com.gdelt.sentiment.client.FredClient;
import com.gdelt.sentiment.client.TavilyClient;
import com.gdelt.sentiment.gdelt.ArticleContentFetcher;
import com.gdelt.sentiment.gdelt.GdeltClient;
import com.gdelt.sentiment.tool.*;
import org.springframework.stereotype.Component;

/**
 * Factory that creates a ToolRouter wired to all tool implementations.
 * Creates a new router per research session (bound to a specific topic).
 */
@Component
public class ToolRouterFactory {

    private final GdeltClient gdeltClient;
    private final TavilyClient tavilyClient;
    private final FredClient fredClient;
    private final ArticleContentFetcher articleContentFetcher;
    private final RateLimitedExecutor rateLimitedExecutor;

    public ToolRouterFactory(
            GdeltClient gdeltClient,
            TavilyClient tavilyClient,
            FredClient fredClient,
            ArticleContentFetcher articleContentFetcher,
            RateLimitedExecutor rateLimitedExecutor) {
        this.gdeltClient = gdeltClient;
        this.tavilyClient = tavilyClient;
        this.fredClient = fredClient;
        this.articleContentFetcher = articleContentFetcher;
        this.rateLimitedExecutor = rateLimitedExecutor;
    }

    public ToolRouter create(String topic) {
        SearchNewsTool searchNewsTool = new SearchNewsTool(gdeltClient, tavilyClient, rateLimitedExecutor);
        GetEconomicIndicatorTool getIndicatorTool = new GetEconomicIndicatorTool(fredClient, rateLimitedExecutor);
        ReadFullArticleTool readArticleTool = new ReadFullArticleTool(articleContentFetcher);
        return new ToolRouter(searchNewsTool, getIndicatorTool, readArticleTool, topic);
    }
}
