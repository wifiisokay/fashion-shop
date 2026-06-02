ALTER TABLE users
ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE TABLE token_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti VARCHAR(100) NOT NULL UNIQUE,
    user_id BIGINT NULL,
    expires_at DATETIME NOT NULL,
    reason VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_token_blacklist_jti (jti),
    INDEX idx_token_blacklist_expires_at (expires_at),
    INDEX idx_token_blacklist_user_id (user_id),

    CONSTRAINT fk_token_blacklist_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL
);
