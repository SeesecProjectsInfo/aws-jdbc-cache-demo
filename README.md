# 🚀 Smart DB Failover API with Caching using JDBC

A production-ready Spring Boot REST API that demonstrates **automatic database failover** and **Redis caching** using pure JDBC (no JPA/Hibernate).

---

## 🏗️ Architecture Overview

```
                    ┌─────────────┐
                    │   Client    │
                    │  (REST API) │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  Controller │
                    │    Layer    │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │   Service   │──────────┐
                    │    Layer    │          │
                    └──────┬──────┘          │
                           │          ┌─────▼─────┐
                    ┌──────▼──────┐   │   Redis   │
                    │ Repository  │   │   Cache   │
                    │   (JDBC)    │   │  (60s TTL)│
                    └──────┬──────┘   └───────────┘
                           │
                ┌──────────▼──────────┐
                │  DBFailoverManager  │
                │  + Circuit Breaker  │
                └────┬──────────┬─────┘
                     │          │
              ┌──────▼──┐  ┌───▼───────┐
              │ Primary │  │ Secondary │
              │  MySQL  │  │   MySQL   │
              │ (:3306) │  │  (:3307)  │
              └─────────┘  └───────────┘
```

---

## 🧱 Tech Stack

| Technology    | Purpose                          |
|---------------|----------------------------------|
| Java 17       | Runtime                          |
| Spring Boot   | Application framework            |
| Spring JDBC   | Database access (NO JPA)         |
| MySQL 8.0     | Primary + Secondary databases    |
| Redis 7       | Caching layer                    |
| HikariCP      | Connection pooling               |
| Docker        | Container orchestration          |
| Maven         | Build & dependency management    |

---

## 📁 Project Structure

```
src/main/java/com/smartdb/failoverapi/
├── SmartDbFailoverApiApplication.java   # Main entry point
├── controller/
│   ├── UserController.java              # REST endpoints for Users
│   └── HealthController.java            # Health & metrics endpoints
├── service/
│   └── UserService.java                 # Business logic + cache orchestration
├── repository/
│   └── UserRepository.java             # Pure JDBC data access
├── model/
│   └── User.java                        # Domain model
├── config/
│   ├── AppConfig.java                   # App-wide configuration
│   └── RedisConfig.java                 # Redis template config
├── failover/
│   ├── DBFailoverManager.java           # ⭐ Core failover logic
│   └── CircuitBreaker.java              # Circuit breaker state machine
├── cache/
│   └── CacheService.java               # Redis cache-first strategy
├── exception/
│   └── GlobalExceptionHandler.java      # Structured error responses
└── metrics/
    └── FailoverMetrics.java             # Thread-safe counters
```

---

## ⚙️ How It Works

### 1. Database Failover (DBFailoverManager)

The failover manager maintains **two HikariCP connection pools** and uses a **circuit breaker** to decide which database to route queries to:

```
Request → getActiveJdbcTemplate()
        → Circuit Breaker: "try primary?"
          YES → test primary connection
                SUCCESS → use primary ✅
                FAILURE → record failure, use secondary ⚠️
          NO  → use secondary directly
```

**Circuit Breaker States:**

| State     | Meaning                                    | Action                          |
|-----------|--------------------------------------------|---------------------------------|
| CLOSED    | Primary is healthy                         | Route all queries to primary    |
| OPEN      | Primary is down (≥3 failures)              | Route all queries to secondary  |
| HALF_OPEN | Cooldown expired, testing primary          | Try one query on primary        |

### 2. Caching Strategy (Cache-First)

```
GET /users/{id}
  → Check Redis cache
    HIT  → Return cached data (⚡ fast)
    MISS → Query database
           → Store in cache (TTL: 60s)
           → Return data
```

- **Reads**: Cache-first with DB fallback
- **Writes**: Write to DB → evict stale cache
- **Redis down**: Graceful degradation (DB-only mode)

### 3. Auto-Recovery

A **scheduled health check** (every 15 seconds) probes the primary database when in failover mode. When primary recovers, traffic automatically routes back.

---

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven 3.8+

### Step 1: Start Infrastructure

```bash
# Start MySQL (primary + secondary) and Redis
docker-compose up -d

# Wait for databases to initialize (~15 seconds)
docker-compose ps
```

### Step 2: Build & Run the Application

```bash
# Build the project
mvn clean package -DskipTests

# Run the application
mvn spring-boot:run
```

Or with Java directly:
```bash
java -jar target/failover-api-1.0.0.jar
```

### Step 3: Test the API

```bash
# Check health (should show PRIMARY database active)
curl http://localhost:8080/health | jq

# Get all users (seeded data)
curl http://localhost:8080/users | jq

# Get a specific user
curl http://localhost:8080/users/1 | jq

# Create a new user
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name": "Frank Castle", "email": "frank@example.com"}' | jq
```

---

## 🧪 Demo: Simulating Failover

### Step 1: Verify Primary is Active

```bash
curl http://localhost:8080/health | jq '.database'
# Expected: { "activeDatabase": "PRIMARY", "circuitBreakerState": "CLOSED" }
```

### Step 2: Stop the Primary Database

```bash
docker-compose stop mysql-primary
```

### Step 3: Make a Request (Triggers Failover)

```bash
# First few requests record failures; after 3 failures, failover triggers
curl http://localhost:8080/users/1 | jq

# Check health — should now show SECONDARY
curl http://localhost:8080/health | jq '.database'
# Expected: { "activeDatabase": "SECONDARY", "circuitBreakerState": "OPEN" }
```

Watch the application logs — you'll see:
```
⚠️ Primary DB failure #1/3. Circuit still CLOSED.
⚠️ Primary DB failure #2/3. Circuit still CLOSED.
🔴 Circuit breaker OPENED — 3 consecutive failures. Routing to secondary DB.
🔴 FAILOVER: Primary DB is down. Switching to SECONDARY database.
```

### Step 4: Restart Primary (Auto-Recovery)

```bash
docker-compose start mysql-primary
```

Within 15-30 seconds, the scheduled health check detects recovery:
```
⏱️ Circuit breaker cooldown expired. Transitioning to HALF_OPEN.
✅ Circuit breaker CLOSED — primary DB recovered successfully.
🔄 RECOVERY (scheduled): Primary DB is back online! Switching back.
```

### Step 5: Verify Recovery

```bash
curl http://localhost:8080/health | jq '.database'
# Expected: { "activeDatabase": "PRIMARY", "circuitBreakerState": "CLOSED" }
```

---

## 🧪 Demo: Caching in Action

### Step 1: First Request (Cache Miss → DB Query)

```bash
curl http://localhost:8080/users/1
# Logs: 🔴 CACHE MISS for key 'user:1'
# Logs: 💾 Cached user 'Alice Johnson' with key 'user:1' (TTL: 60s)
```

### Step 2: Second Request (Cache Hit → No DB Query)

```bash
curl http://localhost:8080/users/1
# Logs: 🟢 CACHE HIT for key 'user:1'
# (No database query executed — served from Redis!)
```

### Step 3: Check Cache Metrics

```bash
curl http://localhost:8080/health | jq '.cache'
# { "redis": "CONNECTED", "cacheHits": 1, "cacheMisses": 1, "cacheHitRate": "50.0%" }
```

### Step 4: Wait 60s (TTL Expires) → Cache Miss Again

```bash
# After 60 seconds...
curl http://localhost:8080/users/1
# Logs: 🔴 CACHE MISS for key 'user:1' (TTL expired)
```

---

## 📡 API Reference

### Users

| Method | Endpoint        | Description          |
|--------|-----------------|----------------------|
| GET    | `/users`        | List all users       |
| GET    | `/users/{id}`   | Get user by ID       |
| POST   | `/users`        | Create a new user    |
| PUT    | `/users/{id}`   | Update a user        |
| DELETE | `/users/{id}`   | Delete a user        |

### System

| Method | Endpoint          | Description                          |
|--------|-------------------|--------------------------------------|
| GET    | `/health`         | Active DB, circuit breaker, cache    |
| GET    | `/metrics/custom` | Detailed failover & cache metrics    |

### Request/Response Examples

**Create User:**
```json
POST /users
{
  "name": "John Doe",
  "email": "john@example.com"
}
// Response: 201 Created
{
  "id": 6,
  "name": "John Doe",
  "email": "john@example.com",
  "createdAt": "2025-01-01T12:00:00",
  "updatedAt": "2025-01-01T12:00:00"
}
```

**Health Check:**
```json
GET /health
{
  "status": "UP",
  "timestamp": "2025-01-01T12:00:00Z",
  "database": {
    "activeDatabase": "PRIMARY",
    "circuitBreakerState": "CLOSED",
    "circuitBreakerDetails": "state=CLOSED, failures=0/3, cooldown=30s"
  },
  "cache": {
    "redis": "CONNECTED",
    "cacheHits": 15,
    "cacheMisses": 5,
    "cacheHitRate": "75.0%"
  },
  "failover": {
    "totalFailovers": 0,
    "totalRecoveries": 0,
    "primaryQueryCount": 20,
    "secondaryQueryCount": 0
  }
}
```

---

## 🗄️ Database Schema

Both primary and secondary databases use the same schema:

```sql
CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

---

## ⚙️ Configuration

All configuration is in `src/main/resources/application.yml`:

| Property                           | Default       | Description                       |
|------------------------------------|---------------|-----------------------------------|
| `datasource.primary.url`           | `localhost:3306` | Primary MySQL connection       |
| `datasource.secondary.url`         | `localhost:3307` | Secondary MySQL connection     |
| `failover.cooldown-seconds`        | `30`          | Wait before retrying primary     |
| `failover.failure-threshold`       | `3`           | Failures before opening circuit  |
| `failover.validation-timeout-seconds` | `5`       | Connection validation timeout    |
| `spring.data.redis.host`           | `localhost`   | Redis host                       |
| `spring.data.redis.port`           | `6379`        | Redis port                       |
| `spring.cache.redis.time-to-live`  | `60000`       | Cache TTL in milliseconds        |

---

## 📊 Metrics Tracked

| Metric               | Description                              |
|----------------------|------------------------------------------|
| `totalFailovers`     | Times system switched to secondary DB    |
| `totalRecoveries`    | Times system recovered back to primary   |
| `cacheHits`          | Requests served from Redis cache         |
| `cacheMisses`        | Requests that required a DB query        |
| `primaryQueryCount`  | Total queries routed to primary DB       |
| `secondaryQueryCount`| Total queries routed to secondary DB     |

---

## 🛑 Stopping Everything

```bash
# Stop the Spring Boot app (Ctrl+C)

# Stop and remove all containers
docker-compose down

# Stop and remove containers + data volumes
docker-compose down -v
```

---

## 📝 Key Design Decisions

1. **Pure JDBC** — No JPA/Hibernate. Direct SQL via `JdbcTemplate` for full control.
2. **Per-query failover** — `getActiveJdbcTemplate()` is called on every DB request, so failover is transparent.
3. **Circuit breaker** — Avoids hammering a dead primary. Configurable threshold and cooldown.
4. **Graceful degradation** — If Redis is down, the app still works (DB-only mode). If primary DB is down, secondary takes over.
5. **Scheduled recovery** — Background task probes primary every 15s during failover, so recovery doesn't depend on user traffic.

---

## 📄 License

MIT License — Use freely for learning and production.
#   a w s - j d b c - c a c h e - d e m o  
 