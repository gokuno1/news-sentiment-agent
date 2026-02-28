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

## Run

```bash
java -jar target/gdelt-sentiment-agent-1.0.0-SNAPSHOT.jar "climate change"
# Or with classpath:
mvn exec:java -Dexec.mainClass="com.gdelt.sentiment.SentimentAgentService" -Dexec.args="gold prices"
```

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

## Output

- **Analysis**: Brief reasoning (5–7 sentences)
- **Sentiment score**: -1.0 (negative) to 1.0 (positive)
- **Confidence**: 0.0–1.0

## Tests

```bash
mvn test
```
