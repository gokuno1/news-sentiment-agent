package com.gdelt.sentiment;

import com.gdelt.sentiment.agent.MacroResearchOrchestrator;
import com.gdelt.sentiment.agent.StockImpactAnalyzer;
import com.gdelt.sentiment.client.UpstoxClient;
import com.gdelt.sentiment.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MacroAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(MacroAnalysisController.class);

    private final MacroResearchOrchestrator orchestrator;
    private final StockImpactAnalyzer stockImpactAnalyzer;
    private final UpstoxClient upstoxClient;

    public MacroAnalysisController(
            MacroResearchOrchestrator orchestrator,
            StockImpactAnalyzer stockImpactAnalyzer,
            UpstoxClient upstoxClient) {
        this.orchestrator = orchestrator;
        this.stockImpactAnalyzer = stockImpactAnalyzer;
        this.upstoxClient = upstoxClient;
    }

    /**
     * Run macro research only. Returns structured MacroView.
     */
    @PostMapping("/macro-analysis")
    public ResponseEntity<MacroView> macroAnalysis(@RequestBody MacroAnalysisRequest request) {
        log.info("[API] POST /api/macro-analysis - topic: '{}'", request.topic());

        MacroView macroView = orchestrator.research(request.topic());
        return ResponseEntity.ok(macroView);
    }

    /**
     * Run macro research + fetch Upstox watchlist + stock impact analysis.
     * Returns the full PortfolioMacroReport with per-stock actionable recommendations.
     */
    @PostMapping("/portfolio-impact")
    public ResponseEntity<PortfolioMacroReport> portfolioImpact(@RequestBody PortfolioImpactRequest request) {
        log.info("[API] POST /api/portfolio-impact - topic: '{}'", request.topic());

        // 1. Run macro research
        MacroView macroView = orchestrator.research(request.topic());

        // 2. Fetch stocks from Upstox
        String token = request.accessToken() != null ? request.accessToken() : null;
        List<StockInfo> stocks = new ArrayList<>();

        try {
            List<StockInfo> holdings = upstoxClient.getHoldings(token);
            stocks.addAll(holdings);
            log.info("[API] Fetched {} holdings from Upstox", holdings.size());
        } catch (Exception e) {
            log.warn("[API] Failed to fetch Upstox holdings: {}", e.getMessage());
        }

        try {
            List<StockInfo> positions = upstoxClient.getPositions(token);
            stocks.addAll(positions);
            log.info("[API] Fetched {} positions from Upstox", positions.size());
        } catch (Exception e) {
            log.warn("[API] Failed to fetch Upstox positions: {}", e.getMessage());
        }

        if (stocks.isEmpty()) {
            log.warn("[API] No stocks fetched from Upstox. Check access token.");
            return ResponseEntity.ok(new PortfolioMacroReport(
                    macroView, List.of(),
                    "no stocks available - check Upstox authentication",
                    Map.of(), macroView.timestamp()));
        }

        // 3. Run stock impact analysis
        PortfolioMacroReport report = stockImpactAnalyzer.analyze(macroView, stocks);
        log.info("[API] Portfolio impact analysis complete: {} stocks analyzed", report.stockImpacts().size());

        return ResponseEntity.ok(report);
    }

    /**
     * OAuth2 callback for Upstox login.
     * User visits the auth URL, Upstox redirects here with a code.
     */
    @GetMapping("/upstox/callback")
    public ResponseEntity<Map<String, String>> upstoxCallback(@RequestParam("code") String code) {
        log.info("[API] Upstox OAuth callback received");
        try {
            String token = upstoxClient.exchangeCodeForToken(code);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Access token obtained. You can now use /api/portfolio-impact."));
        } catch (Exception e) {
            log.error("[API] Upstox token exchange failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    /**
     * Get the Upstox authorization URL for initial login.
     */
    @GetMapping("/upstox/auth-url")
    public ResponseEntity<Map<String, String>> upstoxAuthUrl() {
        return ResponseEntity.ok(Map.of("authUrl", upstoxClient.getAuthorizationUrl()));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "macro-research-agent",
                "upstoxConnected", String.valueOf(upstoxClient.hasValidToken())));
    }
}
