# SocialDash — Social Media Management & Analytics Dashboard

A centralized platform to manage multiple social media accounts from one dashboard: track performance
(likes, comments, shares, engagement rate, follower growth), schedule posts across platforms, monitor brand
mentions and hashtags, and handle audience interactions.

**Stack:** React + TypeScript (Vite, React Router, Recharts) · Java 21 + Spring Boot 3.5 (Spring Security + JWT,
Spring Data JPA/Hibernate, Spring Scheduler, Flyway) · PostgreSQL. A modular monolith — no Redis/Kafka/microservices.

## Features

| Module | What it does |
|---|---|
| **Auth & profile** | Register / login with JWT, BCrypt password hashing, profile + password change, per-user data isolation |
| **Account integration** | Connect Instagram, Facebook, X/Twitter and LinkedIn accounts; manual sync; disconnect. Platform tokens are AES-256-GCM encrypted at rest and never sent to the browser |
| **Analytics dashboard** | Followers, follower growth, impressions, likes, comments, shares, engagement rate — each compared with the previous period. Trend charts, platform comparison, post performance (top / recent) with per-post history. Filters by platform, account and date range. Auto-refreshes every 60s |
| **Content scheduling** | Compose once, publish to many accounts. Drafts, schedule for later, publish now, edit, cancel, retry failed, per-platform character limits. Statuses: `DRAFT → SCHEDULED → PUBLISHING → PUBLISHED / FAILED / CANCELLED` |
| **Social listening** | Track keywords/hashtags (optionally per platform), mention feed with search, daily volume chart vs previous period, trending co-occurring terms |
| **Engagement management** | Unified inbox of comments, mentions and DMs; filter by status/type/platform/text; reply, resolve, reopen; average response time |

### About "simulated" data

Real platform APIs (Meta Graph API, X API, LinkedIn Marketing API) require approved developer apps and
permissions that a personal account usually doesn't have. Every external call therefore goes through a single
interface, `SocialPlatformClient`; today the only implementation is `SimulatedPlatformClient`, which:

- generates **deterministic** daily metrics per account (stable history across restarts, weekday patterns, viral days),
- simulates publishing — including random **rate-limit and timeout failures** (`SIM_PUBLISH_FAILURE_RATE`, default 15%) so retry handling is exercised,
- produces mentions and interactions over time.

Simulated accounts are labelled **Simulated** throughout the UI. See [Adding a real platform](#adding-a-real-platform).

## Engineering highlights

- **Reliable scheduling, no double-posting.** Each publication is claimed with a conditional
  `UPDATE … SET status='PUBLISHING' WHERE id=? AND status='SCHEDULED'`, so only one worker (or app instance) can
  win. The platform call happens *outside* any DB transaction, then the outcome is recorded in a second short
  transaction. The publication id is sent as an **idempotency key**, so a retry after an ambiguous timeout (the
  simulator deliberately produces these) returns the original post instead of creating a duplicate. Retryable
  errors back off exponentially (30s, 60s, …, max 3 attempts); non-retryable ones (e.g. content too long) fail
  immediately; rows stuck in `PUBLISHING` after a crash are re-queued. `@Version` optimistic locking prevents a
  user edit from racing the scheduler. See [`PublishingScheduler`](backend/src/main/java/com/socialdash/post/PublishingScheduler.java).
- **Independent per-platform delivery.** `posts` holds content; `post_publications` holds one row per target
  account, each with its own status, attempts and error. A post can be `PUBLISHED` on Instagram and `FAILED` on X.
- **Historical analytics.** `analytics_snapshots` stores one row per account per UTC day (today's row is updated in
  place); growth and period-over-period comparisons are computed from history, never hard-coded. Per-post metrics
  are kept as a time series in `post_analytics`, with the latest values denormalised onto the publication for cheap
  "top posts" sorting.
- **Query design.** Aggregations run in the database (`GROUP BY` date / account) over indexed
  `(social_account_id, snapshot_date)`; mention volume reads only `(keyword_id, detected_at)`; lists are paginated.
- **Time zones.** Everything is stored as UTC `TIMESTAMP WITH TIME ZONE`; the browser converts the user's local
  schedule time to an ISO instant.
- **API hygiene.** Bean validation, centralised error handling with consistent JSON errors, OpenAPI docs at
  `/swagger-ui.html`, Flyway migrations, `ddl-auto=validate`.

## Running locally

Prerequisites: **JDK 21+**, **Node 18+**, and either Docker (for PostgreSQL) or nothing (H2 quick-start).
Maven is not required — use the included wrapper.

### 1. Backend

With PostgreSQL (default profile):

```bash
docker compose up -d
```

```bash
cd backend && ./mvnw spring-boot:run
```

Quick start without PostgreSQL (in-memory H2 in PostgreSQL mode; data resets on restart):

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

**Windows PowerShell 5.1** doesn't support `&&`; run the steps on separate lines and use `mvnw.cmd`:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"   # omit the -D argument to use PostgreSQL
```

The API runs on <http://localhost:8080>
(Swagger UI: <http://localhost:8080/swagger-ui.html>).

On first start a demo user is seeded with four simulated accounts, ~120 days of history, published/scheduled/failed
posts, tracked keywords and an inbox of interactions:

- **Email:** `demo@socialdash.dev`
- **Password:** `demo1234`

Set `DEMO_SEED=false` to skip it.

### 2. Frontend

```bash
cd frontend && npm install && npm run dev
```

PowerShell 5.1:

```powershell
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. Vite proxies `/api` to the backend.

### Tests

```bash
cd backend && ./mvnw test
```

Unit tests cover post-status derivation; integration tests (MockMvc + H2) cover auth, per-user isolation,
analytics backfill, the publish-now → scheduler → published flow, the single-claim guarantee, cancellation and
validation.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/socialdash` / `socialdash` / `socialdash` | Database |
| `JWT_SECRET` | dev-only value | Base64 HMAC key, ≥ 32 bytes. **Change outside local dev** |
| `TOKEN_ENCRYPTION_KEY` | dev-only value | Base64 32-byte AES key for platform tokens. **Change outside local dev** |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed browser origins |
| `DEMO_SEED` | `true` | Seed the demo user |
| `SIM_PUBLISH_FAILURE_RATE` | `0.15` | Simulated publish failure probability |

Scheduler intervals and retry settings live in `backend/src/main/resources/application.yml` under `app.*`.

## API overview

All endpoints except `/api/auth/**` need `Authorization: Bearer <token>`.

| Area | Endpoints |
|---|---|
| Auth / user | `POST /api/auth/register`, `POST /api/auth/login`, `GET/PUT /api/users/me`, `PUT /api/users/me/password` |
| Accounts | `GET /api/accounts`, `POST /api/accounts/simulated`, `POST /api/accounts/{id}/sync`, `DELETE /api/accounts/{id}` |
| Analytics | `GET /api/analytics/overview`, `/platforms`, `/accounts`, `/trends`, `/posts`, `/posts/{publicationId}/history` — all accept `platform`, `accountId`, `startDate`, `endDate` |
| Posts | `GET/POST /api/posts`, `GET /api/posts/summary`, `GET/PUT/DELETE /api/posts/{id}`, `POST /api/posts/{id}/cancel`, `POST /api/posts/{id}/retry` |
| Listening | `GET/POST /api/listening/keywords`, `DELETE /api/listening/keywords/{id}`, `GET /api/listening/mentions`, `/volume`, `/top-terms` |
| Engagement | `GET /api/engagements`, `GET /api/engagements/summary`, `POST /api/engagements/{id}/reply`, `/resolve`, `/reopen` |

## Project structure

```
backend/src/main/java/com/socialdash/
  account/       connected accounts (entity, service, controller)
  analytics/     snapshots, post history, collector job, aggregation queries
  post/          posts, per-account publications, publishing scheduler
  listening/     keywords, mentions, collector job
  engagement/    interactions inbox, collector job
  integration/   SocialPlatformClient boundary + simulator
  user/ security/ config/ common/ demo/
backend/src/main/resources/db/migration/   Flyway SQL
frontend/src/
  pages/         Dashboard, Publisher, Engagement, Listening, Accounts, Settings, Auth
  components/    layout, shared UI, chart tooltip
  api/ auth/ lib/
```

## Adding a real platform

1. Register a developer app with the platform and obtain the needed scopes.
2. Implement `SocialPlatformClient` for it (e.g. `InstagramGraphClient`) — map HTTP 429/5xx/timeouts to
   `PlatformApiException(retryable = true)` and validation errors to `retryable = false`; pass the idempotency key
   where the API supports one.
3. Add an OAuth callback that stores the token via `TokenCipher` on a non-simulated `SocialAccount`.
4. Return the new client from `PlatformClientRegistry.forAccount` for that platform.

## Roadmap

- One real platform integration end to end (OAuth, refresh tokens, rate-limit headers)
- Sentiment classification of mentions (after data collection is solid)
- Calendar view for scheduled posts, media uploads
- Deployment config (container image, managed PostgreSQL)
