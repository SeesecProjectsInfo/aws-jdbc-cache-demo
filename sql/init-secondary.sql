-- ============================================
-- SQL Init Script for SECONDARY Database
-- ============================================
-- This script initializes the secondary (failover) MySQL database.
-- The schema is IDENTICAL to the primary database to ensure
-- seamless failover without schema mismatches.

CREATE DATABASE IF NOT EXISTS smartdb_secondary;
USE smartdb_secondary;

-- Users table (identical schema to primary)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed with the same sample data for consistency
INSERT INTO users (name, email) VALUES
    ('Alice Johnson', 'alice@example.com'),
    ('Bob Smith', 'bob@example.com'),
    ('Charlie Brown', 'charlie@example.com'),
    ('Diana Prince', 'diana@example.com'),
    ('Eve Williams', 'eve@example.com');
