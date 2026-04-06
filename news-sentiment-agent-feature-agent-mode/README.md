# Macroeconomic Research Agent

An agentic macroeconomic research system that researches like a real macroeconomist -- decomposing broad queries into targeted sub-queries, searching across multiple dimensions, evaluating coverage gaps, and iterating until all macro dimensions are covered. Then maps the macro analysis to your actual portfolio stocks with actionable position recommendations.

## Architecture

```
POST /api/macro-analysis         POST /api/portfolio-impact
        │                                │
        ▼                                ▼
┌─────────────────────┐     ┌─────────────────────┐
│ MacroResearch       │     │ MacroResearch        │
│ Orchestrator        │     │ Orchestrator         │
│ (Cursor-like        │     │         +            │
│  ReAct loop)        │     │ Upstox Holdings      │
│                     │     │         +            │
│ Tools:              │     │ StockImpact          │
│ - searchNews        │     │ Analyzer             │
│ - getEconomicData   │     └─────────────────────┘
│ - readFullArticle   │              │
│ - storeFinding      │              ▼
│ - checkCoverage     │     PortfolioMacroReport
│ - submitMacroView   │     (MacroView + per-stock
└─────────────────────┘      actionable analysis)
        │
        ▼
    MacroView
    (9 dimensions)
```

## Prerequisites

- Java 17+
- Maven 3.8+

## API Keys Required

| Service | Purpose | Get it from |
|---------|---------|-------------|
| **Anthropic** | Claude LLM (reasoning engine) | https://console.anthropic.com/ |
| **Tavily** | Web search API | https://tavily.com/ |
| **FRED** | Economic data (free) | https://fred.stlouisfed.org/docs/api/api_key.html |
| **Upstox** | Broker API (optional) | https://upstox.com/developer/api-documentation/ |

## Setup

1. Set environment variables:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export TAVILY_API_KEY=tvly-...
export FRED_API_KEY=your-fred-key

# Optional: Upstox (for portfolio-impact endpoint)
export UPSTOX_API_KEY=your-api-key
export UPSTOX_API_SECRET=your-secret
export UPSTOX_ACCESS_TOKEN=your-token  # after OAuth login
```

2. Build and run:

```bash
mvn clean package -DskipTests
java -jar target/macro-research-agent-1.0.0-SNAPSHOT.jar
```

Or with Maven:

```bash
mvn spring-boot:run
```

## API Endpoints

### Macro Analysis

```bash
curl -X POST http://localhost:8080/api/macro-analysis \
  -H "Content-Type: application/json" \
  -d '{"topic": "India macro outlook"}'
```

Returns a structured `MacroView` with per-dimension assessments across:
Growth, Labor, Inflation, Monetary, Fiscal, External/Trade, Financial Conditions, Business Cycle, Commodities.

### Portfolio Impact

```bash
curl -X POST http://localhost:8080/api/portfolio-impact \
  -H "Content-Type: application/json" \
  -d '{"topic": "India macro outlook"}'
```

Runs macro analysis, fetches your Upstox holdings, then produces per-stock impact analysis with:
- Sector identification
- Impact direction and score
- Position bias (OVERWEIGHT / UNDERWEIGHT / NEUTRAL / AVOID)
- Reasoning citing specific macro findings

### Upstox Authentication

```bash
# Get the auth URL
curl http://localhost:8080/api/upstox/auth-url

# After login, Upstox redirects to:
# http://localhost:8080/api/upstox/callback?code=...
```

### Health Check

```bash
curl http://localhost:8080/api/health
```

## How the Agent Works

The research agent uses a **Cursor-like ReAct loop**:

1. **Safety cap, not research budget** -- The agent decides when research is complete, not an iteration counter.
2. **Explicit termination** -- The loop only exits when the agent calls `submitMacroView`.
3. **Auto-injected coverage** -- Every few tool calls, the orchestrator shows the agent what dimensions are covered and what's missing.
4. **Parallel tool execution** -- Multiple API calls run concurrently with per-source rate limiting.
5. **Token optimization** -- Result compression + sliding window summarization keeps costs low.

## Data Sources

| Source | What it provides | Rate limit |
|--------|-----------------|------------|
| GDELT | Global news articles (keyword-based) | 1 req / 10s |
| Tavily | Web search (semantic, broader coverage) | 1 req / 1s |
| FRED | Economic data (GDP, CPI, rates, etc.) | 2 req / 1s |
| Upstox | Portfolio holdings and market quotes | Per Upstox plan |

## Configuration

All configuration is in `src/main/resources/application.properties`. Key settings:

| Property | Default | Description |
|----------|---------|-------------|
| `agent.safety-limit` | 50 | Max iterations (safety cap) |
| `agent.token-budget` | 25000 | Token budget before compaction |
| `agent.coverage-check-interval` | 4 | Tool calls between coverage injections |
| `anthropic.model-name` | claude-sonnet-4-20250514 | Claude model to use |
