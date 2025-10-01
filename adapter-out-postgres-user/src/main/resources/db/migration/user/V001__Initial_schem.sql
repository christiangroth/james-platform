-- TODO use Custom ENUM types
--CREATE TYPE password_status AS ENUM ('ONE_TIME', 'PERMANENT');
--CREATE TYPE user_role AS ENUM ('ADMIN', 'DEVELOPER', 'USER');
--CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE');

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    password_status VARCHAR(255) NOT NULL,
    roles VARCHAR(255)[] NOT NULL,
    status VARCHAR(255) NOT NULL,
    status_reason VARCHAR(255),
    deactivation_counter INTEGER NOT NULL DEFAULT 0
        CHECK (deactivation_counter >= 0 AND deactivation_counter <= 65535)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
