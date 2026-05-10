package org.freeplane.plugin.ai.buffer;

import org.freeplane.core.util.LogUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffer-layer router.
 * Responsible for identifying the request type and selecting the appropriate buffer layer.
 * Uses a LinkedHashMap in access-order mode to implement a true LRU cache:
 *   - get/put are both O(1)
 *   - each access automatically moves the entry to the tail of the linked list
 *   - the eldest (least-recently-used) entry is evicted automatically when the limit is exceeded
 *   - thread safety is provided by Collections.synchronizedMap
 */
public class BufferLayerRouter {

    private final List<IBufferLayer> bufferLayers;

    // True LRU cache: LinkedHashMap in access-order mode.
    private final Map<String, CachedResponse> cache;
    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_EXPIRY_TIME = TimeUnit.MINUTES.toMillis(10);

    // Cache statistics.
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictCount = new AtomicLong(0);

    public BufferLayerRouter() {
        this.bufferLayers = new ArrayList<>();
        // access-order=true: each get/put moves the entry to the tail; head = least recently used.
        this.cache = Collections.synchronizedMap(
            new LinkedHashMap<String, CachedResponse>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedResponse> eldest) {
                    boolean shouldEvict = size() > MAX_CACHE_SIZE;
                    if (shouldEvict) {
                        evictCount.incrementAndGet();
                        LogUtils.info("BufferLayerRouter [LRU]: evicted eldest entry - " + eldest.getKey());
                    }
                    return shouldEvict;
                }
            }
        );
        initializeBufferLayers();
    }

    /**
     * Initialises the buffer-layer list.
     * Uses ServiceLoader to auto-discover all IBufferLayer implementations.
     */
    private void initializeBufferLayers() {
        // Auto-discover via ServiceLoader.
        ServiceLoader<IBufferLayer> loader = ServiceLoader.load(IBufferLayer.class);
        for (IBufferLayer layer : loader) {
            bufferLayers.add(layer);
            LogUtils.info("BufferLayerRouter: loaded buffer layer - " + layer.getName());
        }

        // If none found via ServiceLoader, register the default implementation manually.
        if (bufferLayers.isEmpty()) {
            registerDefaultBufferLayers();
        }

        // Sort by priority.
        bufferLayers.sort(Comparator.comparingInt(IBufferLayer::getPriority));
    }

    /**
     * Manually registers the default buffer layers.
     */
    private void registerDefaultBufferLayers() {
        try {
            // Register the mindmap buffer layer.
            Class<?> clazz = Class.forName("org.freeplane.plugin.ai.buffer.mindmap.MindMapBufferLayer");
            IBufferLayer mindMapLayer = (IBufferLayer) clazz.getDeclaredConstructor().newInstance();
            bufferLayers.add(mindMapLayer);
            LogUtils.info("BufferLayerRouter: registered MindMapBufferLayer");
        } catch (Exception e) {
            LogUtils.warn("BufferLayerRouter: failed to register MindMapBufferLayer", e);
        }
    }

    /**
     * Processes the given request by routing it to the appropriate buffer layer.
     * @param request the request context
     * @return the processing result
     */
    public BufferResponse processRequest(BufferRequest request) {
        long startTime = System.currentTimeMillis();

        // Try to serve from cache (LinkedHashMap access-order: get automatically moves the entry to the tail).
        String cacheKey = generateCacheKey(request);
        CachedResponse cachedResponse;
        synchronized (cache) {
            cachedResponse = cache.get(cacheKey);
        }
        if (cachedResponse != null) {
            if (!isCacheExpired(cachedResponse)) {
                hitCount.incrementAndGet();
                LogUtils.info("BufferLayerRouter [LRU]: cache hit for key - " + cacheKey
                    + " | hits=" + hitCount.get() + " misses=" + missCount.get());
                BufferResponse response = cachedResponse.getResponse();
                response.setProcessingTime(System.currentTimeMillis() - startTime);
                response.addLog("[LRU] Cache hit: " + cacheKey);
                return response;
            } else {
                // Lazy eviction: entry found but expired; remove from cache.
                synchronized (cache) {
                    cache.remove(cacheKey);
                }
                LogUtils.info("BufferLayerRouter [LRU]: expired entry removed - " + cacheKey);
            }
        }

        // Cache miss: process normally.
        missCount.incrementAndGet();
        LogUtils.info("BufferLayerRouter [LRU]: cache miss for key - " + cacheKey
            + " | hits=" + hitCount.get() + " misses=" + missCount.get());

        // Find a buffer layer that can handle the request.
        IBufferLayer selectedLayer = selectBufferLayer(request);
        if (selectedLayer == null) {
            BufferResponse response = new BufferResponse();
            response.setSuccess(false);
            response.setErrorMessage("No suitable buffer layer found for this request.");
            response.setProcessingTime(System.currentTimeMillis() - startTime);
            return response;
        }

        LogUtils.info("BufferLayerRouter: selected layer - " + selectedLayer.getName());

        try {
            // Delegate to the selected buffer layer.
            BufferResponse response = selectedLayer.process(request);
            response.setProcessingTime(System.currentTimeMillis() - startTime);
            
            // Cache successful responses.
            if (response.isSuccess()) {
                cacheResponse(cacheKey, response);
                response.addLog("[LRU] Cache stored: " + cacheKey);
            }
            
            return response;
        } catch (Exception e) {
            LogUtils.warn("BufferLayerRouter: processing failed", e);
            BufferResponse response = new BufferResponse();
            response.setSuccess(false);
            response.setErrorMessage("Processing failed: " + e.getMessage());
            response.setProcessingTime(System.currentTimeMillis() - startTime);
            return response;
        }
    }

    /**
     * Selects the first buffer layer that can handle the request.
     */
    private IBufferLayer selectBufferLayer(BufferRequest request) {
        for (IBufferLayer layer : bufferLayers) {
            if (layer.canHandle(request)) {
                return layer;
            }
        }
        return null;
    }

    /**
     * Generates a cache key for the given request.
     */
    private String generateCacheKey(BufferRequest request) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(request.getRequestType())
                 .append("|")
                 .append(request.getUserInput() != null ? request.getUserInput() : "");
        
        // Include parameter info.
        if (request.getParameters() != null && !request.getParameters().isEmpty()) {
            keyBuilder.append("|");
            request.getParameters().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    keyBuilder.append(entry.getKey())
                             .append("=")
                             .append(entry.getValue())
                             .append("&");
                });
        }
        
        return keyBuilder.toString();
    }

    /**
     * Caches a response.
     * LinkedHashMap's removeEldestEntry eviction is triggered automatically on put.
     */
    private void cacheResponse(String key, BufferResponse response) {
        synchronized (cache) {
            cache.put(key, new CachedResponse(response));
        }
        LogUtils.info("BufferLayerRouter [LRU]: cached response for key - " + key
            + " | cacheSize=" + cache.size());
    }

    /**
     * Returns whether the cached entry has exceeded its TTL.
     */
    private boolean isCacheExpired(CachedResponse cachedResponse) {
        return System.currentTimeMillis() - cachedResponse.getTimestamp() > CACHE_EXPIRY_TIME;
    }

    /**
     * Clears the cache and resets all statistics counters.
     */
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
        hitCount.set(0);
        missCount.set(0);
        evictCount.set(0);
        LogUtils.info("BufferLayerRouter [LRU]: cache cleared");
    }

    /**
     * Returns the current number of entries in the cache.
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Returns the cache hit count.
     */
    public long getCacheHitCount() {
        return hitCount.get();
    }

    /**
     * Returns the cache miss count.
     */
    public long getCacheMissCount() {
        return missCount.get();
    }

    /**
     * Returns the LRU eviction count.
     */
    public long getCacheEvictCount() {
        return evictCount.get();
    }

    /**
     * Returns the cache hit rate in the range [0.0, 1.0].
     */
    public double getCacheHitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0.0 : (double) hitCount.get() / total;
    }

    /**
     * Returns all registered buffer layers.
     */
    public List<IBufferLayer> getBufferLayers() {
        return new ArrayList<>(bufferLayers);
    }

    /**
     * Cache entry wrapper that records the write timestamp for TTL expiry checks.
     */
    private static class CachedResponse {
        private final BufferResponse response;
        private final long timestamp;

        public CachedResponse(BufferResponse response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        public BufferResponse getResponse() {
            return response;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}