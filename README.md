# 🚀 Smart DB Failover API with Caching using JDBC

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java CI with Maven](https://github.com/seesec/aws-jdbc-cache-demo/actions/workflows/build.yml/badge.svg)](https://github.com/seesec/aws-jdbc-cache-demo/actions/workflows/build.yml)

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
              │ (:3308) │  │  (:3307)  │
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

### Step 1: Configure Environment

```bash
# Copy the example environment file
cp .env.example .env
```

### Step 2: Start Infrastructure

```bash
# Start MySQL (primary + secondary) and Redis
docker-compose up -d

# Wait for databases to initialize (~15 seconds)
docker-compose ps
```

### Step 3: Build & Run the Application

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

### Step 4: Test the API

Open the included premium dashboard!
Navigate to **http://localhost:8080** to see live system metrics, active databases, and failover/cache trackers.

---

## 🧪 Demo: Simulating Failover

### Step 1: Verify Primary is Active

Go to your dashboard (http://localhost:8080) and wait until it says the active database is `PRIMARY`.

### Step 2: Stop the Primary Database

```bash
docker-compose stop mysql-primary
```

### Step 3: Make a Request (Triggers Failover)

Use the API tester at the bottom of the dashboard. Click "Fetch User Layer" a few times.
The circuit breaker will recognize failures and switch to `SECONDARY` automatically. 

### Step 4: Restart Primary (Auto-Recovery)

```bash
docker-compose start mysql-primary
```

Within 15-30 seconds, the scheduled health check detects recovery. You will see the dashboard seamlessly snap back to `PRIMARY`.

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
| `datasource.primary.url`           | `localhost:3308` | Primary MySQL connection       |
| `datasource.secondary.url`         | `localhost:3307` | Secondary MySQL connection     |
| `failover.cooldown-seconds`        | `30`          | Wait before retrying primary     |
| `failover.failure-threshold`       | `3`           | Failures before opening circuit  |
| `failover.validation-timeout-seconds` | `5`       | Connection validation timeout    |
| `spring.data.redis.host`           | `localhost`   | Redis host                       |
| `spring.data.redis.port`           | `6379`        | Redis port                       |
| `spring.cache.redis.time-to-live`  | `60000`       | Cache TTL in milliseconds        |

---

## 📝 Key Design Decisions

1. **Pure JDBC** — No JPA/Hibernate. Direct SQL via `JdbcTemplate` for full control.
2. **Per-query failover** — `getActiveJdbcTemplate()` is called on every DB request, so failover is transparent.
3. **Circuit breaker** — Avoids hammering a dead primary. Configurable threshold and cooldown.
4. **Graceful degradation** — If Redis is down, the app still works (DB-only mode). If primary DB is down, secondary takes over.
5. **Scheduled recovery** — Background task probes primary every 15s during failover, so recovery doesn't depend on user traffic.