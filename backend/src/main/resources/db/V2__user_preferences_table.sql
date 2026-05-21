-- V2: Thêm bảng user_preferences cho AI chatbot
CREATE TABLE IF NOT EXISTS user_preferences (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    gender       ENUM('MALE','FEMALE','UNISEX') DEFAULT NULL,
    age_group    VARCHAR(10)  DEFAULT NULL,
    size_info    JSON         DEFAULT NULL,
    color_pref   JSON         DEFAULT NULL,
    style_pref   JSON         DEFAULT NULL,
    budget_range JSON         DEFAULT NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_pref (user_id),
    CONSTRAINT fk_user_pref_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
