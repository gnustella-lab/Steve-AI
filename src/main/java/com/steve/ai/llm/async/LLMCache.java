package com.steve.ai.llm.async;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * LRU cache for LLM responses using Caffeine.
 *
 * <p>Caches LLM responses to reduce API calls and costs. Cache key is a SHA-256 hash
 * of the combination of provider, model, and prompt.</p>
 *
 * <p><b>Cache Configuration:</b></p>
 * <ul>
 *   <li>Maximum size: 500 entries (~25MB estimated)</li>
 *   <li>TTL: 5 minutes (expireAfterWrite)</li>
 *   <li>Eviction: LRU (Least Recently Used)</li>
 *   <li>Stats: Enabled for monitoring hit/miss rates</li>
 * </ul>
 *
 * <p><b>Performance Impact:</b></p>
 * <ul>
 *   <li>Cache hit: ~1ms latency (vs 500-2000ms for API call)</li>
 *   <li>Expected hit rate: 40-60% after warmup</li>
 *   <li>Cost savings: $0.002 per 1K tokens saved</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Caffeine is fully thread-safe</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * LLMCache cache = new LLMCache();
 *
 * // Check cache before API call
 * Optional&lt;LLMResponse&gt; cached = cache.get("user prompt", "gpt-3.5-turbo", "openai");
 * if (cached.isPresent()) {
 *     return cached.get(); // Cache hit!
 * }
 *
 * // Cache miss - make API call
 * LLMResponse response = makeApiCall(...);
 * cache.put("user prompt", "gpt-3.5-turbo", "openai", response);
 *
 * // Monitor cache performance
 * CacheStats stats = cache.getStats();
 * double hitRate = stats.hitRate();
 * System.out.println("Cache hit rate: " + (hitRate * 100) + "%");
 * </pre>
 *
 * @since 1.1.0
 */
public class LLMCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(LLMCache.class);

    private static final int MAX_CACHE_SIZE = 500;
    private static final int TTL_MINUTES = 5;

    private final Cache<String, LLMResponse> cache;

    /**
     * Constructs a new LLMCache with default configuration.
     *
     * <p>Cache is configured with:</p>
     * <ul>
     *   <li>500 entry maximum (LRU eviction)</li>
     *   <li>5 minute TTL</li>
     *   <li>Statistics recording enabled</li>
     * </ul>
     */
    public LLMCache() {
        LOGGER.info("Initializing LLM cache (max size: {}, TTL: {} minutes)", MAX_CACHE_SIZE, TTL_MINUTES);

        this.cache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
            .recordStats() // Enable hit/miss tracking
            .build();

        LOGGER.info("LLM cache initialized successfully");
    }

    /**
     * Retrieves a cached response if available.
     *
     * <p><b>Performance:</b> O(1) lookup, typically <1ms</p>
     *
     * @param prompt     The prompt text (used in cache key)
     * @param model      The model name (used in cache key)
     * @param providerId The provider ID (used in cache key)
     * @return Optional containing cached response, or empty if cache miss
     */
    public Optional<LLMResponse> get(String prompt, String model, String providerId) {
        String key = generateKey(prompt, model, providerId);
        LLMResponse cached = cache.getIfPresent(key);

        if (cached != null) {
            LOGGER.debug("Cache HIT for provider={}, model={}, promptHash={}",
                providerId, model, key.substring(0, 8));
        } else {
            LOGGER.debug("Cache MISS for provider={}, model={}, promptHash={}",
                providerId, model, key.substring(0, 8));
        }

        return Optional.ofNullable(cached);
    }

    /**
     * Stores a response in the cache.
     *
     * <p>Automatically sets the {@code fromCache} flag to true on the stored response.</p>
     *
     * <p><b>Performance:</b> O(1) insertion, typically <1ms</p>
     *
     * @param prompt     The prompt text (used in cache key)
     * @param model      The model name (used in cache key)
     * @param providerId The provider ID (used in cache key)
     * @param response   The response to cache
     */
    public void put(String prompt, String model, String providerId, LLMResponse response) {
        String key = generateKey(prompt, model, providerId);

        // Mark response as cached
        LLMResponse cachedResponse = response.withCacheFlag(true);
        cache.put(key, cachedResponse);

        LOGGER.debug("Cached response for provider={}, model={}, promptHash={}, tokens={}",
            providerId, model, key.substring(0, 8), response.getTokensUsed());
    }

    /**
     * Generates a cache key from prompt, model, and provider.
     *
     * <p>Uses SHA-256 hash to ensure consistent key length and prevent cache
     * key collision. Format: "{providerId}:{model}:{prompt}" → SHA-256 hex</p>
     *
     * <p><b>Why SHA-256?</b></p>
     * <ul>
     *   <li>Fixed length (64 hex chars) regardless of prompt length</li>
     *   <li>Cryptographically secure (prevents collision attacks)</li>
     *   <li>Fast (~1μs on modern hardware)</li>
     * </ul>
     *
     * @param prompt     The prompt text
     * @param model      The model name
     * @param providerId The provider ID
     * @return SHA-256 hash as hex string (64 characters)
     */
    private String generateKey(String prompt, String model, String providerId) {
        String composite = providerId + ":" + model + ":" + prompt;
        return DigestUtils.sha256Hex(composite);
    }

    /**
     * Returns cache statistics for monitoring.
     *
     * <p><b>Available metrics:</b></p>
     * <ul>
     *   <li>{@code hitRate()}: Percentage of requests served from cache (0.0 - 1.0)</li>
     *   <li>{@code missRate()}: Percentage of cache misses (0.0 - 1.0)</li>
     *   <li>{@code hitCount()}: Total number of cache hits</li>
     *   <li>{@code missCount()}: Total number of cache misses</li>
     *   <li>{@code loadSuccessCount()}: Number of successful cache loads</li>
     *   <li>{@code evictionCount()}: Number of evicted entries</li>
     * </ul>
     *
     * <p><b>Example:</b></p>
     * <pre>
     * CacheStats stats = cache.getStats();
     * LOGGER.info("Cache hit rate: {:.2f}%, {} hits, {} misses",
     *     stats.hitRate() * 100, stats.hitCount(), stats.missCount());
     * </pre>
     *
     * @return Immutable snapshot of cache statistics
     */
    public CacheStats getStats() {
        return cache.stats();
    }

    /**
     * Returns the approximate number of entries in the cache.
     *
     * <p><b>Note:</b> This is an estimate and may not be exact due to
     * concurrent modifications and pending cleanup operations.</p>
     *
     * @return Approximate cache size
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * Invalidates all entries in the cache.
     *
     * <p><b>Use Case:</b> Testing, memory pressure, or after configuration changes</p>
     *
     * <p><b>Warning:</b> This clears all cached responses. Next requests will
     * result in cache misses and fresh API calls.</p>
     */
    public void clear() {
        long sizeBefore = cache.estimatedSize();
        cache.invalidateAll();
        LOGGER.info("Cache cleared, removed ~{} entries", sizeBefore);
    }

    /**
     * Logs current cache statistics at INFO level.
     *
     * <p>Useful for periodic monitoring and debugging.</p>
     */
    public void logStats() {
        CacheStats stats = getStats();
        LOGGER.info("LLM Cache Stats - Size: ~{}/{}, Hit Rate: {:.2f}%, Hits: {}, Misses: {}, Evictions: {}",
            size(),
            MAX_CACHE_SIZE,
            stats.hitRate() * 100,
            stats.hitCount(),
            stats.missCount(),
            stats.evictionCount()
        );
    }
}
