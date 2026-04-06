package com.gdelt.sentiment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdelt.sentiment.tool.RateLimitedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Client for the Federal Reserve Economic Data (FRED) API.
 * Provides hard economic data points (GDP, CPI, unemployment, etc.)
 */
public class FredClient {

    private static final Logger log = LoggerFactory.getLogger(FredClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    /**
     * Maps human-readable indicator names to FRED series IDs.
     */
    public static final Map<String, String> SERIES_MAP = Map.ofEntries(
            Map.entry("GDP", "GDPC1"),
            Map.entry("CPI", "CPIAUCSL"),
            Map.entry("CORE_CPI", "CPILFESL"),
            Map.entry("PPI", "PPIACO"),
            Map.entry("PCE", "PCEPI"),
            Map.entry("UNEMPLOYMENT", "UNRATE"),
            Map.entry("NONFARM_PAYROLLS", "PAYEMS"),
            Map.entry("FED_FUNDS_RATE", "FEDFUNDS"),
            Map.entry("TREASURY_10Y", "DGS10"),
            Map.entry("TREASURY_2Y", "DGS2"),
            Map.entry("TRADE_BALANCE", "BOPGSTB"),
            Map.entry("IMPORTS", "IMPGS"),
            Map.entry("EXPORTS", "EXPGS"),
            Map.entry("GOLD", "GOLDAMGBD228NLBM"),
            Map.entry("OIL_WTI", "DCOILWTICO"),
            Map.entry("DOLLAR_INDEX", "DTWEXBGS"),
            Map.entry("PMI_MANUFACTURING", "MANEMP"),
            Map.entry("CONSUMER_CONFIDENCE", "UMCSENT"),
            Map.entry("INDUSTRIAL_PRODUCTION", "INDPRO"),
            Map.entry("RETAIL_SALES", "RSXFS"),
            Map.entry("HOUSING_STARTS", "HOUST"),
            Map.entry("INITIAL_CLAIMS", "ICSA"),
            Map.entry("FEDERAL_DEBT", "GFDEBTN"),
            Map.entry("M2_MONEY_SUPPLY", "M2SL"),
            Map.entry("VIX", "VIXCLS")
    );

    public record Observation(String date, String value) {}

    public record SeriesData(String seriesId, String title, List<Observation> observations, String units, String frequency) {}

    public FredClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.stlouisfed.org/fred";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Fetch recent observations for a FRED series.
     * Accepts either a human-readable name (e.g. "CPI") or a FRED series ID (e.g. "CPIAUCSL").
     */
    public SeriesData getObservations(String indicator, int limit) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[FRED] No API key configured");
            return new SeriesData(indicator, indicator, List.of(), "", "");
        }

        String seriesId = SERIES_MAP.getOrDefault(indicator.toUpperCase(), indicator);

        try {
            String url = baseUrl + "/series/observations"
                    + "?series_id=" + URLEncoder.encode(seriesId, StandardCharsets.UTF_8)
                    + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                    + "&file_type=json"
                    + "&sort_order=desc"
                    + "&limit=" + Math.max(1, Math.min(limit, 100));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                throw new RateLimitedExecutor.RateLimitException("FRED API rate limited (429)");
            }

            if (response.statusCode() != 200) {
                log.warn("[FRED] API returned {}: {}", response.statusCode(), response.body());
                return new SeriesData(seriesId, seriesId, List.of(), "", "");
            }

            return parseObservations(seriesId, response.body());
        } catch (RateLimitedExecutor.RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[FRED] Failed to fetch {}: {}", seriesId, e.getMessage());
            return new SeriesData(seriesId, seriesId, List.of(), "", "");
        }
    }

    public SeriesData getObservations(String indicator) {
        return getObservations(indicator, 12);
    }

    private SeriesData parseObservations(String seriesId, String body) {
        List<Observation> observations = new ArrayList<>();
        String units = "";
        String frequency = "";

        try {
            JsonNode root = MAPPER.readTree(body);

            if (root.has("units")) units = root.get("units").asText();
            if (root.has("frequency")) frequency = root.get("frequency").asText();

            JsonNode obsNode = root.get("observations");
            if (obsNode != null && obsNode.isArray()) {
                for (JsonNode obs : obsNode) {
                    String date = obs.has("date") ? obs.get("date").asText() : "";
                    String value = obs.has("value") ? obs.get("value").asText() : ".";
                    if (!".".equals(value)) {
                        observations.add(new Observation(date, value));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FRED] Failed to parse response for {}: {}", seriesId, e.getMessage());
        }

        return new SeriesData(seriesId, seriesId, observations, units, frequency);
    }

    public static List<String> availableIndicators() {
        return new ArrayList<>(SERIES_MAP.keySet());
    }
}
