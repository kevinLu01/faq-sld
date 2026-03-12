# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**企业微信 FAQ 智能整理助手** — An H5 app running inside WeCom (企业微信) for managing internal FAQ knowledge bases. Target users are manufacturing/HVAC parts enterprises.

Core flow: Upload document → Auto parse & chunk → LLM generates FAQ candidates → Human review → Publish as official FAQ.

## Tech Stack

### Backend (`sld-faq-backend/`)
- Java 17 + Spring Boot 3
- Sa-Token (role/permission annotations, lighter than Spring Security for this use case)
- JWT (issued after WeCom OAuth, validated by Sa-Token filter)
- MyBatis Plus (chosen over JPA) + PostgreSQL
- Redis (token cache, state storage, async task status)
- MinIO (file storage)
- Generic HTTP-based LLM client (not tied to any vendor)
- `OcrClient` — HTTP client calling local GOT-OCR 2.0 service (provider-abstracted, fallback to cloud API via config)

### Frontend (`sld-faq-frontend/`)
- Vue 3 + Vite + TypeScript
- Vant (mobile UI) + Pinia + Vue Router + Axios
- Mobile-first, 375px single-column layout, WeCom embedded browser target

### Deployment (Docker)
All services run via `docker-compose.yml` at repo root. Single command to start everything: `docker-compose up -d`.

| Service | Image |
|---------|-------|
| `sld-faq-backend` | Built from `sld-faq-backend/Dockerfile` |
| `sld-faq-frontend` | Built from `sld-faq-frontend/Dockerfile` (Nginx serving dist/) |
| `ocr-service` | Built from `ocr-service/Dockerfile` (GOT-OCR 2.0, FastAPI, GPU passthrough via `deploy.resources`) |
| `postgres` | `postgres:15` |
| `redis` | `redis:7-alpine` |
| `minio` | `minio/minio` |

All stateful services use named volumes (`postgres_data`, `redis_data`, `minio_data`) — not bind mounts — for clean portability.

**Externalizing a service** (e.g., moving to a managed DB): comment out the service block in compose, update the corresponding `application.yml` env var to point to the external host. Data migration via `pg_dump` / `redis-cli --rdb` / MinIO mc mirror.

## Architecture

### Module Layout (Backend)

```
controller/     → REST endpoints
service/        → Business logic
repository/     → MyBatis Plus mappers
entity/         → DB entities
dto/            → Request bodies
vo/             → Response bodies
config/         → SecurityConfig, JwtConfig, WeComProperties, MinioProperties, LlmProperties
common/         → ApiResponse, GlobalExceptionHandler, BusinessException
infrastructure/ → LlmClient, OcrClient, MinioClient wrapper, WeComOAuthService
```

### Key Request Chains

**OAuth Login:**
Frontend → GET /api/auth/wecom/url → redirect to WeCom OAuth → WeCom redirects with `code` → POST /api/auth/wecom/callback → verify code with WeCom API → auto-create user if first login → sign JWT → return token

**FAQ Generation:**
POST /api/files/upload → save to MinIO + kb_file record → POST /api/files/{id}/generate-faq → parse doc → [if scanned PDF: OcrClient → PaddleOCR sidecar] → TextCleaner → ChunkService → per-chunk LLM call → parse JSON response → save faq_candidate records (status=PENDING)

Async: parse+generate runs via `@Async` thread pool; task status stored in Redis and `kb_task` table; frontend polls GET /api/tasks/{taskId}/status.

**Review Flow:**
GET /api/faq-candidates → POST /{id}/approve | /{id}/reject | /{id}/edit-approve | /{id}/merge → approved candidates become faq_item records

## Database Schema (PostgreSQL)

Key tables and their purposes:
- `sys_user`, `sys_role`, `sys_user_role`, `sys_department` — user/auth system
- `kb_file` — uploaded file metadata (path in MinIO, parse status)
- `kb_chunk` — parsed text chunks with `chunk_index`, `raw_content`, `clean_content`
- `kb_task` — async task tracking for parse/generate jobs
- `faq_category` — category tree
- `faq_candidate` — LLM-generated candidates (status: PENDING/APPROVED/REJECTED/MERGED), fields: question, answer, category, keywords, source_summary, confidence
- `faq_item` — official published FAQs
- `faq_source_ref` — links faq_item back to source chunk/file

## Async Task Design

- Parse + FAQ generation runs in a `@Async` thread pool (configure pool size in `application.yml`)
- Task lifecycle: `PENDING → RUNNING → SUCCESS / FAILED`
- Status stored in both Redis (fast poll) and `kb_task` table (persistent)
- Frontend polls `GET /api/tasks/{taskId}/status` every 3s until terminal state
- Future migration path: replace `@Async` body with XXL-Job/PowerJob handler, keep the same service interface

## Permission Model (Sa-Token)

Three roles, initialized via SQL seed data:

| Role | Permissions |
|------|-------------|
| `ADMIN` | All operations + user management |
| `REVIEWER` | View/review FAQ candidates, view official FAQs |
| `SUBMITTER` | Upload files, trigger FAQ generation, view own submissions |

First WeCom login auto-creates user with role `SUBMITTER`. Role upgrade done via DB or future admin UI.

## Configuration (`application.yml`)

All external services are configured via `application.yml`:
```yaml
wecom:
  corp-id:
  corp-secret:
  agent-id:
  redirect-uri:

minio:
  endpoint:
  access-key:
  secret-key:
  bucket:

llm:
  base-url:
  api-key:
  model-name:
  timeout: 60s

jwt:
  secret:
  expiration: 86400
```

## Document Parsing

| Format | Library | Notes |
|--------|---------|-------|
| PDF (text) | Apache PDFBox | Extract text layer directly |
| PDF (scanned) | OcrClient → GOT-OCR 2.0 (local) | Detected when extracted text < 50 chars; mark file as `SCAN_PDF` if OCR also fails |
| Word (.docx) | Apache POI (XWPFDocument) | Must iterate tables separately; convert to `[表格] header\nrow...` format |
| Excel | EasyExcel | Handle merged cells |
| txt/csv | JDK + juniversalchardet | Encoding detection required |
| Chat records | TextCleaner (CONVERSATION mode) | Filter emoji/short lines; use conversation-specific Prompt |

**Chunk strategy:** Split on double-newline (paragraph boundary); max 800 chars per chunk; 100-char overlap with previous chunk; filter lines < 10 chars or pure numeric.

**Degradation rule:** Always prefer telling the user "parse failed" over silently producing empty or garbage chunks. Never let one file's parse failure affect other tasks.

## OCR Integration

Uses a **local OCR model service** running on an in-house GPU (RTX 5090, 32GB VRAM). No data leaves the intranet, no per-call cost.

**Model: GOT-OCR 2.0** (`ucaslcl/GOT-OCR2_0`) — lightweight (580M params), purpose-built for OCR, strong on Chinese text, tables, and mixed layouts. Outputs structured Markdown.

The OCR model is wrapped in a **FastAPI HTTP service** (`ocr-service/`), packaged as a Docker container, and called by `OcrClient` in the Java backend — same pattern as `LlmClient`.

```
OcrClient → POST http://ocr-service:8866/ocr  (multipart/form-data)
          ← { "text": "...", "markdown": "..." }
```

```yaml
ocr:
  provider: local      # local | baidu | tencent (fallback options if GPU unavailable)
  enabled: true
  local:
    base-url: http://ocr-service:8866
    timeout: 30s
```

`OcrClient` routes by `provider` value — switching to a cloud API requires only a config change, no code changes in upper layers.

## LLM Integration

`LlmClient` uses generic HTTP (RestTemplate/WebClient) — not tied to OpenAI SDK. Calls `POST {llm.base-url}/chat/completions` with OpenAI-compatible format.

`PromptBuilder` supports two modes (enum `PromptMode`):
- `DOCUMENT` — extract FAQ from continuous prose/table text
- `CONVERSATION` — identify Q&A intent from chat records, reconstruct as standard FAQ

Both enforce strict JSON output with fields: `question`, `answer`, `category`, `keywords`, `source_summary`, `confidence`.

Error handling must cover: non-JSON response, empty response, missing fields — use fallback/skip strategy, never throw to user.

## WeCom OAuth Notes

- Use `state` parameter (UUID stored in Redis with TTL) for CSRF protection
- `code` is single-use — consume immediately, cache user info in Redis
- Local dev: implement a `mock-login` endpoint (toggle via `wecom.mock-login=true`) that returns a JWT for a test user, bypassing OAuth

## Frontend Route Structure

```
/login          → LoginPage (handles OAuth code redirect)
/home           → HomePage (stats: pending count, today processed, quick links)
/upload         → UploadPage
/review/list    → ReviewListPage (pull-to-refresh, load-more)
/review/detail/:id → ReviewDetailPage (fixed bottom action bar: approve/reject/edit/merge)
/faq/list       → FaqListPage
/faq/detail/:id → FaqDetailPage
/me             → MePage
```

All routes except `/login` require auth (router guard checks Pinia user store / localStorage token). 401 responses trigger redirect to `/login`.

## API Conventions

- All responses: `{ code, message, data }` via `ApiResponse<T>`
- Auth header: `Authorization: Bearer <token>`
- File upload: `multipart/form-data`, max size configurable, whitelist: pdf/docx/xlsx/txt/csv
- Pagination: `?page=0&size=20` (Spring pageable convention)
