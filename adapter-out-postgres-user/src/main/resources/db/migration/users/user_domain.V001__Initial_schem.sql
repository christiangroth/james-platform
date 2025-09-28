-- Create ENUM types
CREATE TYPE password_status AS ENUM ('ONE_TIME', 'PERMANENT');
CREATE TYPE user_role AS ENUM ('ADMIN', 'DEVELOPER', 'USER');
CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE');

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    password_status password_status NOT NULL,
    roles user_role[] NOT NULL,
    status user_status NOT NULL,
    status_reason TEXT,
    deactivation_counter SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_modified_column();
