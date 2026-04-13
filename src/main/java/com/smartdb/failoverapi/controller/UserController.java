package com.smartdb.failoverapi.controller;

import com.smartdb.failoverapi.model.User;
import com.smartdb.failoverapi.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * UserController — REST API endpoints for User CRUD operations.
 * 
 * Endpoints:
 *   GET    /users       → List all users
 *   GET    /users/{id}  → Get user by ID
 *   POST   /users       → Create a new user
 *   PUT    /users/{id}  → Update an existing user
 *   DELETE /users/{id}  → Delete a user
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    /**
     * GET /users — List all users.
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        log.info("🌐 GET /users");
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * GET /users/{id} — Get a single user by ID.
     * 
     * Response:
     *   200 OK         → User found (may be from cache or DB)
     *   404 NOT FOUND  → User does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        log.info("🌐 GET /users/{}", id);

        Optional<User> user = userService.getUserById(id);

        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "User not found",
                            "id", id
                    ));
        }
    }

    /**
     * POST /users — Create a new user.
     * 
     * Request body:
     *   { "name": "John Doe", "email": "john@example.com" }
     * 
     * Response:
     *   201 CREATED    → User created successfully
     *   400 BAD REQUEST → Validation failed (e.g., duplicate email)
     */
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody User user) {
        log.info("🌐 POST /users — name='{}', email='{}'", user.getName(), user.getEmail());

        // Basic validation
        if (user.getName() == null || user.getName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Name is required"));
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email is required"));
        }

        try {
            User created = userService.createUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Failed to create user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create user. Both databases may be unavailable."));
        }
    }

    /**
     * PUT /users/{id} — Update an existing user.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User user) {
        log.info("🌐 PUT /users/{}", id);

        Optional<User> updated = userService.updateUser(id, user);

        if (updated.isPresent()) {
            return ResponseEntity.ok(updated.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found", "id", id));
        }
    }

    /**
     * DELETE /users/{id} — Delete a user.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        log.info("🌐 DELETE /users/{}", id);

        boolean deleted = userService.deleteUser(id);

        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "User deleted successfully", "id", id));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found", "id", id));
        }
    }
}
