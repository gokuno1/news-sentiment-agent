# GDELT Sentiment AI Agent

Java-based AI agent that fetches news from the GDELT Doc 2.0 API, uses Ollama with Mistral 7B (via LangChain4j) to compute sentiment, and keeps researching until 90% confidence. RAG with a local-disk embedding store reduces prompt length; only unique news (by URL) is stored.

## Prerequisites

- **Java 17+**
- **Ollama** with Mistral model (e.g. `ollama run mistral`)
- **Maven** (or use `mvn wrapper:wrapper` then `./mvnw`)

## Build

```bash
mvn clean package
```

## Run (pipeline mode, default)

```bash
java -jar target/gdelt-sentiment-agent-1.0.0-SNAPSHOT.jar "climate change"
# Or with classpath:
mvn exec:java -Dexec.mainClass="com.gdelt.sentiment.SentimentAgentService" -Dexec.args="gold prices"
```

This uses the original deterministic research loop (`agent.mode=pipeline`).

## Run in agent mode (agentic macroeconomist)

Agent mode lets a macroeconomist-style agent use tools to search GDELT, inspect articles, and then submit a final macro sentiment report.

1. **Ensure Ollama is running** (same as pipeline mode), e.g.:

   ```bash
   ollama run mistral
   ```

2. **Enable agent mode** by setting `agent.mode=agentic`. You can do this either:

   - In `src/main/resources/application.properties`:

     ```properties
     agent.mode=agentic
     ```

   - Or via system property / env var when running:

     ```bash
     java -Dagent.mode=agentic -jar target/gdelt-sentiment-agent-1.0.0-SNAPSHOT.jar "emerging markets AND rates"
     # or
     mvn exec:java \
       -Dagent.mode=agentic \
       -Dexec.mainClass="com.gdelt.sentiment.SentimentAgentService" \
       -Dexec.args="US labor market AND inflation"
     ```

3. (Optional) **Fetch full article content** from GDELT URLs instead of just snippets:

   ```properties
   gdelt.fetchFullContent=true
   ```

   With this enabled, both pipeline and agent modes will try to download the article HTML for each URL and extract main text; otherwise only the GDELT-provided snippet is used.

The CLI output format is the same in both modes:

- Analysis (in agent mode: macroeconomic framing)
- Sentiment score
- Confidence

## Configuration

Edit `src/main/resources/application.properties` or set system properties / env vars:

| Key | Default | Description |
|-----|---------|-------------|
| `ollama.baseUrl` | http://localhost:11434 | Ollama server |
| `ollama.modelName` | mistral | Chat model name |
| `ollama.embeddingModelName` | nomic-embed-text | Embedding model for RAG |
| `gdelt.baseUrl` | GDELT Doc 2.0 URL | GDELT API base |
| `gdelt.delaySeconds` | 5 | Delay between GDELT calls (rate limit) |
| `embedding.store.path` | ./data/embedding-store | Local path for vector store |
| `agent.maxIterations` | 5 | Max research loop iterations |
| `agent.confidenceThreshold` | 0.9 | Exit when confidence >= this |
| `rag.topK` | 25 | Top-k segments to pass to scorer |
| `agent.mode` | pipeline | `pipeline` (deterministic loop) or `agentic` (agentic macroeconomist with tools) |
| `gdelt.fetchFullContent` | false | If true, fetch full article HTML and extract text for scoring |

## Output

- **Analysis**: Brief reasoning (5–7 sentences)
- **Sentiment score**: -1.0 (negative) to 1.0 (positive)
- **Confidence**: 0.0–1.0

## Tests

```bash
mvn test
```
