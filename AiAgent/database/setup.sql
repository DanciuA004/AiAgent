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