/**
 * Admin Dashboard Data Cache Manager
 * Optimizes API calls by caching data and avoiding redundant requests
 * Implements intelligent cache invalidation and refresh strategies
 */

(function() {
    'use strict';

    /**
     * Cache configuration
     */
    const CACHE_CONFIG = {
        // Cache durations in milliseconds
        USERS: 5 * 60 * 1000,           // 5 minutes
        PAYMENTS: 3 * 60 * 1000,        // 3 minutes
        REFUNDS: 3 * 60 * 1000,         // 3 minutes
        SERVICES: 10 * 60 * 1000,       // 10 minutes (services change less frequently)
        REPORTS: 5 * 60 * 1000,         // 5 minutes
        METRICS: 2 * 60 * 1000,         // 2 minutes (metrics need fresher data)
        
        // Stale-while-revalidate durations (show stale data while fetching fresh)
        STALE_WHILE_REVALIDATE: 30 * 1000  // 30 seconds
    };

    /**
     * Cache entry structure
     */
    class CacheEntry {
        constructor(data, ttl) {
            this.data = data;
            this.timestamp = Date.now();
            this.ttl = ttl;
            this.expiresAt = this.timestamp + ttl;
        }

        isExpired() {
            return Date.now() > this.expiresAt;
        }

        isStale() {
            const staleThreshold = this.expiresAt - CACHE_CONFIG.STALE_WHILE_REVALIDATE;
            return Date.now() > staleThreshold;
        }

        getAge() {
            return Date.now() - this.timestamp;
        }
    }

    /**
     * Main cache manager
     */
    class AdminDataCache {
        constructor() {
            this.cache = new Map();
            this.pendingRequests = new Map();
            this.stats = {
                hits: 0,
                misses: 0,
                staleHits: 0,
                errors: 0
            };
        }

        /**
         * Generate cache key from endpoint and params
         */
        generateKey(endpoint, params = {}) {
            const sortedParams = Object.keys(params)
                .sort()
                .map(key => `${key}=${JSON.stringify(params[key])}`)
                .join('&');
            return `${endpoint}?${sortedParams}`;
        }

        /**
         * Get data from cache or fetch from API
         */
        async get(endpoint, params = {}, ttl = CACHE_CONFIG.USERS, fetchFn) {
            const key = this.generateKey(endpoint, params);
            const entry = this.cache.get(key);

            // Cache hit - return fresh data
            if (entry && !entry.isExpired()) {
                this.stats.hits++;
                console.log(`[Cache HIT] ${key} (age: ${Math.round(entry.getAge() / 1000)}s)`);
                
                // Stale-while-revalidate: return stale data but refresh in background
                if (entry.isStale()) {
                    this.stats.staleHits++;
                    console.log(`[Cache STALE] ${key} - refreshing in background`);
                    this.refreshInBackground(key, fetchFn, ttl);
                }
                
                return entry.data;
            }

            // Cache miss - fetch from API
            this.stats.misses++;
            console.log(`[Cache MISS] ${key}`);
            
            // Check if there's already a pending request for this key
            if (this.pendingRequests.has(key)) {
                console.log(`[Cache PENDING] ${key} - waiting for existing request`);
                return this.pendingRequests.get(key);
            }

            // Fetch data
            const promise = this.fetchAndCache(key, fetchFn, ttl);
            this.pendingRequests.set(key, promise);
            
            try {
                const data = await promise;
                return data;
            } finally {
                this.pendingRequests.delete(key);
            }
        }

        /**
         * Fetch data and store in cache
         */
        async fetchAndCache(key, fetchFn, ttl) {
            try {
                const data = await fetchFn();
                const entry = new CacheEntry(data, ttl);
                this.cache.set(key, entry);
                console.log(`[Cache SET] ${key} (ttl: ${Math.round(ttl / 1000)}s)`);
                return data;
            } catch (error) {
                this.stats.errors++;
                console.error(`[Cache ERROR] ${key}:`, error);
                throw error;
            }
        }

        /**
         * Refresh data in background (stale-while-revalidate)
         */
        async refreshInBackground(key, fetchFn, ttl) {
            try {
                const data = await fetchFn();
                const entry = new CacheEntry(data, ttl);
                this.cache.set(key, entry);
                console.log(`[Cache REFRESH] ${key}`);
            } catch (error) {
                console.warn(`[Cache REFRESH ERROR] ${key}:`, error);
                // Keep stale data on refresh error
            }
        }

        /**
         * Invalidate specific cache entry
         */
        invalidate(endpoint, params = {}) {
            const key = this.generateKey(endpoint, params);
            const deleted = this.cache.delete(key);
            if (deleted) {
                console.log(`[Cache INVALIDATE] ${key}`);
            }
            return deleted;
        }

        /**
         * Invalidate all cache entries matching a pattern
         */
        invalidatePattern(pattern) {
            let count = 0;
            for (const key of this.cache.keys()) {
                if (key.includes(pattern)) {
                    this.cache.delete(key);
                    count++;
                }
            }
            if (count > 0) {
                console.log(`[Cache INVALIDATE PATTERN] ${pattern} (${count} entries)`);
            }
            return count;
        }

        /**
         * Clear all cache
         */
        clear() {
            const size = this.cache.size;
            this.cache.clear();
            this.pendingRequests.clear();
            console.log(`[Cache CLEAR] Cleared ${size} entries`);
        }

        /**
         * Remove expired entries
         */
        cleanup() {
            let removed = 0;
            for (const [key, entry] of this.cache.entries()) {
                if (entry.isExpired()) {
                    this.cache.delete(key);
                    removed++;
                }
            }
            if (removed > 0) {
                console.log(`[Cache CLEANUP] Removed ${removed} expired entries`);
            }
            return removed;
        }

        /**
         * Get cache statistics
         */
        getStats() {
            const totalRequests = this.stats.hits + this.stats.misses;
            const hitRate = totalRequests > 0 
                ? Math.round((this.stats.hits / totalRequests) * 100) 
                : 0;
            
            return {
                ...this.stats,
                totalRequests,
                hitRate: `${hitRate}%`,
                cacheSize: this.cache.size,
                pendingRequests: this.pendingRequests.size
            };
        }

        /**
         * Reset statistics
         */
        resetStats() {
            this.stats = {
                hits: 0,
                misses: 0,
                staleHits: 0,
                errors: 0
            };
        }
    }

    // Create global cache instance
    window.AdminDataCache = new AdminDataCache();

    /**
     * Cached API wrapper functions
     */
    window.CachedAPI = {
        /**
         * Fetch users with caching
         */
        async getUsers(page = 1, pageSize = 10, forceRefresh = false) {
            if (forceRefresh) {
                window.AdminDataCache.invalidatePattern('/admin/users');
            }

            return window.AdminDataCache.get(
                '/admin/users',
                { page, pageSize },
                CACHE_CONFIG.USERS,
                async () => {
                    if (typeof window.UserRoute !== 'undefined' && window.UserRoute.listUsers) {
                        return await window.UserRoute.listUsers(page, pageSize);
                    }
                    throw new Error('UserRoute not available');
                }
            );
        },

        /**
         * Fetch all users (for reports) with caching
         */
        async getAllUsers(forceRefresh = false) {
            if (forceRefresh) {
                window.AdminDataCache.invalidate('/admin/users/all', {});
            }

            return window.AdminDataCache.get(
                '/admin/users/all',
                {},
                CACHE_CONFIG.REPORTS,
                async () => {
                    // Fetch all users by requesting a large page size
                    if (typeof window.UserRoute !== 'undefined' && window.UserRoute.listUsers) {
                        const result = await window.UserRoute.listUsers(1, 10000);
                        return result.users || [];
                    }
                    throw new Error('UserRoute not available');
                }
            );
        },

        /**
         * Fetch payments with caching
         */
        async getPayments(page = 1, pageSize = 20, filters = {}, forceRefresh = false) {
            if (forceRefresh) {
                window.AdminDataCache.invalidatePattern('/admin/payments');
            }

            return window.AdminDataCache.get(
                '/admin/payments',
                { page, pageSize, ...filters },
                CACHE_CONFIG.PAYMENTS,
                async () => {
                    // Implement actual payment fetch logic here
                    const url = `/admin/payments?page=${page}&pageSize=${pageSize}`;
                    const response = await fetch(url, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Accept': 'application/json' }
                    });
                    
                    if (!response.ok) {
                        throw new Error('Failed to fetch payments');
                    }
                    
                    const data = await response.json();
                    return data.data || {};
                }
            );
        },

        /**
         * Fetch refunds with caching
         */
        async getRefunds(page = 1, pageSize = 20, filters = {}, forceRefresh = false) {
            if (forceRefresh) {
                window.AdminDataCache.invalidatePattern('/admin/refunds');
            }

            return window.AdminDataCache.get(
                '/admin/refunds/history',
                { page, pageSize, ...filters },
                CACHE_CONFIG.REFUNDS,
                async () => {
                    const params = new URLSearchParams({ page, pageSize, ...filters });
                    const url = `/admin/refunds/history?${params}`;
                    
                    const response = await fetch(url, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Accept': 'application/json' }
                    });
                    
                    if (!response.ok) {
                        throw new Error('Failed to fetch refunds');
                    }
                    
                    const data = await response.json();
                    return data.data || {};
                }
            );
        },

        /**
         * Fetch refund metrics with caching
         */
        async getRefundMetrics(forceRefresh = false) {
            if (forceRefresh) {
                window.AdminDataCache.invalidate('/admin/refunds/metrics', {});
            }

            return window.AdminDataCache.get(
                '/admin/refunds/metrics',
                {},
                CACHE_CONFIG.METRICS,
                async () => {
                    const response = await fetch('/admin/refunds/metrics', {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Accept': 'application/json' }
                    });
                    
                    if (!response.ok) {
                        throw new Error('Failed to fetch refund metrics');
                    }
                    
                    const data = await response.json();
                    return data.data || {};
                }
            );
        },

        /**
         * Fetch services with caching
         */
        async getServices(page = 1, pageSize = 20, filters = {}, forceRefresh = false) {
            if (forceRefresh) {
                window.AdminDataCache.invalidatePattern('/admin/services');
            }

            return window.AdminDataCache.get(
                '/admin/services',
                { page, pageSize, ...filters },
                CACHE_CONFIG.SERVICES,
                async () => {
                    const params = new URLSearchParams({ page, pageSize, ...filters });
                    const url = `/admin/services?${params}`;
                    
                    const response = await fetch(url, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Accept': 'application/json' }
                    });
                    
                    if (!response.ok) {
                        throw new Error('Failed to fetch services');
                    }
                    
                    const data = await response.json();
                    return data.data || {};
                }
            );
        },

        /**
         * Invalidate cache after mutations
         */
        invalidateUsers() {
            window.AdminDataCache.invalidatePattern('/admin/users');
        },

        invalidatePayments() {
            window.AdminDataCache.invalidatePattern('/admin/payments');
        },

        invalidateRefunds() {
            window.AdminDataCache.invalidatePattern('/admin/refunds');
        },

        invalidateServices() {
            window.AdminDataCache.invalidatePattern('/admin/services');
        },

        /**
         * Clear all cache
         */
        clearAll() {
            window.AdminDataCache.clear();
        }
    };

    /**
     * Auto-cleanup expired entries every 5 minutes
     */
    setInterval(() => {
        window.AdminDataCache.cleanup();
    }, 5 * 60 * 1000);

    /**
     * Log cache stats every minute in development
     */
    if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        setInterval(() => {
            const stats = window.AdminDataCache.getStats();
            console.log('[Cache Stats]', stats);
        }, 60 * 1000);
    }

    console.log('[Admin Data Cache] Initialized');
})();
