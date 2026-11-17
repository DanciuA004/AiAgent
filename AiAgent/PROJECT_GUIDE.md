# AiAgent Project Guide

This guide describes the current project structure, the purpose of each
application file, the methods it provides, and the configuration used to run
the Java application and Minecraft bridge.

## What the project does today

The project is a Java 21 / Spring Boot web application with:

- A chat endpoint that saves conversation messages, adds selected memories to
  the prompt, and streams a response from an Ollama model using server-sent
  events (SSE).
- JPA persistence for chat messages and memories in MySQL.
- A Minecraft session API implemented by a local Mineflayer bridge. It can
  connect the bot to its configured server and disconnect it.

## Request and data flow

### Chat

1. A client sends a message to `POST /chat`.
2. `Controller` calls `Service.streamChat`.
3. `Service` loads recent history, saves the new user message, asks
   `MemoryService` to evaluate it, and loads stored memories.
4. The service builds a bounded prompt and sends it to the streaming Ollama
   model.
5. Response chunks are sent over SSE. Once complete, the assistant response
   is saved to MySQL.

### Minecraft

1. A client sends `POST` or `DELETE` to `/games/minecraft/session`.
2. `MinecraftController` delegates the lifecycle operation to
   `MinecraftAdapter`.
3. `MineflayerMinecraftAdapter` makes the corresponding HTTP request to the
   local Node bridge.
4. `minecraft-bridge/server.js` connects or disconnects the Mineflayer bot and
   returns basic session state.

The bridge binds to `127.0.0.1`. Run it on the same machine as the Spring
application unless you deliberately change and secure this design.

## Project files

### Root files

#### `pom.xml`

Maven project definition for the Spring application.

- Sets the project coordinates to `com.agent:AiAgent:1.0-SNAPSHOT`.
- Inherits dependency management from Spring Boot `3.5.3`.
- Sets Java to version 21.
- Declares Spring Web for REST endpoints and SSE, LangChain4j plus its Ollama
  integration for model calls, Spring Data JPA for persistence, and MySQL
  Connector/J for the database.

#### `.gitignore`

Excludes generated build output (`target/`), editor files, and common IDE
metadata from Git. It does not exclude application source or the database
setup script.

#### `PROJECT_GUIDE.md`

This document. It describes the code and configuration currently in the
repository.

### Database

#### `database/setup.sql`

Creates the `ai_agent` database and its tables if they do not already exist:

- `messages`: UUID-like character ID, role, content, and creation timestamp.
- `memories`: ID, memory text, importance score, and creation timestamp.

The script also checks for and creates the `idx_messages_created_at` index
only when it is missing. Run this script against MySQL before starting the
application if the database and tables have not already been set up.

### Spring application source

#### `src/main/java/com/agent/App.java`

Spring Boot entry point.

- `main(String[] args)`: starts the Spring application and embedded web server.

#### `src/main/java/com/agent/config/ChatModelConfiguration.java`

Creates the Ollama model beans injected into the services.

- `ollamaRequestParameters(...)`: checks the context/output token limits and
  builds shared Ollama request parameters, including context length, maximum
  output tokens, and the thinking option. Request truncation is disabled.
- `chatModel(...)`: creates a synchronous `ChatModel`, used by
  `MemoryService` for memory extraction.
- `streamingChatModel(...)`: creates a `StreamingChatModel`, used by `Service`
  for user-facing chat responses.

#### `src/main/java/com/agent/controller/Controller.java`

REST controller for chat, rooted at `/chat`.

- `Controller(Service service)`: receives the chat service.
- `chat(String message)`: handles `POST /chat`, taking the raw request body as
  the user message and returning an SSE stream.

#### `src/main/java/com/agent/controller/MinecraftController.java`

REST controller for the Minecraft adapter, rooted at `/games/minecraft`.

- `MinecraftController(MinecraftAdapter adapter)`: receives the game adapter.
- `start()`: `POST /games/minecraft/session`; starts the configured bot session.
- `stop()`: `DELETE /games/minecraft/session`; stops the session.

#### `src/main/java/com/agent/dao/MessageRepository.java`

Spring Data JPA repository for `Message` entities.

- `findTop19ByOrderByCreatedAtDesc()`: retrieves up to 19 newest messages.

`save`, `findById`, and other standard CRUD methods are inherited from
`JpaRepository`.

#### `src/main/java/com/agent/dao/MemoryRepository.java`

Spring Data JPA repository for `Memory` entities.

- `findTop20ByOrderByImportanceDesc()`: retrieves up to 20 memories ordered by
  importance, descending.
- `existsByMemoryText(String memoryText)`: derived existence query for exact
  memory text. It is defined by the repository but is not currently used by
  `MemoryService`.

Standard CRUD methods are inherited from `JpaRepository`.

#### `src/main/java/com/agent/dto/Message.java`

JPA entity mapped to the `messages` table.

- The no-argument constructor supports JPA.
- `getId` / `setId`: access the UUID primary key.
- `getRole` / `setRole`: access the speaker role, such as `user` or
  `assistant`.
- `getContent` / `setContent`: access the message text.
- `getCreatedAt` / `setCreatedAt`: access the message creation timestamp.

#### `src/main/java/com/agent/dto/Memory.java`

JPA entity mapped to the `memories` table.

- The no-argument constructor supports JPA.
- `getId` / `setId`: access the UUID primary key.
- `getMemoryText` / `setMemoryText`: access the stored fact.
- `getImportance` / `setImportance`: access its ordering score.
- `getCreatedAt` / `setCreatedAt`: access the creation timestamp.

#### `src/main/java/com/agent/service/Service.java`

Coordinates chat history, memory context, prompt sizing, model streaming, and
response persistence.

- `Service(...)`: receives persistence and model dependencies and validates
  that input, output, and safety-margin token limits fit the configured model
  context window.
- `streamChat(String userMessage)`: loads recent history; saves the user
  message; evaluates and loads memories; limits the prompt size; streams model
  text through an `SseEmitter`; saves the completed assistant response; and
  reports errors to the emitter and logs.
- `truncate(String text, int maxChars, boolean keepEnd)`: private helper that
  limits text length, adds a truncation marker, optionally keeps the end of
  the text, and avoids splitting a UTF-16 surrogate pair.

The prompt currently reserves approximately 35% of available context
characters for history and 15% for memories; the remaining space is for the
current user message. Character-to-token conversion is an estimate.

#### `src/main/java/com/agent/service/MessageService.java`

Persists messages and assembles history for the prompt.

- `MessageService(MessageRepository messageRepository)`: receives the message
  repository.
- `saveMessage(String role, String content)`: creates a message with a random
  UUID and current local timestamp, then saves it.
- `getRecentConversationHistory()`: fetches up to 19 newest messages, reverses
  them into chronological order, and joins them as `role: content` lines.

#### `src/main/java/com/agent/service/MemoryService.java`

Extracts possible durable user facts and supplies memory context.

- `MemoryService(MemoryRepository memoryRepository, ChatModel model)`: receives
  the repository and synchronous model.
- `rememberIfRelevant(String userMessage)`: skips null messages and those
  without a matching personal-fact cue; otherwise asks the model to extract a
  concise fact and saves the result unless it is `NONE`.
- `getMemoryContext()`: loads the 20 most important memories and joins their
  text with newlines.
- `extractMemoryOrNone(String userMessage)`: private helper that prompts the
  model to return either one memory or exactly `NONE`.
- `saveMemory(String memoryText)`: private helper that saves nonblank memory
  text with a random UUID, importance `100`, and current local timestamp.

The cue pattern is a cheap filter before the model call. Current memories are
not scoped to a user, deduplicated, or explicitly deletable through an API.

#### `src/main/java/com/agent/game/GameAdapter.java`

Generic contract for a game adapter's session lifecycle.

- `start()`: starts or connects to a game and returns its observation.
- `stop()`: stops the game session and returns the resulting observation.

The generic type parameter is the adapter's observation type.

#### `src/main/java/com/agent/game/GameState.java`

Immutable record describing the bot's Minecraft session status.

- `Position(double x, double y, double z)`: nested record for world coordinates.

The state includes the session status, bot username, spawn status, position,
health, food, dimension, and latest error. Some fields are nullable when the
bot is not spawned or the value is unavailable.

#### `src/main/java/com/agent/game/MinecraftAdapter.java`

Minecraft session lifecycle extension of `GameAdapter<GameState>`.

- Inherits `start()` and `stop()`.

#### `src/main/java/com/agent/game/MineflayerMinecraftAdapter.java`

Spring service implementing `MinecraftAdapter` through HTTP calls to the local
Node bridge.

- `MineflayerMinecraftAdapter(RestClient.Builder, String bridgeUrl)`: creates
  the HTTP client using `minecraft.bridge-url`, defaulting to
  `http://127.0.0.1:8765`.
- `start()`: calls the bridge's `POST /session`.
- `stop()`: calls the bridge's `DELETE /session`.

Bridge HTTP failures are surfaced by Spring's `RestClient`.

### Spring resources

#### `src/main/resources/application.properties`

Default runtime configuration for the Spring application:

| Property | Current default | Purpose |
|---|---:|---|
| `server.port` | `8080` | HTTP port for the Spring application. |
| `ai.model` | `qwen3.5:4b` | Ollama model name used by both model beans. |
| `ai.base-url` | `http://localhost:11434` | Ollama server URL. |
| `ai.ollama.num-ctx` | `4096` | Ollama context window; can be set with `AI_OLLAMA_NUM_CTX`. |
| `ai.ollama.think` | `false` | Ollama thinking option; can be set with `AI_OLLAMA_THINK`. |
| `ai.context.max-tokens` | `3072` | Maximum input context budget used by chat prompt construction; can be set with `AI_CONTEXT_MAX_TOKENS`. |
| `ai.context.safety-margin-tokens` | `256` | Reserved context tokens; can be set with `AI_CONTEXT_SAFETY_MARGIN_TOKENS`. |
| `ai.output.max-tokens` | `256` | Maximum generated tokens; can be set with `AI_MAX_OUTPUT_TOKENS`. |
| `ai.http.stream-timeout-ms` | `120000` | SSE emitter timeout; can be set with `AI_STREAM_TIMEOUT_MS`. |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/ai_agent` | MySQL JDBC connection. |
| `spring.datasource.username` | `root` | MySQL username. |
| `spring.datasource.password` | Configured locally | MySQL password. Keep credentials out of source control; prefer an environment variable or secret manager. |
| `spring.jpa.hibernate.ddl-auto` | `none` | Hibernate does not create or alter schema. |
| `spring.jpa.show-sql` | `false` | Suppresses SQL output in application logs. |

`MinecraftAdapter` also reads `minecraft.bridge-url`, defaulting to
`http://127.0.0.1:8765`. Spring's environment-variable mapping allows
`MINECRAFT_BRIDGE_URL` to override it.

The context properties must fit together: input tokens plus output tokens plus
safety margin must not exceed the Ollama context window. The application
validates this at startup.

### Minecraft bridge

#### `minecraft-bridge/package.json`

Node package manifest for the local bridge.

- Requires Node.js 20 or newer.
- `npm start` runs `node server.js`.
- Declares Mineflayer as the Minecraft protocol client dependency.

#### `minecraft-bridge/package-lock.json`

Lockfile generated by npm. It records exact dependency versions for repeatable
installs; update it through npm when changing bridge dependencies.

#### `minecraft-bridge/.gitignore`

Excludes `node_modules/` from source control.

#### `minecraft-bridge/README.md`

Bridge-specific setup instructions and a short list of Spring API endpoints.
This project guide provides broader application and method-level context.

#### `minecraft-bridge/server.js`

Node HTTP server and Mineflayer session manager.

- `send(response, status, body)`: writes a JSON response.
- `getState()`: returns session state and basic bot status.
- `startSession()`: connects the configured bot, reuses an existing connection,
  waits for spawn, and times out after 30 seconds.
- `stopSession()`: disconnects the bot and marks the session stopped.
- `handle(request, response)`: routes only session connect and disconnect
  requests and reports errors as JSON.
- `shutdown()`: disconnects the bot and closes the HTTP server on process
  shutdown.

Bridge routes:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/session` | Connect and wait for bot spawn. |
| `DELETE` | `/session` | Disconnect the bot. |

The bridge handles only session lifecycle operations and binds to loopback.

## Configuration and running

### Requirements

- Java 21.
- Maven.
- MySQL with the schema from `database/setup.sql`.
- Ollama running locally (by default at `http://localhost:11434`) with the
  configured model available.
- Node.js 20+ and npm for the Minecraft bridge.
- A Minecraft Java server reachable by the bridge and an account permitted to
  join it.

### Start the Spring application

From the project root, ensure the database and Ollama settings are correct,
then run:

```powershell
mvn spring-boot:run
```

### Start the Minecraft bridge

In another PowerShell window:

```powershell
Set-Location .\minecraft-bridge
npm.cmd install
$env:MINECRAFT_HOST = "127.0.0.1"
$env:MINECRAFT_PORT = "25565"
$env:MINECRAFT_USERNAME = "NovaBot"
npm.cmd start
```

Optional bridge environment variables:

| Variable | Default | Purpose |
|---|---:|---|
| `MINECRAFT_HOST` | `127.0.0.1` | Minecraft server host. |
| `MINECRAFT_PORT` | `25565` | Minecraft Java server port. |
| `MINECRAFT_USERNAME` | `NovaBot` | Bot username. |
| `MINECRAFT_VERSION` | Automatic detection | Explicit Minecraft protocol version when needed. |
| `BRIDGE_PORT` | `8765` | Local HTTP port used by the bridge. |

Optional Spring environment variables are listed alongside their properties in
the `application.properties` table above. Set `MINECRAFT_BRIDGE_URL` if the
bridge does not use the default local URL.

### Example requests

```powershell
# Start the Minecraft session
Invoke-RestMethod -Method Post http://localhost:8080/games/minecraft/session

# Stop the session
Invoke-RestMethod -Method Delete http://localhost:8080/games/minecraft/session
```

## Current boundaries and next integration point

- Chat and Minecraft are separate features. `Service.streamChat` does not
  currently call Minecraft operations.
- `MinecraftAdapter` is the boundary for adding other game implementations.
- The Mineflayer bridge currently supports connecting to and disconnecting from
  the configured Minecraft server; it does not issue gameplay actions.
- The bridge binds to loopback but has no authentication. Do not expose it on
  an untrusted network as-is.
- The database entities currently hold conversation and memory data without a
  user/session ownership model.
