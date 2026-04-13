package com.smartdb.failoverapi.repository;

import com.smartdb.failoverapi.failover.DBFailoverManager;
import com.smartdb.failoverapi.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * UserRepository — Data access layer using pure JDBC (no JPA/Hibernate).
 * 
 * Key design choice: This class does NOT hold a reference to a fixed JdbcTemplate.
 * Instead, it calls dbFailoverManager.getActiveJdbcTemplate() on EVERY query.
 * This ensures that if a failover happens between requests, the next query
 * automatically routes to the healthy database.
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    @Autowired
    private DBFailoverManager dbFailoverManager;

    /**
     * RowMapper — converts a ResultSet row into a User object.
     * Reusable across all SELECT queries.
     */
    private final RowMapper<User> userRowMapper = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null
    );

    /**
     * Finds a user by ID.
     * 
     * @param id User ID
     * @return Optional containing the user, or empty if not found
     */
    public Optional<User> findById(Long id) {
        log.debug("📖 Repository: findById({})", id);
        try {
            // Get the currently active JdbcTemplate (primary or secondary)
            JdbcTemplate jdbc = dbFailoverManager.getActiveJdbcTemplate();
            User user = jdbc.queryForObject(
                    "SELECT id, name, email, created_at, updated_at FROM users WHERE id = ?",
                    userRowMapper,
                    id
            );
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            log.debug("📖 User with id {} not found.", id);
            return Optional.empty();
        }
    }

    /**
     * Returns all users.
     */
    public List<User> findAll() {
        log.debug("📖 Repository: findAll()");
        JdbcTemplate jdbc = dbFailoverManager.getActiveJdbcTemplate();
        return jdbc.query(
                "SELECT id, name, email, created_at, updated_at FROM users ORDER BY id",
                userRowMapper
        );
    }

    /**
     * Inserts a new user and returns the entity with auto-generated ID.
     * 
     * Uses KeyHolder to capture the auto-generated primary key from MySQL.
     * 
     * @param user User to insert (id should be null)
     * @return The saved user with populated ID
     */
    public User save(User user) {
        log.debug("📝 Repository: save({})", user);
        JdbcTemplate jdbc = dbFailoverManager.getActiveJdbcTemplate();

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (name, email) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            return ps;
        }, keyHolder);

        // Set the auto-generated ID on the user object
        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            user.setId(generatedId.longValue());
        }

        log.info("✅ User saved with ID: {}", user.getId());
        return user;
    }

    /**
     * Updates an existing user's name and email.
     * 
     * @param user User with ID and updated fields
     * @return Number of rows affected (should be 1)
     */
    public int update(User user) {
        log.debug("📝 Repository: update({})", user);
        JdbcTemplate jdbc = dbFailoverManager.getActiveJdbcTemplate();

        return jdbc.update(
                "UPDATE users SET name = ?, email = ? WHERE id = ?",
                user.getName(),
                user.getEmail(),
                user.getId()
        );
    }

    /**
     * Deletes a user by ID.
     * 
     * @param id User ID to delete
     * @return Number of rows affected
     */
    public int deleteById(Long id) {
        log.debug("🗑️ Repository: deleteById({})", id);
        JdbcTemplate jdbc = dbFailoverManager.getActiveJdbcTemplate();

        return jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    /**
     * Checks if a user with the given email already exists.
     */
    public boolean existsByEmail(String email) {
        JdbcTemplate jdbc = dbFailoverManager.getActiveJdbcTemplate();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }
}
