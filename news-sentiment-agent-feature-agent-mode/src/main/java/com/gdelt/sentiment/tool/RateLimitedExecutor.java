package com.gdelt.sentiment.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Wraps a cached thread pool with per-source token bucket rate limiters.
 * Calls to different sources run concurrently; calls to the same source
 * are serialized by the rate limiter.
 */
public class RateLimitedExecutor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedExecutor.class);

    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Map<String, TokenBucket> limiters;
    private final int maxRetries;

    public RateLimitedExecutor() {
        this.maxRetries = 3;
        this.limiters = new ConcurrentHashMap<>(Map.of(
                "GDELT", new TokenBucket(10_000),
                "TAVILY", new TokenBucket(1_000),
                "FRED", new TokenBucket(500)
        ));
    }

    public RateLimitedExecutor(Map<String, Long> intervalMillis, int maxRetries) {
        this.maxRetries = maxRetries;
        this.limiters = new ConcurrentHashMap<>();
        intervalMillis.forEach((source, interval) -> limiters.put(source, new TokenBucket(interval)));
    }

    /**
     * Submit a task for a given source, respecting that source's rate limit.
     */
    public CompletableFuture<String> submit(String source, Callable<String> task) {
        return CompletableFuture.supplyAsync(() -> executeWithRetry(source, task), threadPool);
    }

    /**
     * Execute synchronously with rate limiting and retry on rate-limit errors.
     */
    public String executeBlocking(String source, Callable<String> task) {
        return executeWithRetry(source, task);
    }

    private String executeWithRetry(String source, Callable<String> task) {
        TokenBucket limiter = limiters.get(source);

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (limiter != null) {
                limiter.acquire();
            }

            try {
                return task.call();
            } catch (RateLimitException e) {
                long backoff = (long) Math.pow(2, attempt) * 1000L;
                log.warn("[RateLimit] {} rate-limited (attempt {}/{}), backing off {}ms",
                        source, attempt + 1, maxRetries, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "{\"error\":\"interrupted\",\"source\":\"" + source + "\"}";
                }
            } catch (Exception e) {
                log.warn("[RateLimit] {} call failed: {}", source, e.getMessage());
                return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\",\"source\":\"" + source + "\"}";
            }
        }

        String msg = source + " rate-limited after " + maxRetries + " retries. "
                + "Consider using a different source to avoid delays.";
        log.error("[RateLimit] {}", msg);
        return "{\"error\":\"" + msg + "\",\"source\":\"" + source + "\"}";
    }

    public void shutdown() {
        threadPool.shutdown();
    }

    /**
     * Simple token bucket rate limiter per source.
     */
    static class TokenBucket {
        private final long intervalMillis;
        private long lastCallTime = 0;

        TokenBucket(long intervalMillis) {
            this.intervalMillis = intervalMillis;
        }

        synchronized void acquire() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastCallTime;
            if (lastCallTime > 0 && elapsed < intervalMillis) {
                try {
                    Thread.sleep(intervalMillis - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCallTime = System.currentTimeMillis();
        }
    }

    /**
     * Thrown by tool implementations when a 429 response is received.
     */
    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
