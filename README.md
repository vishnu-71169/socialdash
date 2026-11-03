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

### Live and simulated data

Every external call goes through a single interface, `SocialPlatformClient`, with two implementations:

- **`MetaGraphClient` (live)** — real **Instagram professional accounts** and **Facebook Pages** through the Meta
  Graph API: followers, daily views/likes/comments/shares, per-post metrics, comments in the inbox with replies, and
  publishing text posts to Facebook Pages. See [Connecting Instagram and Facebook](#connecting-instagram-and-facebook).
- **`SimulatedPlatformClient`** — used for X and LinkedIn (whose APIs need paid or partner access) and for any
  account added as simulated. It:

- generates **deterministic** daily metrics per account (stable history across restarts, weekday patterns, viral days),
- simulates publishing — including random **rate-limit and timeout failures** (`SIM_PUBLISH_FAILURE_RATE`, default 15%) so retry handling is exercised,
- produces mentions and interactions over time.

Accounts are labelled **Live** or **Simulated** throughout the UI. Social listening (keyword search) is always
simulated, because the Meta APIs don't offer keyword search.

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

Prerequisites: **JDK 21+** and **Node 18+**. Maven is not required — use the included wrapper.
PostgreSQL is optional: the quick start below uses an in-memory database.

If `java -version` shows an older Java, point `JAVA_HOME` at your JDK 21+ folder first
(PowerShell: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"`, adjusting the path to your install).

### 1. Backend

#### Option A — Quick start, no database setup (recommended for trying it out)

Uses in-memory H2 in PostgreSQL mode. Nothing to install; data resets every time the backend restarts.

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

**Windows PowerShell 5.1** doesn't support `&&`; run the steps on separate lines, use `mvnw.cmd`, and keep the
quotes around the `-D` argument:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=h2"
```

#### Option B — PostgreSQL (data persists)

The default profile expects PostgreSQL on `localhost:5432` with database, user and password all set to
`socialdash`. Start one with Docker:

```bash
docker compose up -d
```

Or, with an existing PostgreSQL install, run this once as the `postgres` superuser (psql or pgAdmin):

```sql
CREATE USER socialdash WITH PASSWORD 'socialdash';
CREATE DATABASE socialdash OWNER socialdash;
```

Then start the backend without the profile argument:

```bash
cd backend && ./mvnw spring-boot:run
```

PowerShell 5.1:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

> **Seeing `PSQLException` on startup?** The backend can't reach PostgreSQL — it isn't running, or the
> `socialdash` user/database doesn't exist. Use Option A, or fix the database as described in Option B.
> Different connection details can be supplied via `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` (see Configuration).

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

Unit tests cover post-status derivation and the Meta client (response parsing, metric fallbacks, retryable vs.
permanent errors, duplicate-post protection) against a mocked HTTP server; integration tests (MockMvc + H2) cover
auth, per-user isolation, analytics backfill, connecting live accounts, the publish-now → scheduler → published flow,
the single-claim guarantee, cancellation and validation.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/socialdash` / `socialdash` / `socialdash` | Database |
| `JWT_SECRET` | dev-only value | Base64 HMAC key, ≥ 32 bytes. **Change outside local dev** |
| `TOKEN_ENCRYPTION_KEY` | dev-only value | Base64 32-byte AES key for platform tokens. **Change outside local dev** |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*,http://127.0.0.1:*` | Allowed browser origins |
| `DEMO_SEED` | `true` | Seed the demo user |
| `SIM_PUBLISH_FAILURE_RATE` | `0.15` | Simulated publish failure probability |
| `META_APP_ID` / `META_APP_SECRET` | empty | Optional. Lets the backend upgrade Facebook tokens to long-lived ones. **Never commit these** |
| `META_API_VERSION` | `v26.0` | Graph API version |

Scheduler intervals and retry settings live in `backend/src/main/resources/application.yml` under `app.*`.

## API overview

All endpoints except `/api/auth/**` need `Authorization: Bearer <token>`.

| Area | Endpoints |
|---|---|
| Auth / user | `POST /api/auth/register`, `POST /api/auth/login`, `GET/PUT /api/users/me`, `PUT /api/users/me/password` |
| Accounts | `GET /api/accounts`, `POST /api/accounts/simulated`, `POST /api/accounts/meta`, `POST /api/accounts/{id}/sync`, `DELETE /api/accounts/{id}` |
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
  integration/   SocialPlatformClient boundary, simulator, meta/ (live Graph API client)
  user/ security/ config/ common/ demo/
backend/src/main/resources/db/migration/   Flyway SQL
frontend/src/
  pages/         Dashboard, Publisher, Engagement, Listening, Accounts, Settings, Auth
  components/    layout, shared UI, chart tooltip
  api/ auth/ lib/
```

## Connecting Instagram and Facebook

Live accounts use your own Meta developer app in **Development mode**, which can read accounts you manage without
Meta's App Review.

1. At [developers.facebook.com](https://developers.facebook.com) create an app with the use cases
   *Manage messaging & content on Instagram* and *Manage everything on your Page*. Leave it unpublished.
2. **Instagram** (professional account required): in the Instagram use case, open *API setup with Unified login*,
   connect a business portfolio, add the account as an *Instagram Tester* and accept the invite in Instagram. Create a
   login configuration with `instagram_basic`, `instagram_manage_insights`, `instagram_manage_comments` and
   `instagram_content_publish`, then *Generate token*. Note the numeric account ID shown under the account.
3. **Facebook Page**: add `pages_show_list`, `pages_read_engagement`, `pages_read_user_content`,
   `pages_manage_posts`, `pages_manage_engagement` and `read_insights` to the Page use case. In the
   [Graph API Explorer](https://developers.facebook.com/tools/explorer) generate a user token with those permissions
   and select your Page; `me/accounts?fields=id,name` shows the Page ID.
4. Optional, so Facebook tokens don't expire: set `META_APP_ID` and `META_APP_SECRET` (*App settings → Basic*) as
   environment variables before starting the backend:

   ```powershell
   $env:META_APP_ID = "<your app id>"
   $env:META_APP_SECRET = "<your app secret>"
   .\mvnw.cmd spring-boot:run
   ```
5. In SocialDash go to **Accounts → Connect account → Live (Meta)**, pick the platform, and paste the ID and token.
   The token is verified with Meta, stored encrypted (AES-GCM) and never returned by the API.

How it behaves:

- The last 14 days of history are fetched on connect; background jobs then refresh live accounts at most every
  15 minutes (`app.meta.sync-interval-minutes`) to stay inside Meta's rate limits. **Sync now** refreshes immediately.
- Meta renames and retires insights metrics; their names are configurable under `app.meta` in `application.yml`, and
  a metric Meta rejects counts as 0 instead of failing the whole sync.
- Rate-limit and temporary errors are retried; an expired or revoked token fails permanently with a message asking
  for a new one.
- Instagram's API only publishes photo/video posts, so text posts can't target a live Instagram account. Facebook
  Pages accept text posts; because Meta has no idempotency keys, a retry first checks for an identical post from the
  last hour so an ambiguous timeout never double-posts.
- Facebook daily comments/shares are counted on posts created that day, since Page insights don't report them per day.
- Follower history starts on the day you connect: Meta reports the current follower count, not past totals.

## Adding another platform

1. Register a developer app with the platform and obtain the needed scopes.
2. Implement `SocialPlatformClient` for it — map HTTP 429/5xx/timeouts to `PlatformApiException(retryable = true)`
   and validation errors to `retryable = false`; pass the idempotency key where the API supports one.
3. Store the token via `TokenCipher` on a non-simulated `SocialAccount` (see `SocialAccountService.connectMeta`).
4. Return the new client from `PlatformClientRegistry.forAccount` for that platform.

## Roadmap

- OAuth sign-in flow for Meta instead of pasting tokens (needs a public HTTPS callback URL)
- Instagram photo publishing (needs publicly reachable media URLs)
- Sentiment classification of mentions (after data collection is solid)
- Calendar view for scheduled posts, media uploads
- Deployment config (container image, managed PostgreSQL)
