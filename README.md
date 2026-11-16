# AI Agent

A Java-based AI companion built with Spring Boot, Ollama, Qwen, MySQL, and LangChain4j.

## Current Features

- Chat with a local LLM
- Persistent conversation history
- Long-term memory storage
- MySQL integration
- REST API

## Requirements

- Java 21+
- Maven
- MySQL
- Ollama

## Setup

1. Install Ollama and download the required Qwen model.
2. Create the database using the SQL script located in:

        database/setup.sql

3. Update the database and Ollama settings in:

        src/main/resources/application.properties

4. Start the application.

## Database

The setup script creates two tables:

### messages

Stores full conversation history.

### memories

Stores long-term information the AI should remember about the user.

## Roadmap

- Memory relevance scoring
- Discord integration
- Voice support
- Autonomous goals and tasks
- Game integration

## Purpose

This project is primarily a learning project focused on building an AI companion with persistence, memory, and increasing autonomy over time.