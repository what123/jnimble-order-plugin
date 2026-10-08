CREATE TABLE IF NOT EXISTS app_auth_token (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    token         VARCHAR(64)  NOT NULL,
    user_id       VARCHAR(64)  NOT NULL,
    username      VARCHAR(128) NOT NULL,
    display_name  VARCHAR(128),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    expires_at    DATETIME     NOT NULL,
    last_used_at  DATETIME,
    created_at    DATETIME     NOT NULL,
    UNIQUE KEY uk_app_auth_token (token),
    KEY idx_app_auth_token_user (user_id)
);
