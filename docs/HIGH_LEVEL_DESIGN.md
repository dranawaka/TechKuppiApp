# Tech Kuppi App — High-Level Design

## 1. Overview

**Tech Kuppi App** is a Spring Boot 3 application that delivers tech quiz questions via **Telegram** and exposes **REST APIs** for question management and leaderboards. Questions can be **AI-generated** (OpenAI) and stored in a **PostgreSQL** question bank. Users answer in a Telegram group; answers are scored and persisted for a **leaderboard**.

---

## 2. System Context

```
                    +------------------+
                    |   Telegram API   |
                    +--------+---------+
                             |
                             | Long polling / Send message
                             v
+-------------+     +------------------+     +------------------+
|   OpenAI    |<----|  Tech Kuppi App  |---->|   PostgreSQL     |
|   API       |     |  (Spring Boot)   |     |   (Question Bank,|
+-------------+     +--------+--------+     |    Users, Answers)|
                    |        |              +------------------+
                    |        |
                    v        v
              +----------+  +----------+
              | REST API |  | Swagger  |
              | clients  |  | UI       |
              +----------+  +----------+
```

- **Users** interact via a Telegram group (bot commands, reply with A/B/C/D).
- **Admins/automation** use REST APIs (and Swagger UI) for batch question generation, sending questions, and viewing leaderboard.
- **OpenAI** supplies generated multiple-choice questions (single or batch).
- **PostgreSQL** stores questions, users (by Telegram ID), and answer history.

---

## 3. Architecture Layers

| Layer        | Responsibility |
|-------------|----------------|
| **Controllers** | REST endpoints; request/response mapping; validation. |
| **Services**    | Business logic; orchestration (Telegram, OpenAI, DB). |
| **Repositories**| JPA persistence (QuestionBank, QuizUser, UserAnswer). |
| **Config**      | DataSource, Telegram bot registration, RestTemplate, OpenAPI. |
| **Scheduler**   | Optional auto-send questions; periodic close of expired questions. |

---

## 4. Core Components

### 4.1 REST API

| API Area | Base Path | Purpose |
|----------|-----------|---------|
| **Question Bank** | `/api/questions` | Batch generate and save AI questions (count, optional topic). |
| **Leaderboard**   | `/api/leaderboard` | Top users by correct answers (optional `limit`). |
| **Telegram**      | `/telegram` | Send message, send poll, generate-and-send question (when bot enabled). |

- **Documentation**: OpenAPI 3 at `/api-docs`, Swagger UI at `/swagger-ui.html`.
- **Error handling**: `GlobalExceptionHandler` returns structured error payloads (e.g. validation failures).

### 4.2 Telegram Bot

- **Registration**: `TelegramBotConfig` registers `TelegramBotService` with Telegram when `telegram.bot.token` is set.
- **Behaviour**:
  - Commands (e.g. `/quiz`) trigger AI question generation, send question to configured chat, and track answers (A–D) per user.
  - After a configurable answer window (e.g. 60s), results are posted (correct/wrong) and answers are persisted to DB for leaderboard.
- **User identity**: Users are created/updated by Telegram user ID; display name and username stored for leaderboard.

### 4.3 Question Pipeline

1. **Single question (Telegram)**: User/trigger → `TelegramBotService` → `OpenAIService` → parse JSON → optional save to `QuestionBank` → send to chat → collect answers → close and persist `UserAnswer`.
2. **Batch (REST)**: `QuestionBankController` → `BatchQuestionService` → `OpenAIService` (batch prompt) → parse array of questions → `QuestionBankService.saveAllFromGenerated()` with duplicate detection via `question_hash`.

### 4.4 Scheduler

- **QuestionScheduler** runs only when `TelegramBotService` is present.
  - **Auto-send**: Every 10 minutes (if `telegram.quiz.auto-send-enabled`), generate one question and send to chat.
  - **Close expired**: Every 15 seconds, close questions past the answer window and send results + persist answers.

---

## 5. Data Model (High-Level)

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│   question_bank │       │    quiz_user    │       │   user_answer   │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ id              │       │ id              │       │ id              │
│ question        │◄──────│ telegram_user_id│       │ user_id (FK)    │──► quiz_user
│ question_hash   │       │ display_name    │       │ question_bank_id │──► question_bank
│ options (list)  │       │ username        │       │ chosen_answer   │
│ correct_answer  │       │ created_at      │       │ correct         │
│ created_at      │       │ updated_at      │       │ answered_at     │
└─────────────────┘       └─────────────────┘       └─────────────────┘
```

- **QuestionBank**: One row per question; `question_hash` (SHA-256 of normalized text) enforces uniqueness and avoids duplicate imports.
- **QuizUser**: One per Telegram user; used for leaderboard display and linking answers.
- **UserAnswer**: One row per user per answered question; `correct` drives leaderboard aggregation.

---

## 6. External Integrations

| System       | Role |
|-------------|------|
| **OpenAI**  | Chat completions for single/batch question generation when `app.ai.provider=openai` (model, max tokens, temperature in config). |
| **Ollama**  | Local LLM via OpenAI-compatible API when `app.ai.provider=ollama` (base URL, model, e.g. llama3.2). |
| **Telegram**| Long-polling bot; send message to group; parse commands and reply text (A/B/C/D). |
| **PostgreSQL** | Persistence; DB created automatically if missing (when `spring.datasource.create-database-if-not-exists` is true). |

---

## 7. Configuration (Summary)

- **Application**: `application.yml` — server port, logging, app name.
- **DataSource**: PostgreSQL URL, credentials (env-overridable: `POSTGRES_*`), JPA/Hibernate, optional DB creation.
- **AI provider**: `app.ai.provider` — `openai` (default) or `ollama`; controls which LLM is used for question generation (Telegram and REST batch).
- **OpenAI**: `openai.api.key`, `openai.api.model`, `openai.api.max-tokens`, `openai.api.temperature` (when provider is openai).
- **Ollama**: `ollama.api.base-url` (default `http://localhost:11434/v1`), `ollama.api.model`, `ollama.api.max-tokens`, `ollama.api.temperature` (when provider is ollama).
- **Telegram**: `telegram.bot.token`, `telegram.bot.chatid`, `telegram.bot.username`, `telegram.quiz.answer-seconds`, `telegram.quiz.auto-send-enabled`, `telegram.quiz.question-source` (`openai` | `question-bank`).
- **Springdoc**: `/api-docs`, `/swagger-ui.html`, UI sort options.

---

## 8. Key Design Decisions

- **Conditional Telegram**: Bot and Telegram-related beans (including scheduler) are only active when `telegram.bot.token` is set, so the app can run without Telegram (e.g. API-only).
- **Custom DataSource**: `DataSourceConfig` ensures the PostgreSQL database exists before building the DataSource, then uses standard Spring/Hikari for pooling.
- **In-memory active question**: Per-chat active question and answers are held in memory until the answer window closes; then results are sent and `UserAnswer` rows are written.
- **Topic-aware batch**: Batch generation supports optional topics (e.g. java, aws, kafka, database, spring, springboot, genai) to tailor prompts.
- **Configurable question source**: When a user asks for a question (Telegram `/quiz`, REST, or scheduler), the source is chosen by `telegram.quiz.question-source`: `openai` (generate via AI) or `question-bank` (pick a random question from the database). When generating via AI, the actual LLM is chosen by `app.ai.provider`: `openai` or `ollama`. When `question-bank` is used, topic filters are ignored. If the bank is empty, the user sees a fallback message or the scheduler sends a fixed fallback text.

---

## 9. Deployment View

- Single JVM process (Spring Boot).
- Requires: Java 21+, PostgreSQL, network access to Telegram and to the chosen AI provider (OpenAI or Ollama at `localhost:11434` when using ollama).
- REST and Swagger are served on the same port (default 8080); Telegram bot uses long polling (no separate webhook server required).

---

## 10. Troubleshooting

### Telegram 409 Conflict: "terminated by other getUpdates request"

**Cause:** Telegram allows **only one** active long-polling connection per bot token. Error 409 means another process is already calling `getUpdates` with the same token.

**Fix:**

1. **Run only one instance** of the app. Stop any duplicate:
   - Second terminal with `mvn spring-boot:run` or `java -jar`
   - Second run from your IDE
   - Another machine or server using the same `telegram.bot.token`
2. **If you use webhooks elsewhere** for this bot, remove the webhook so long polling can work:
   - Call `DELETE https://api.telegram.org/bot<YOUR_TOKEN>/deleteWebhook`
   - Then restart this app.
3. After stopping the other instance or deleting the webhook, restart the app; the single instance will then receive updates without conflict.
