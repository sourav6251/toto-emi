-- =========================================================
-- V1__init_schema.sql
-- EMI Management System Initial Database Schema
-- Entity: Users
-- =========================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    userid VARCHAR(255) NOT NULL,
    full_name VARCHAR(150),
    phone_number VARCHAR(15),
    email VARCHAR(255),
    password VARCHAR(255),
    role VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    otp VARCHAR(255),
    otp_expire TIMESTAMP(6) WITHOUT TIME ZONE,
    otp_verification_failed_attempted INTEGER DEFAULT 0,
    last_failed_attempted TIMESTAMP(6) WITHOUT TIME ZONE,
    last_login_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT users_pkey PRIMARY KEY (userid),
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number),
    CONSTRAINT users_role_check CHECK (role IS NULL OR role IN ('ADMIN', 'CUSTOMER_CARE', 'CUSTOMER')),
    CONSTRAINT users_status_check CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_phone_number ON users(phone_number);
