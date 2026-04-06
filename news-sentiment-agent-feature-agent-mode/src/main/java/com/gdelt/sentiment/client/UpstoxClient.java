package com.gdelt.sentiment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.model.StockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for Upstox API v2.
 * Handles OAuth2 token exchange, portfolio holdings, positions, and market quotes.
 */
public class UpstoxClient {

    private static final Logger log = LoggerFactory.getLogger(UpstoxClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String apiSecret;
    private final String redirectUri;
    private final String baseUrl;
    private String accessToken;
    private final HttpClient httpClient;

    public UpstoxClient(String apiKey, String apiSecret, String redirectUri, String baseUrl, String accessToken) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.redirectUri = redirectUri;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.upstox.com/v2";
        this.accessToken = accessToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Exchange an OAuth2 authorization code for an access token.
     */
    public String exchangeCodeForToken(String authCode) {
        try {
            String body = "code=" + authCode
                    + "&client_id=" + apiKey
                    + "&client_secret=" + apiSecret
                    + "&redirect_uri=" + redirectUri
                    + "&grant_type=authorization_code";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/login/authorization/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[Upstox] Token exchange failed ({}): {}", response.statusCode(), response.body());
                throw new RuntimeException("Upstox token exchange failed: " + response.statusCode());
            }

            JsonNode json = MAPPER.readTree(response.body());
            this.accessToken = json.get("access_token").asText();
            log.info("[Upstox] Access token obtained successfully");
            return this.accessToken;
        } catch (Exception e) {
            log.error("[Upstox] Token exchange failed: {}", e.getMessage());
            throw new RuntimeException("Upstox token exchange failed", e);
        }
    }

    /**
     * Fetch long-term holdings from the user's DEMAT account.
     */
    public List<StockInfo> getHoldings(String token) {
        String effectiveToken = token != null ? token : this.accessToken;
        return fetchPortfolio(effectiveToken, "/portfolio/long-term-holdings", "data");
    }

    public List<StockInfo> getHoldings() {
        return getHoldings(this.accessToken);
    }

    /**
     * Fetch current day positions.
     */
    public List<StockInfo> getPositions(String token) {
        String effectiveToken = token != null ? token : this.accessToken;
        return fetchPortfolio(effectiveToken, "/user/get-positions", "data");
    }

    public List<StockInfo> getPositions() {
        return getPositions(this.accessToken);
    }

    /**
     * Fetch market quotes for given instrument keys.
     */
    public Map<String, Double> getMarketQuotes(String token, List<String> instrumentKeys) {
        String effectiveToken = token != null ? token : this.accessToken;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            log.warn("[Upstox] No access token configured");
            return Map.of();
        }

        try {
            String instruments = String.join(",", instrumentKeys);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/market-quote/quotes?instrument_key=" + instruments))
                    .header("Authorization", "Bearer " + effectiveToken)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[Upstox] Market quotes failed ({}): {}", response.statusCode(), response.body());
                return Map.of();
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode data = root.get("data");
            Map<String, Double> quotes = new java.util.HashMap<>();
            if (data != null && data.isObject()) {
                data.fields().forEachRemaining(entry -> {
                    JsonNode quote = entry.getValue();
                    if (quote.has("last_price")) {
                        quotes.put(entry.getKey(), quote.get("last_price").asDouble());
                    }
                });
            }
            return quotes;
        } catch (Exception e) {
            log.warn("[Upstox] Market quotes failed: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Get the OAuth2 authorization URL for user login.
     */
    public String getAuthorizationUrl() {
        return "https://api.upstox.com/v2/login/authorization/dialog"
                + "?client_id=" + apiKey
                + "&redirect_uri=" + redirectUri
                + "&response_type=code";
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public boolean hasValidToken() {
        return accessToken != null && !accessToken.isBlank();
    }

    private List<StockInfo> fetchPortfolio(String token, String endpoint, String dataField) {
        if (token == null || token.isBlank()) {
            log.warn("[Upstox] No access token for portfolio fetch");
            return List.of();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[Upstox] Portfolio fetch failed ({}): {}", response.statusCode(), response.body());
                return List.of();
            }

            return parsePortfolio(response.body(), dataField);
        } catch (Exception e) {
            log.warn("[Upstox] Portfolio fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<StockInfo> parsePortfolio(String body, String dataField) {
        List<StockInfo> stocks = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode data = root.get(dataField);
            if (data == null || !data.isArray()) return stocks;

            for (JsonNode item : data) {
                String symbol = getField(item, "tradingsymbol", "trading_symbol");
                String name = getField(item, "company_name", "name");
                String isin = getField(item, "isin", "");
                String instrumentKey = getField(item, "instrument_token", "");
                String exchange = getField(item, "exchange", "NSE");
                double quantity = getNumericField(item, "quantity");
                double avgPrice = getNumericField(item, "average_price");
                double lastPrice = getNumericField(item, "last_price", "close_price");

                if (symbol != null && !symbol.isBlank()) {
                    stocks.add(new StockInfo(symbol, name, isin, instrumentKey, exchange, quantity, avgPrice, lastPrice));
                }
            }
        } catch (Exception e) {
            log.warn("[Upstox] Failed to parse portfolio: {}", e.getMessage());
        }
        return stocks;
    }

    private String getField(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return "";
    }

    private double getNumericField(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asDouble(0.0);
            }
        }
        return 0.0;
    }
}
