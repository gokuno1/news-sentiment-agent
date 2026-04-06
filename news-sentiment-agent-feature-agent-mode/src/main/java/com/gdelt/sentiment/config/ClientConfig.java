package com.gdelt.sentiment.config;

import com.gdelt.sentiment.client.FredClient;
import com.gdelt.sentiment.client.TavilyClient;
import com.gdelt.sentiment.client.UpstoxClient;
import com.gdelt.sentiment.gdelt.ArticleContentFetcher;
import com.gdelt.sentiment.gdelt.GdeltClient;
import com.gdelt.sentiment.tool.RateLimitedExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClientConfig {

    @Bean
    public GdeltClient gdeltClient(
            @Value("${gdelt.base-url:https://api.gdeltproject.org/api/v2/doc/doc}") String baseUrl,
            @Value("${gdelt.delay-seconds:10}") int delaySeconds,
            @Value("${gdelt.max-records-per-request:50}") int maxRecords,
            @Value("${gdelt.timespan:5d}") String timespan) {
        return new GdeltClient(baseUrl, delaySeconds, maxRecords, timespan);
    }

    @Bean
    public ArticleContentFetcher articleContentFetcher() {
        return new ArticleContentFetcher();
    }

    @Bean
    public TavilyClient tavilyClient(
            @Value("${tavily.api-key:}") String apiKey,
            @Value("${tavily.base-url:https://api.tavily.com}") String baseUrl,
            @Value("${tavily.search-depth:advanced}") String searchDepth,
            @Value("${tavily.max-results:10}") int maxResults) {
        return new TavilyClient(apiKey, baseUrl, searchDepth, maxResults);
    }

    @Bean
    public FredClient fredClient(
            @Value("${fred.api-key:}") String apiKey,
            @Value("${fred.base-url:https://api.stlouisfed.org/fred}") String baseUrl) {
        return new FredClient(apiKey, baseUrl);
    }

    @Bean
    public UpstoxClient upstoxClient(
            @Value("${upstox.api-key:}") String apiKey,
            @Value("${upstox.api-secret:}") String apiSecret,
            @Value("${upstox.redirect-uri:http://localhost:8080/api/upstox/callback}") String redirectUri,
            @Value("${upstox.base-url:https://api.upstox.com/v2}") String baseUrl,
            @Value("${upstox.access-token:}") String accessToken) {
        return new UpstoxClient(apiKey, apiSecret, redirectUri, baseUrl, accessToken);
    }

    @Bean
    public RateLimitedExecutor rateLimitedExecutor() {
        return new RateLimitedExecutor();
    }
}
