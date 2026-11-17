CREATE DATABASE IF NOT EXISTS ai_agent;

USE ai_agent;

CREATE TABLE IF NOT EXISTS messages (
    id CHAR(36) PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS memories (
    id CHAR(36) PRIMARY KEY,
    memory_text TEXT NOT NULL,
    importance INT NOT NULL,
    created_at DATETIME NOT NULL
);

SET @message_created_at_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'messages'
      AND index_name = 'idx_messages_created_at'
);

SET @create_message_created_at_index = IF(
    @message_created_at_index_exists = 0,
    'CREATE INDEX idx_messages_created_at ON messages (created_at)',
    'SELECT 1'
);

PREPARE create_message_created_at_index FROM @create_message_created_at_index;
EXECUTE create_message_created_at_index;
DEALLOCATE PREPARE create_message_created_at_index;