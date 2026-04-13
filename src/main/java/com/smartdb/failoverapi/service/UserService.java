package com.smartdb.failoverapi.service;

import com.smartdb.failoverapi.cache.CacheService;
import com.smartdb.failoverapi.model.User;
import com.smartdb.failoverapi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * UserService — Business logic layer with cache-first read strategy.
 * 
 * Read flow (Cache-First):
 *   1. Check Redis cache for the user
 *   2. CACHE HIT  → return immediately (fast path)
 *   3. CACHE MISS → query database via repository
 *   4. DB SUCCESS → cache the result, return it
 *   5. DB FAILURE → return empty (or stale cache if available)
 * 
 * Write flow:
 *   1. Write to database
 *   2. Evict related cache entries (cache invalidation)
 * 
 * This ensures:
 *   - Reduced DB load (cache absorbs repeated reads)
 *   - Graceful degradation (if Redis is down, DB still works)
 *   - Data freshness (writes invalidate cache)
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheService cacheService;

    /**
     * Get user by ID — implements cache-first strategy.
     * 
     * @param id User ID
     * @return Optional containing the user
     */
    public Optional<User> getUserById(Long id) {
        log.debug("🔎 Service: getUserById({})", id);

        // Step 1: Try cache first (fast path)
        Optional<User> cached = cacheService.getFromCache(id);
        if (cached.isPresent()) {
            log.debug("⚡ Returning user {} from cache (fast path).", id);
            return cached;
        }

        // Step 2: Cache miss — query database
        log.debug("📊 Cache miss for user {}. Querying database...", id);
        try {
            Optional<User> fromDb = userRepository.findById(id);

            // Step 3: If found in DB, cache it for future requests
            fromDb.ifPresent(user -> {
                cacheService.putInCache(user);
                log.debug("💾 User {} fetched from DB and cached.", id);
            });

            return fromDb;

        } catch (Exception e) {
            log.error("❌ Database query failed for user {}: {}", id, e.getMessage());

            // Step 4: Last resort — try to serve stale cache data
            // (getFromCache was already tried, but in a scenario where
            //  cache was populated by another thread between our first
            //  check and the DB failure, this could save us)
            log.warn("🔄 Attempting cache fallback after DB failure for user {}.", id);
            return cacheService.getFromCache(id);
        }
    }

    /**
     * Get all users.
     * Note: List queries are NOT cached to avoid complexity with list invalidation.
     * Individual users returned here WILL be cached for future getById calls.
     */
    public List<User> getAllUsers() {
        log.debug("🔎 Service: getAllUsers()");

        List<User> users = userRepository.findAll();

        // Cache each user individually for future getById lookups
        users.forEach(cacheService::putInCache);

        return users;
    }

    /**
     * Create a new user.
     * 
     * @param user User to create
     * @return The created user with generated ID
     * @throws IllegalArgumentException if email already exists
     */
    public User createUser(User user) {
        log.debug("📝 Service: createUser({})", user);

        // Validate email uniqueness
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email '" + user.getEmail() + "' is already registered.");
        }

        // Save to database
        User saved = userRepository.save(user);

        // Cache the newly created user
        cacheService.putInCache(saved);

        log.info("✅ User created: {}", saved);
        return saved;
    }

    /**
     * Update an existing user.
     * 
     * @param id   User ID to update
     * @param user Updated user data
     * @return Optional containing the updated user
     */
    public Optional<User> updateUser(Long id, User user) {
        log.debug("📝 Service: updateUser({}, {})", id, user);

        // Check if user exists
        Optional<User> existing = userRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        user.setId(id);
        int rowsAffected = userRepository.update(user);

        if (rowsAffected > 0) {
            // Evict stale cache entry — next read will re-populate
            cacheService.evictFromCache(id);

            // Fetch and return the updated user
            Optional<User> updated = userRepository.findById(id);
            updated.ifPresent(cacheService::putInCache);
            return updated;
        }

        return Optional.empty();
    }

    /**
     * Delete a user by ID.
     * 
     * @param id User ID to delete
     * @return true if deleted, false if not found
     */
    public boolean deleteUser(Long id) {
        log.debug("🗑️ Service: deleteUser({})", id);

        int rowsAffected = userRepository.deleteById(id);

        if (rowsAffected > 0) {
            // Remove from cache
            cacheService.evictFromCache(id);
            log.info("✅ User {} deleted.", id);
            return true;
        }

        return false;
    }
}
